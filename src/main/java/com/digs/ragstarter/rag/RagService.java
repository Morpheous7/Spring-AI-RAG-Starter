package com.digs.ragstarter.rag;

import com.digs.ragstarter.support.TextChunker;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RagService {

    private static final String SYSTEM_PROMPT = """
            You answer questions using only the numbered context passages provided.
            If the passages don't contain the answer, say you don't know.
            Cite the passages you used like [1] or [2].
            """;

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final ChatClient chatClient; // null when answer generation is off
    private final TextChunker chunker = new TextChunker(600, 100);
    private final Reranker reranker;
    private final int defaultTopK;
    private final double similarityThreshold;
    private final int rerankPool;

    public RagService(VectorStore vectorStore,
                      JdbcTemplate jdbcTemplate,
                      ObjectProvider<ChatModel> chatModel,
                      Reranker reranker,
                      @Value("${app.search.top-k:4}") int defaultTopK,
                      @Value("${app.search.similarity-threshold:0.0}") double similarityThreshold,
                      @Value("${app.rerank.pool:20}") int rerankPool) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.reranker = reranker;
        this.rerankPool = Math.max(rerankPool, 1);
        // Ask for the model itself, not ChatClient.Builder: Spring AI registers the builder bean
        // even when no chat model exists, and resolving it then fails at startup.
        ChatModel model = chatModel.getIfAvailable();
        this.chatClient = model == null ? null : ChatClient.builder(model).build();
        this.defaultTopK = defaultTopK;
        this.similarityThreshold = similarityThreshold;
    }

    public boolean answersEnabled() {
        return chatClient != null;
    }

    public boolean rerankingEnabled() {
        return reranker.enabled();
    }

    public long documentCount() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vector_store", Long.class);
        return count == null ? 0 : count;
    }

    public int ingest(String text, String source) {
        List<String> pieces = chunker.split(text);
        List<Document> documents = new ArrayList<>(pieces.size());
        for (int i = 0; i < pieces.size(); i++) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", source == null || source.isBlank() ? "untitled" : source);
            metadata.put("chunk", i);
            documents.add(new Document(pieces.get(i), metadata));
        }
        if (!documents.isEmpty()) {
            vectorStore.add(documents);
        }
        return documents.size();
    }

    public List<Passage> search(String query, Integer topK) {
        int k = topK == null || topK < 1 ? defaultTopK : Math.min(topK, 20);
        // With reranking on, fetch a wider pool by vector similarity, then let the cross-encoder pick the best k.
        int fetch = reranker.enabled() ? Math.max(k, rerankPool) : k;
        List<Document> hits = vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(fetch)
                .similarityThreshold(similarityThreshold)
                .build());

        List<Double> rerankScores = new ArrayList<>(hits.size());
        List<Document> ordered = hits;
        if (reranker.enabled() && hits.size() > 1) {
            try {
                List<String> texts = new ArrayList<>(hits.size());
                for (Document d : hits) {
                    texts.add(d.getText() == null ? "" : d.getText());
                }
                float[] scores = reranker.score(query, texts);
                Integer[] order = new Integer[hits.size()];
                for (int i = 0; i < order.length; i++) {
                    order[i] = i;
                }
                java.util.Arrays.sort(order, (a, b) -> Float.compare(scores[b], scores[a]));
                ordered = new ArrayList<>(hits.size());
                for (Integer idx : order) {
                    ordered.add(hits.get(idx));
                    rerankScores.add((double) scores[idx]);
                }
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(RagService.class).warn("Reranking failed; using vector order.", e);
                ordered = hits;
                rerankScores.clear();
            }
        }

        int n = Math.min(k, ordered.size());
        List<Passage> passages = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Document doc = ordered.get(i);
            Object source = doc.getMetadata().get("source");
            passages.add(new Passage(i + 1,
                    source == null ? "untitled" : source.toString(),
                    doc.getScore(),
                    rerankScores.isEmpty() ? null : rerankScores.get(i),
                    doc.getText()));
        }
        return passages;
    }

    public AskResponse ask(String question, Integer topK) {
        List<Passage> passages = search(question, topK);
        if (chatClient == null) {
            return new AskResponse(question, null, "search-only", passages);
        }
        if (passages.isEmpty()) {
            return new AskResponse(question, "I don't know: no stored passage matches this question.",
                    "answer", passages);
        }
        StringBuilder context = new StringBuilder();
        for (Passage p : passages) {
            context.append('[').append(p.rank()).append("] (").append(p.source()).append(")\n")
                    .append(p.text()).append("\n\n");
        }
        String answer = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user("Context passages:\n\n" + context + "Question: " + question)
                .call()
                .content();
        return new AskResponse(question, answer, "answer", passages);
    }

    public record Passage(int rank, String source, Double score, Double rerankScore, String text) {
    }

    public record AskResponse(String question, String answer, String mode, List<Passage> sources) {
    }
}
