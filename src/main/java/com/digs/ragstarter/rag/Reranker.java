package com.digs.ragstarter.rag;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Second-stage reranker: a cross-encoder (ms-marco-MiniLM-L6-v2, ONNX) reads the question and each
 * candidate passage together and scores how well the passage answers it. Higher score = better.
 * The model files are baked into the Docker image; if they're missing, reranking is skipped.
 */
@Component
public class Reranker implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(Reranker.class);
    private static final int MAX_TOKENS = 512;

    private final OrtEnvironment env;
    private final OrtSession session;
    private final HuggingFaceTokenizer tokenizer;
    private final Set<String> inputNames;

    public Reranker(@Value("${app.rerank.enabled:true}") boolean enabled,
                    @Value("${app.rerank.model-dir:/app/models/reranker}") String modelDir) {
        OrtEnvironment e = null;
        OrtSession s = null;
        HuggingFaceTokenizer t = null;
        Set<String> names = Set.of();
        Path model = Path.of(modelDir, "model.onnx");
        Path vocab = Path.of(modelDir, "tokenizer.json");
        if (!enabled) {
            log.info("Reranking is turned off (RERANK=false).");
        } else if (!Files.isReadable(model) || !Files.isReadable(vocab)) {
            log.warn("Reranker model not found in {}; search results will use vector order only.", modelDir);
        } else {
            try {
                e = OrtEnvironment.getEnvironment();
                OrtSession.SessionOptions options = new OrtSession.SessionOptions();
                options.setIntraOpNumThreads(2);
                s = e.createSession(model.toString(), options);
                t = HuggingFaceTokenizer.newInstance(vocab);
                names = s.getInputNames();
                log.info("Reranker loaded from {} (inputs {}).", modelDir, names);
            } catch (Exception ex) {
                log.warn("Reranker failed to load; search results will use vector order only.", ex);
                s = null;
                t = null;
            }
        }
        this.env = e;
        this.session = s;
        this.tokenizer = t;
        this.inputNames = names;
    }

    public boolean enabled() {
        return session != null && tokenizer != null;
    }

    /** Scores each passage against the query. Returns one relevance logit per passage. */
    public synchronized float[] score(String query, List<String> passages) throws OrtException {
        float[] scores = new float[passages.size()];
        for (int i = 0; i < passages.size(); i++) {
            Encoding enc = tokenizer.encode(query, passages.get(i));
            long[] ids = clip(enc.getIds());
            long[] mask = clip(enc.getAttentionMask());
            long[] types = clip(enc.getTypeIds());
            Map<String, OnnxTensor> inputs = new HashMap<>();
            try {
                inputs.put("input_ids", OnnxTensor.createTensor(env, new long[][]{ids}));
                if (inputNames.contains("attention_mask")) {
                    inputs.put("attention_mask", OnnxTensor.createTensor(env, new long[][]{mask}));
                }
                if (inputNames.contains("token_type_ids")) {
                    inputs.put("token_type_ids", OnnxTensor.createTensor(env, new long[][]{types}));
                }
                try (OrtSession.Result result = session.run(inputs)) {
                    float[][] logits = (float[][]) result.get(0).getValue();
                    scores[i] = logits[0][0];
                }
            } finally {
                inputs.values().forEach(OnnxTensor::close);
            }
        }
        return scores;
    }

    private static long[] clip(long[] values) {
        return values.length <= MAX_TOKENS ? values : Arrays.copyOf(values, MAX_TOKENS);
    }

    @Override
    public void close() throws Exception {
        if (session != null) {
            session.close();
        }
        if (tokenizer != null) {
            tokenizer.close();
        }
    }
}
