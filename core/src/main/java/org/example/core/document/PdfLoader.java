package org.example.core.document;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class PdfLoader implements DocumentLoader {

    @Override
    public List<Document> load(File file) {
        // 先用 PagePdfDocumentReader 按页读取
        List<Document> docs = readByPage(file);
        if (!docs.isEmpty()) {
            return docs;
        }
        log.warn("按页读取为空，尝试按段落读取: {}", file.getName());
        docs = readByParagraph(file);
        if (!docs.isEmpty()) {
            return docs;
        }
        log.error("PDF 文件无法提取文本内容（可能是扫描件或无文本层）: {}", file.getName());
        return List.of();
    }

    private List<Document> readByPage(File file) {
        try {
            log.info("按页读取 PDF: {}", file.getName());
            PagePdfDocumentReader reader = new PagePdfDocumentReader(
                    new org.springframework.core.io.FileSystemResource(file),
                    PdfDocumentReaderConfig.builder()
                            .withPagesPerDocument(1)
                            .build()
            );
            List<Document> all = reader.get();
            // 过滤掉无内容的页面
            List<Document> valid = new ArrayList<>();
            for (Document doc : all) {
                String text = doc.getText();
                if (text != null && !text.isBlank()) {
                    valid.add(doc);
                }
            }
            log.info("按页读取: 共{}页, 有效{}页", all.size(), valid.size());
            return valid;
        } catch (Exception e) {
            log.error("按页读取 PDF 异常: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Document> readByParagraph(File file) {
        try {
            log.info("按段落读取 PDF: {}", file.getName());
            ParagraphPdfDocumentReader reader = new ParagraphPdfDocumentReader(
                    new org.springframework.core.io.FileSystemResource(file),
                    PdfDocumentReaderConfig.builder()
                            .withPagesPerDocument(1)
                            .build()
            );
            List<Document> all = reader.get();
            List<Document> valid = new ArrayList<>();
            for (Document doc : all) {
                String text = doc.getText();
                if (text != null && !text.isBlank()) {
                    valid.add(doc);
                }
            }
            log.info("按段落读取: 共{}段, 有效{}段", all.size(), valid.size());
            return valid;
        } catch (Exception e) {
            log.error("按段落读取 PDF 异常: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<String> support() {
        return List.of("pdf");
    }
}
