package com.digs.ragstarter.support;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits text into overlapping chunks, preferring paragraph and sentence boundaries.
 * Plain Java on purpose: no dependency on framework splitter APIs that change between releases.
 */
public final class TextChunker {

    private final int maxChars;
    private final int overlapChars;

    public TextChunker(int maxChars, int overlapChars) {
        if (maxChars < 100) {
            throw new IllegalArgumentException("maxChars must be at least 100");
        }
        if (overlapChars < 0 || overlapChars >= maxChars / 2) {
            throw new IllegalArgumentException("overlapChars must be between 0 and maxChars/2");
        }
        this.maxChars = maxChars;
        this.overlapChars = overlapChars;
    }

    public List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null) {
            return chunks;
        }
        String normalized = text.replace("\r\n", "\n").strip();
        if (normalized.isEmpty()) {
            return chunks;
        }

        int start = 0;
        int length = normalized.length();
        while (start < length) {
            int end = Math.min(start + maxChars, length);
            if (end < length) {
                end = bestBreak(normalized, start, end);
            }
            String chunk = normalized.substring(start, end).strip();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            if (end >= length) {
                break;
            }
            int next = end - overlapChars;
            start = Math.max(next, start + 1);
        }
        return chunks;
    }

    /** Find the latest paragraph, sentence or word break in the back half of the window. */
    private int bestBreak(String text, int start, int end) {
        int floor = start + maxChars / 2;
        int paragraph = text.lastIndexOf("\n\n", end);
        if (paragraph >= floor) {
            return paragraph;
        }
        for (int i = end - 1; i >= floor; i--) {
            char c = text.charAt(i);
            if ((c == '.' || c == '!' || c == '?' || c == '\n') && i + 1 < text.length()
                    && Character.isWhitespace(text.charAt(i + 1))) {
                return i + 1;
            }
        }
        int space = text.lastIndexOf(' ', end);
        if (space >= floor) {
            return space;
        }
        return end;
    }
}
