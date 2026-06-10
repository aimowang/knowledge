package org.example.core.document;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Component
public class PdfLoader implements DocumentLoader {

    @Override
    public List<Document> load(File file) {

        try {
            // 1. 配置 PDF 读取器
            PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                    .withPageExtractedTextFormatter(
                            org.springframework.ai.reader.ExtractedTextFormatter.builder()
                                    .withNumberOfTopPagesToSkipBeforeDelete(0)
                                    .build())
                    .withPagesPerDocument(1)  // 每页一个文档
                    .build();
            
            // 2. 使用 ParagraphPdfDocumentReader 读取 PDF 文件
            ParagraphPdfDocumentReader pdfReader = new ParagraphPdfDocumentReader(
                    new org.springframework.core.io.FileSystemResource(file),
                    config
            );
            
            // 3. 读取原始文档
            List<Document> rawDocuments = pdfReader.get();
            
            return rawDocuments;
        } catch (Exception e) {
            System.err.println("读取 PDF 文件失败: " + file.getAbsolutePath());
        }
        
        return new ArrayList<>();
    }

    @Override
    public List<String> support() {
        return List.of("pdf");
    }
}
