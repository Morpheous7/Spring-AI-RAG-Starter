package com.digs.ragstarter.support;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Turns an uploaded file into plain text. Supports .txt, .md and .pdf (text-based PDFs, not scans). */
public final class TextExtractor {

    private TextExtractor() {
    }

    public static boolean isSupported(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".pdf");
    }

    public static String extract(String filename, byte[] bytes) throws IOException {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return extractPdf(bytes);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static String extractPdf(byte[] bytes) throws IOException {
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setParagraphStart("\n");
            StringBuilder out = new StringBuilder();
            int pages = pdf.getNumberOfPages();
            for (int page = 1; page <= pages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(pdf).strip();
                if (!text.isEmpty()) {
                    out.append(text).append("\n\n");
                }
            }
            return out.toString().strip();
        }
    }
}
