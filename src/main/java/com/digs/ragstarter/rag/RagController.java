package com.digs.ragstarter.rag;

import com.digs.ragstarter.support.TextExtractor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class RagController {

    private final RagService ragService;
    private final boolean apiKeyRequired;

    public RagController(RagService ragService, @Value("${app.api-key:}") String apiKey) {
        this.ragService = ragService;
        this.apiKeyRequired = apiKey != null && !apiKey.isBlank();
    }

    @GetMapping("/api/stats")
    public Map<String, Object> stats() {
        return Map.of("storedChunks", ragService.documentCount());
    }

    /** Open (no API key needed) so the question page can show the mode before the user signs in. */
    @GetMapping("/api/info")
    public Map<String, Object> info() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", "Spring AI RAG Starter");
        info.put("mode", ragService.answersEnabled() ? "answer" : "search-only");
        info.put("apiKeyRequired", apiKeyRequired);
        info.put("endpoints", List.of(
                "GET  /  (question page)",
                "POST /api/documents  {\"text\": \"...\", \"source\": \"name\"}",
                "POST /api/documents/upload  (multipart field 'file': .pdf, .txt or .md, 20 MB max)",
                "GET  /api/search?q=...&k=4",
                "POST /api/ask  {\"question\": \"...\", \"k\": 4}",
                "GET  /actuator/health"));
        return info;
    }

    @PostMapping("/api/documents")
    public Map<String, Object> addText(@RequestBody AddTextRequest request) {
        if (request == null || request.text() == null || request.text().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text is required");
        }
        int chunks = ragService.ingest(request.text(), request.source());
        return Map.of("source", request.source() == null ? "untitled" : request.source(), "chunks", chunks);
    }

    @PostMapping("/api/documents/upload")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) throws IOException {
        String name = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        if (!TextExtractor.isSupported(name)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Only .pdf, .txt and .md files are accepted.");
        }
        String text;
        try {
            text = TextExtractor.extract(name, file.getBytes());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Couldn't read this file. If it's a PDF, it may be damaged or password-protected.");
        }
        if (text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "No text found in this file. Scanned PDFs (images of pages) aren't supported.");
        }
        return Map.of("source", name, "chunks", ragService.ingest(text, name));
    }

    @GetMapping("/api/search")
    public List<RagService.Passage> search(@RequestParam("q") String query,
                                           @RequestParam(value = "k", required = false) Integer k) {
        if (query.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "q is required");
        }
        return ragService.search(query, k);
    }

    @PostMapping("/api/ask")
    public RagService.AskResponse ask(@RequestBody AskRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "question is required");
        }
        return ragService.ask(request.question(), request.k());
    }

    public record AddTextRequest(String text, String source) {
    }

    public record AskRequest(String question, Integer k) {
    }
}
