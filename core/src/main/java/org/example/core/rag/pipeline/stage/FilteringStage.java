package org.example.core.rag.pipeline.stage;

import lombok.extern.slf4j.Slf4j;
import org.example.core.rag.context.RagContext;
import org.example.core.rag.pipeline.PipelineStage;
import org.example.core.rag.strategy.DocumentProcessingStrategy;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 文档过滤阶段
 * 根据质量、相关性等指标过滤低质量文档
 */
@Slf4j
public class FilteringStage implements PipelineStage {
    
    private final DocumentProcessingStrategy filteringStrategy;
    
    public FilteringStage(DocumentProcessingStrategy filteringStrategy) {
        this.filteringStrategy = filteringStrategy;
    }
    
    @Override
    public void process(RagContext context) {
        List<Document> docs = context.getDeduplicatedDocs();
        
        if (docs == null || docs.isEmpty()) {
            log.debug("无去重结果，跳过过滤");
            return;
        }
        
        log.debug("开始过滤 - 初始文档数: {}", docs.size());
        
        List<Document> filtered = filteringStrategy.process(docs, context);
        context.setFilteredDocs(filtered);
        
        log.info("过滤完成: {} -> {} 个文档", docs.size(), filtered.size());
    }
    
    @Override
    public String getName() {
        return "FilteringStage";
    }
    
    @Override
    public boolean shouldSkip(RagContext context) {
        List<Document> docs = context.getDeduplicatedDocs();
        return docs == null || docs.isEmpty();
    }
}
