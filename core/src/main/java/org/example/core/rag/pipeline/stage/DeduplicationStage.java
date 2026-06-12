package org.example.core.rag.pipeline.stage;

import lombok.extern.slf4j.Slf4j;
import org.example.core.rag.context.RagContext;
import org.example.core.rag.pipeline.PipelineStage;
import org.example.core.rag.strategy.DocumentProcessingStrategy;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 文档去重阶段
 * 使用哈希 + Jaccard 相似度进行智能去重
 */
@Slf4j
public class DeduplicationStage implements PipelineStage {
    
    private final DocumentProcessingStrategy deduplicationStrategy;
    
    public DeduplicationStage(DocumentProcessingStrategy deduplicationStrategy) {
        this.deduplicationStrategy = deduplicationStrategy;
    }
    
    @Override
    public void process(RagContext context) {
        List<Document> docs = context.getDocuments();
        
        if (docs == null || docs.isEmpty()) {
            log.debug("无检索结果，跳过去重");
            return;
        }
        
        log.debug("开始去重 - 初始文档数: {}", docs.size());
        
        List<Document> deduplicated = deduplicationStrategy.process(docs, context);
        context.setDocuments(deduplicated);
        
        log.info("去重完成: {} -> {} 个文档", docs.size(), deduplicated.size());
    }
    
    @Override
    public String getName() {
        return "DeduplicationStage";
    }
    
    @Override
    public boolean shouldSkip(RagContext context) {
        List<Document> docs = context.getDocuments();
        return docs == null || docs.isEmpty();
    }
}
