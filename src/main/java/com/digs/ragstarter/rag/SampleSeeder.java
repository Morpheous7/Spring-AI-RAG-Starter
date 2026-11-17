package com.digs.ragstarter.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Loads one sample document on first start so /api/ask works right after deploy. */
@Component
public class SampleSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleSeeder.class);

    private final RagService ragService;
    private final boolean enabled;

    public SampleSeeder(RagService ragService, @Value("${app.seed-sample:true}") boolean enabled) {
        this.ragService = ragService;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (!enabled || ragService.documentCount() > 0) {
            return;
        }
        try (InputStream in = new ClassPathResource("sample/welcome.md").getInputStream()) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            int chunks = ragService.ingest(text, "welcome.md");
            log.info("Seeded sample document welcome.md ({} chunks). Set SEED_SAMPLE=false to skip.", chunks);
        }
    }
}
