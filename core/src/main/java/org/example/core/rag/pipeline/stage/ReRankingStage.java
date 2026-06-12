package org.example.core.rag.pipeline.stage;

import lombok.extern.slf4j.Slf4j;
import org.example.core.rag.context.RagContext;
import org.example.core.rag.pipeline.PipelineStage;
import org.example.core.rerank.ReRanker;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 文档重排序阶段
 * 使用 MMR 或其他算法对文档进行重排序
 */
@Slf4j
public class ReRankingStage implements PipelineStage {
    
    private final ReRanker reRanker;
    
    public ReRankingStage(ReRanker reRanker) {
        this.reRanker = reRanker;
    }
    
    @Override
    public void process(RagContext context) {
        List<Document> docs = context.getFilteredDocs();
        
        if (docs == null || docs.isEmpty()) {
            log.debug("无过滤结果，跳过重排序");
            return;
        }
        
        if (reRanker == null) {
            log.debug("ReRanker 未配置，跳过重排序");
            context.setRerankedDocs(docs);
            return;
        }
        
        log.debug("开始重排序 - 初始文档数: {}", docs.size());
        
        String query = context.getExpandedQuery() != null ? 
            context.getExpandedQuery() : context.getOriginalQuestion();
        
        int topK = context.getRetrievalConfig().topK();
        double lambda = context.getRetrievalConfig().lambda();
        
        List<Document> reranked = reRanker.rerank(docs, query, topK, String.valueOf(lambda));
        context.setRerankedDocs(reranked);
        
        log.info("重排序完成: {} -> {} 个文档", docs.size(), reranked.size());
    }
    
    @Override
    public String getName() {
        return "ReRankingStage";
    }
    
    @Override
    public boolean shouldSkip(RagContext context) {
        List<Document> docs = context.getFilteredDocs();
        return docs == null || docs.isEmpty() || reRanker == null;
    }
}
