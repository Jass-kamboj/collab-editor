package com.example;

import com.itextpdf.text.Document;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.tool.xml.XMLWorkerHelper;
import org.apache.poi.xwpf.usermodel.*;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class DocumentService {

    // ── Save to .docx ────────────────────────────────────────────
    public static void saveDocx(String html, File file) {
        try (XWPFDocument doc = new XWPFDocument();
             FileOutputStream out = new FileOutputStream(file)) {

            // Strip HTML tags to get plain text for now
            String[] lines = html
                .replaceAll("<br\\s*/?>", "\n")
                .replaceAll("<p[^>]*>", "")
                .replaceAll("</p>", "\n")
                .replaceAll("<[^>]+>", "")
                .split("\n");

            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    XWPFParagraph para = doc.createParagraph();
                    XWPFRun run = para.createRun();
                    run.setText(trimmed);
                    run.setFontSize(12);
                    run.setFontFamily("Arial");
                }
            }

            doc.write(out);
            System.out.println("Saved DOCX: " + file.getAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ── Save to .pdf ─────────────────────────────────────────────
    public static void savePdf(String html, File file) {
        try {
            Document doc = new Document();
            PdfWriter writer = PdfWriter.getInstance(
                doc, new FileOutputStream(file)
            );
            doc.open();

            // Wrap in full HTML structure so XMLWorker parses it correctly
            String fullHtml = "<!DOCTYPE html><html><head>"
                + "<meta charset=\"UTF-8\"/>"
                + "</head><body>"
                + html
                + "</body></html>";

            InputStream is = new ByteArrayInputStream(
                fullHtml.getBytes(StandardCharsets.UTF_8)
            );

            XMLWorkerHelper.getInstance().parseXHtml(writer, doc, is);
            doc.close();
            System.out.println("Saved PDF: " + file.getAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ── Open .docx → HTML ─────────────────────────────────────────
    public static String loadDocx(File file) {
        StringBuilder sb = new StringBuilder();

        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument doc = new XWPFDocument(fis)) {

            for (XWPFParagraph para : doc.getParagraphs()) {
                sb.append("<p>");

                for (XWPFRun run : para.getRuns()) {
                    String text = run.getText(0);
                    if (text == null || text.isEmpty()) continue;

                    // Wrap in formatting tags based on run style
                    if (run.isBold())   text = "<b>" + text + "</b>";
                    if (run.isItalic()) text = "<i>" + text + "</i>";
                    if (run.getUnderline() != null
                            && run.getUnderline() != UnderlinePatterns.NONE) {
                        text = "<u>" + text + "</u>";
                    }

                    // Preserve font size if set
                    Double fs = run.getFontSizeAsDouble();
                    if (fs != null && fs > 0){
                        text = "<span style=\"font-size:" + fs + "px\">"
                             + text + "</span>";
                    }

                    sb.append(text);
                }

                sb.append("</p>");
            }

            System.out.println("Loaded DOCX: " + file.getAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
        }

        return sb.toString();
    }
}