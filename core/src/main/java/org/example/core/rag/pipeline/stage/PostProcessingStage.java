package org.example.core.rag.pipeline.stage;

import lombok.extern.slf4j.Slf4j;
import org.example.core.rag.context.RagContext;
import org.example.core.rag.pipeline.PipelineStage;
import org.example.core.rag.strategy.DocumentProcessingStrategy;
import org.example.core.rerank.ReRanker;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 后处理阶段
 * 负责对检索到的文档进行去重、过滤、重排序、压缩等处理
 */
@Slf4j
@Component
public class PostProcessingStage implements PipelineStage {
    
    private final DocumentProcessingStrategy deduplicationStrategy;
    private final DocumentProcessingStrategy filteringStrategy;
    private final DocumentProcessingStrategy compressionStrategy;
    private final ReRanker reRanker;
    
    public PostProcessingStage(DocumentProcessingStrategy deduplicationStrategy,
                              DocumentProcessingStrategy filteringStrategy,
                              DocumentProcessingStrategy compressionStrategy,
                              ReRanker reRanker) {
        this.deduplicationStrategy = deduplicationStrategy;
        this.filteringStrategy = filteringStrategy;
        this.compressionStrategy = compressionStrategy;
        this.reRanker = reRanker;
    }
    
    @Override
    public void process(RagContext context) {
        List<Document> docs = context.getRetrievedDocs();
        
        if (docs == null || docs.isEmpty()) {
            log.debug("无检索结果，跳过后处理");
            context.setFinalDocs(List.of());
            return;
        }
        
        log.debug("开始后处理 - 初始文档数: {}", docs.size());
        
        // 1. 去重
        docs = deduplicationStrategy.process(docs, context);
        context.setDeduplicatedDocs(docs);
        log.debug("去重后: {} 个文档", docs.size());
        
        // 2. 过滤
        docs = filteringStrategy.process(docs, context);
        context.setFilteredDocs(docs);
        log.debug("过滤后: {} 个文档", docs.size());
        
        // 3. 重排序（如果配置了 ReRanker）
        if (reRanker != null && !docs.isEmpty()) {
            String query = context.getExpandedQuery() != null ? 
                context.getExpandedQuery() : context.getOriginalQuestion();
            
            int topK = context.getRetrievalConfig().topK();
            double lambda = context.getRetrievalConfig().lambda();
            
            docs = reRanker.rerank(docs, query, topK, String.valueOf(lambda));
            context.setRerankedDocs(docs);
            log.debug("重排序后: {} 个文档", docs.size());
        }
        
        // 4. 压缩
        docs = compressionStrategy.process(docs, context);
        context.setFinalDocs(docs);
        log.info("后处理完成 - 最终文档数: {}", docs.size());
    }
    
    @Override
    public String getName() {
        return "PostProcessingStage";
    }
    
    @Override
    public String getDescription() {
        return "文档后处理：去重、过滤、重排序、压缩";
    }
}
