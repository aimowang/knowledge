package org.example.core.rag.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.core.compress.HybridCompressor;
import org.example.core.rag.context.RagContext;
import org.example.core.rag.strategy.DocumentProcessingStrategy;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文档压缩策略
 * 使用 HybridCompressor 进行上下文压缩，减少 token 消耗
 */
@Slf4j
@Component
public class CompressionStrategy implements DocumentProcessingStrategy {
    
    private final HybridCompressor hybridCompressor;
    
    public CompressionStrategy(HybridCompressor hybridCompressor) {
        this.hybridCompressor = hybridCompressor;
    }
    
    @Override
    public List<Document> process(List<Document> documents, RagContext context) {
        if (documents == null || documents.isEmpty()) {
            return documents;
        }
        
        if (hybridCompressor != null) {
            try {
                log.info("使用 HybridCompressor 进行上下文压缩");
                String query = context.getCurrentQuery();
                List<Document> compressed = hybridCompressor.compress(documents, query);
                log.info("压缩后文档数: {} -> {}", documents.size(), compressed.size());
                return compressed;
            } catch (Exception e) {
                log.error("HybridCompressor 压缩失败，降级为简单截断", e);
            }
        }
        
        // 降级策略：简单截断，保留前 5 个文档
        if (documents.size() > 5) {
            log.debug("使用简单截断策略，保留前 5 个文档");
            return documents.subList(0, 5);
        }
        
        return documents;
    }
    
    @Override
    public ProcessingType getType() {
        return ProcessingType.COMPRESSION;
    }
    
    @Override
    public String getName() {
        return "CompressionStrategy";
    }
}
