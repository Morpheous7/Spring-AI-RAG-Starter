package com.digs.ragstarter.rag;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @GetMapping("/")
    public Map<String, Object> info() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", "Spring AI RAG Starter");
        info.put("mode", ragService.answersEnabled() ? "answer" : "search-only");
        info.put("storedChunks", ragService.documentCount());
        info.put("endpoints", List.of(
                "POST /api/documents  {\"text\": \"...\", \"source\": \"name\"}",
                "POST /api/documents/upload  (multipart field 'file': .txt or .md)",
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
        String lower = name.toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".txt") || lower.endsWith(".md"))) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "only .txt and .md files are accepted");
        }
        String text = new String(file.getBytes(), StandardCharsets.UTF_8);
        if (text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is empty");
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
