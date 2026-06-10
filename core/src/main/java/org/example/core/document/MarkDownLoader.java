package org.example.core.document;


import org.example.model.Section;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MarkDownLoader implements DocumentLoader {

    // 匹配二级标题的正则表达式：## 标题
    private static final Pattern H2_PATTERN = Pattern.compile("^##\\s+(.+)$", Pattern.MULTILINE);

    @Override
    public List<Document> load(File file) {
        List<Document> docs = new ArrayList<>();
        
        try {
            // 1. 读取文件内容
            String content = Files.readString(file.toPath());
            
            // 2. 根据二级标题分割文档
            List<Section> sections = splitByH2(content);
            
            // 3. 为每个章节创建 Document
            for (int i = 0; i < sections.size(); i++) {
                Section section = sections.get(i);
                
                // 构建完整的章节内容（包含标题）
                String sectionContent = "## " + section.getTitle() + "\n\n" + section.getContent();
                
                // 创建 Document 对象
                Document doc = new Document(sectionContent);
                
                // 添加元数据
                doc.getMetadata().put("source", file.getAbsolutePath());
                doc.getMetadata().put("title", file.getName().replace(".md", ""));
                doc.getMetadata().put("category", "markdown");
                doc.getMetadata().put("section_title", section.getTitle());  // 章节标题
                doc.getMetadata().put("section_index", i);              // 章节索引
                doc.getMetadata().put("total_sections", sections.size()); // 总章节数
                
                docs.add(doc);
            }
            
        } catch (IOException e) {
            System.err.println("读取文件失败: " + file.getAbsolutePath());
        }
        
        return docs;
    }

    /**
     * 根据二级标题分割 Markdown 文档
     * @param content Markdown 内容
     * @return 章节列表
     */
    private List<Section> splitByH2(String content) {
        List<Section> sections = new ArrayList<>();
        
        // 查找所有二级标题的位置
        Matcher matcher = H2_PATTERN.matcher(content);
        
        int lastEnd = 0;
        String currentTitle = "引言";  // 默认标题（第一个标题之前的内容）
        StringBuilder currentContent = new StringBuilder();
        
        while (matcher.find()) {
            // 如果有之前的内容，保存为一个章节
            if (lastEnd < matcher.start()) {
                String previousContent = content.substring(lastEnd, matcher.start()).trim();
                if (!previousContent.isEmpty()) {
                    sections.add(new Section(currentTitle, previousContent));
                }
            }
            
            // 更新当前标题和位置
            currentTitle = matcher.group(1).trim();
            lastEnd = matcher.end();
        }
        
        // 处理最后一个章节
        if (lastEnd < content.length()) {
            String remainingContent = content.substring(lastEnd).trim();
            if (!remainingContent.isEmpty()) {
                sections.add(new Section(currentTitle, remainingContent));
            }
        }
        
        // 如果没有找到任何二级标题，将整个文档作为一个章节
        if (sections.isEmpty()) {
            sections.add(new Section("全文", content.trim()));
        }
        
        return sections;
    }

    @Override
    public List<String> support() {
        return List.of("md");
    }
}
