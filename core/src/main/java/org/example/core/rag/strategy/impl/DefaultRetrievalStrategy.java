package org.example.core.rag.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.core.rag.context.RagContext;
import org.example.core.rag.strategy.RetrievalStrategy;
import org.example.core.retrieval.ContentRetriever;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 默认检索策略实现
 * 基于 ContentRetriever 进行向量检索
 */
@Slf4j
public class DefaultRetrievalStrategy implements RetrievalStrategy {
    
    private final ContentRetriever contentRetriever;
    
    public DefaultRetrievalStrategy(ContentRetriever contentRetriever) {
        this.contentRetriever = contentRetriever;
    }
    
    @Override
    public List<Document> retrieve(String query, RagContext context) {
        String source = context.getSource();
        
        log.debug("使用默认检索策略 - 查询: {}, 来源: {}", query, source);
        
        // 从上下文中获取检索配置
        int topK = context.getRetrievalConfig() != null ? 
            context.getRetrievalConfig().topK() : 5;
        
        // 执行检索（使用带 topK 的方法）
        return contentRetriever.retrieve(query, topK, 0.7, source);
    }
    
    @Override
    public boolean supportsParallel() {
        // 默认不支持并行检索
        return false;
    }
    
    @Override
    public String getName() {
        return "DefaultRetrievalStrategy";
    }
}
