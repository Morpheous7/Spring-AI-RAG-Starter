package com.digs.ragstarter.support;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextExtractorTest {

    @Test
    void supportsTextMarkdownAndPdfOnly() {
        assertTrue(TextExtractor.isSupported("notes.TXT"));
        assertTrue(TextExtractor.isSupported("readme.md"));
        assertTrue(TextExtractor.isSupported("Policy Handbook.pdf"));
        assertFalse(TextExtractor.isSupported("report.docx"));
        assertFalse(TextExtractor.isSupported(null));
    }

    @Test
    void readsPlainTextAsUtf8() throws Exception {
        assertEquals("café", TextExtractor.extract("a.txt", "café".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void extractsTextFromEveryPdfPage() throws Exception {
        byte[] pdf = twoPagePdf("The secret word is pelican.", "Page two mentions the warehouse.");
        String text = TextExtractor.extract("test.pdf", pdf);
        assertTrue(text.contains("The secret word is pelican."), text);
        assertTrue(text.contains("Page two mentions the warehouse."), text);
    }

    @Test
    void blankPdfGivesEmptyText() throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            doc.save(out);
            assertEquals("", TextExtractor.extract("scan.pdf", out.toByteArray()));
        }
    }

    private static byte[] twoPagePdf(String first, String second) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String line : new String[]{first, second}) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(72, 700);
                    content.showText(line);
                    content.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
