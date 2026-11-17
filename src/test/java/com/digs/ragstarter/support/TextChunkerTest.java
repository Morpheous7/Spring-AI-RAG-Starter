package com.digs.ragstarter.support;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextChunkerTest {

    private final TextChunker chunker = new TextChunker(200, 30);

    @Test
    void emptyAndNullTextGiveNoChunks() {
        assertTrue(chunker.split(null).isEmpty());
        assertTrue(chunker.split("   \n ").isEmpty());
    }

    @Test
    void shortTextIsOneChunk() {
        assertEquals(List.of("Hello world."), chunker.split("  Hello world.  "));
    }

    @Test
    void longTextIsSplitWithinLimitAndCoversEverything() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            sb.append("Sentence number ").append(i).append(" is here. ");
        }
        String text = sb.toString().strip();
        List<String> chunks = chunker.split(text);
        assertTrue(chunks.size() > 1);
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= 200, "chunk too long: " + chunk.length());
        }
        assertTrue(chunks.get(0).startsWith("Sentence number 0 "));
        assertTrue(chunks.get(chunks.size() - 1).endsWith("Sentence number 59 is here."));
    }

    @Test
    void prefersParagraphBreaks() {
        String para1 = "a".repeat(150);
        String para2 = "b".repeat(150);
        List<String> chunks = chunker.split(para1 + "\n\n" + para2);
        assertEquals(para1, chunks.get(0));
    }

    @Test
    void textWithNoBreaksStillTerminates() {
        List<String> chunks = chunker.split("x".repeat(1000));
        assertTrue(chunks.size() >= 5);
    }

    @Test
    void rejectsBadSettings() {
        assertThrows(IllegalArgumentException.class, () -> new TextChunker(50, 10));
        assertThrows(IllegalArgumentException.class, () -> new TextChunker(200, 150));
    }
}
