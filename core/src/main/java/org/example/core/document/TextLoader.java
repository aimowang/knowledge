package org.example.core.document;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/**
 * 通用文本文件加载器，支持 txt、csv、json、xml 等纯文本格式
 */
@Component
public class TextLoader implements DocumentLoader {

    private static final List<String> SUPPORTED_TYPES = List.of("txt", "csv", "json", "xml", "yaml", "yml", "properties", "log", "java", "py", "js", "ts", "html", "css");

    @Override
    public List<Document> load(File file) {
        try {
            String content = Files.readString(file.toPath());
            if (content.isBlank()) {
                return List.of();
            }
            Document doc = new Document(content);
            doc.getMetadata().put("source", file.getAbsolutePath());
            doc.getMetadata().put("title", file.getName());
            doc.getMetadata().put("category", "text");
            return List.of(doc);
        } catch (IOException e) {
            System.err.println("读取文本文件失败: " + file.getAbsolutePath());
            return List.of();
        }
    }

    @Override
    public List<String> support() {
        return SUPPORTED_TYPES;
    }
}
