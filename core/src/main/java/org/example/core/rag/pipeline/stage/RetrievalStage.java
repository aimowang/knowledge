package org.example.core.rag.pipeline.stage;

import lombok.extern.slf4j.Slf4j;
import org.example.core.metrics.RagMetrics;
import org.example.core.rag.context.RagContext;
import org.example.core.rag.pipeline.PipelineStage;
import org.example.core.rag.strategy.RetrievalStrategy;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * 检索阶段
 * 负责从向量数据库中检索相关文档
 * 支持策略模式，可切换不同的检索策略
 */
@Slf4j
public class RetrievalStage implements PipelineStage {
    
    private final RetrievalStrategy retrievalStrategy;
    private final RagMetrics ragMetrics;
    
    public RetrievalStage(RetrievalStrategy retrievalStrategy, RagMetrics ragMetrics) {
        this.retrievalStrategy = retrievalStrategy;
        this.ragMetrics = ragMetrics;
    }
    
    @Override
    public void process(RagContext context) {
        String query = determineQuery(context);
        
        log.debug("执行检索 - 查询: {}, 策略: {}", truncate(query), retrievalStrategy.getName());
        
        try {
            // 使用策略执行检索（带指标采集）
            long startTime = System.currentTimeMillis();
            List<Document> docs = retrievalStrategy.retrieve(query, context);
            long durationMs = System.currentTimeMillis() - startTime;
            
            // 记录向量搜索指标
            if (ragMetrics != null) {
                ragMetrics.recordVectorSearch();
                ragMetrics.recordVectorSearchDuration(durationMs / 1000.0);
            }
            
            if (docs == null) {
                docs = new ArrayList<>();
            }
            
            context.setDocuments(docs);
            
            log.info("检索完成 - 获得 {} 个文档 (策略: {}, 耗时: {}ms)", 
                docs.size(), retrievalStrategy.getName(), durationMs);
            
        } catch (Exception e) {
            log.error("检索失败 (策略: {}): {}", retrievalStrategy.getName(), e.getMessage(), e);
            context.setDocuments(List.of());
            throw e; // 重新抛出，让管道处理
        }
    }
    
    @Override
    public String getName() {
        return "RetrievalStage";
    }
    
    @Override
    public boolean shouldSkip(RagContext context) {
        return retrievalStrategy == null;
    }
    
    /**
     * 确定使用哪个查询进行检索
     */
    private String determineQuery(RagContext context) {
        // 直接使用当前查询（已经被各个 Stage 更新过）
        return context.getCurrentQuery();
    }
    
    /**
     * 截断长字符串用于日志
     */
    private String truncate(String text) {
        if (text == null) return "";
        return text.length() > 50 ? text.substring(0, 50) + "..." : text;
    }
}
