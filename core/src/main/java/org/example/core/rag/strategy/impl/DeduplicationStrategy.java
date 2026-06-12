package org.example.core.rag.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.core.rag.context.RagContext;
import org.example.core.rag.strategy.DocumentProcessingStrategy;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 文档去重策略
 * 使用内容哈希 + Jaccard 相似度进行精确和模糊去重
 */
@Slf4j
@Component
public class DeduplicationStrategy implements DocumentProcessingStrategy {
    
    @Override
    public List<Document> process(List<Document> documents, RagContext context) {
        if (documents == null || documents.isEmpty()) {
            return documents;
        }
        
        // 优化：使用内容哈希 + 相似度检测进行去重
        Map<String, Document> uniqueDocs = new LinkedHashMap<>();
        
        for (Document doc : documents) {
            if (doc.getText() == null || doc.getText().trim().isEmpty()) {
                continue;
            }
            
            // 1. 精确去重：使用内容哈希
            String contentHash = generateContentHash(doc.getText());
            
            // 2. 模糊去重：检查是否与已有文档高度相似
            boolean isDuplicate = false;
            for (String existingHash : uniqueDocs.keySet()) {
                if (isSimilarContent(contentHash, existingHash, doc.getText(), 
                                   uniqueDocs.get(existingHash).getText())) {
                    isDuplicate = true;
                    log.debug("检测到相似文档，跳过: {}", 
                        doc.getText().substring(0, Math.min(30, doc.getText().length())));
                    break;
                }
            }
            
            if (!isDuplicate) {
                uniqueDocs.put(contentHash, doc);
            }
        }
        
        log.debug("文档去重: {} -> {}", documents.size(), uniqueDocs.size());
        return new ArrayList<>(uniqueDocs.values());
    }
    
    @Override
    public ProcessingType getType() {
        return ProcessingType.DEDUPLICATION;
    }
    
    @Override
    public String getName() {
        return "DeduplicationStrategy";
    }
    
    /**
     * 生成内容哈希（用于精确去重）
     */
    private String generateContentHash(String content) {
        // 简化：使用前100个字符的 hash
        String normalized = content.trim().toLowerCase();
        String sample = normalized.length() > 100 ? normalized.substring(0, 100) : normalized;
        return String.valueOf(sample.hashCode());
    }
    
    /**
     * 检测内容是否相似（用于模糊去重）
     * 使用简单的 Jaccard 相似度
     */
    private boolean isSimilarContent(String hash1, String hash2, String text1, String text2) {
        // 如果哈希相同，肯定是重复
        if (hash1.equals(hash2)) {
            return true;
        }
        
        // 计算 Jaccard 相似度
        Set<String> words1 = tokenize(text1);
        Set<String> words2 = tokenize(text2);
        
        if (words1.isEmpty() || words2.isEmpty()) {
            return false;
        }
        
        // 交集 / 并集
        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);
        
        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);
        
        double similarity = (double) intersection.size() / union.size();
        
        // 相似度阈值：80%
        return similarity > 0.8;
    }
    
    /**
     * 文本分词（简单实现）
     */
    private Set<String> tokenize(String text) {
        // 按空格和标点分割，转为小写
        String[] tokens = text.toLowerCase().split("[\\s\\p{Punct}]+");
        Set<String> result = new HashSet<>();
        for (String token : tokens) {
            if (token.length() > 2) {  // 忽略短词
                result.add(token);
            }
        }
        return result;
    }
}
