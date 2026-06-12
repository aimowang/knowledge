package org.example.core.rag.pipeline.stage;

import lombok.extern.slf4j.Slf4j;
import org.example.core.rag.context.RagContext;
import org.example.core.rag.pipeline.PipelineStage;
import org.example.core.retrieval.ContentRetriever;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 检索阶段
 * 负责从向量数据库中检索相关文档
 */
@Slf4j
@Component
public class RetrievalStage implements PipelineStage {
    
    private final ContentRetriever contentRetriever;
    
    public RetrievalStage(ContentRetriever contentRetriever) {
        this.contentRetriever = contentRetriever;
    }
    
    @Override
    public void process(RagContext context) {
        String query = determineQuery(context);
        String source = context.getSource();
        
        log.debug("执行检索 - 查询: {}, 来源: {}", truncate(query), source);
        
        try {
            // 执行检索
            List<Document> docs = contentRetriever.retrieve(query, source);
            
            if (docs == null) {
                docs = new ArrayList<>();
            }
            
            context.setRetrievedDocs(docs);
            
            log.info("检索完成 - 获得 {} 个文档", docs.size());
            
        } catch (Exception e) {
            log.error("检索失败: {}", e.getMessage(), e);
            context.setRetrievedDocs(List.of());
            throw e; // 重新抛出，让管道处理
        }
    }
    
    @Override
    public String getName() {
        return "RetrievalStage";
    }
    
    @Override
    public String getDescription() {
        return "文档检索：从向量数据库检索相关文档";
    }
    
    /**
     * 确定使用哪个查询进行检索
     */
    private String determineQuery(RagContext context) {
        // 优先级：扩展查询 > 增强查询 > 预处理查询 > 原始查询
        if (context.getExpandedQuery() != null && !context.getExpandedQuery().trim().isEmpty()) {
            return context.getExpandedQuery();
        }
        if (context.getEnhancedQuery() != null && !context.getEnhancedQuery().trim().isEmpty()) {
            return context.getEnhancedQuery();
        }
        if (context.getPreprocessedQuery() != null && !context.getPreprocessedQuery().trim().isEmpty()) {
            return context.getPreprocessedQuery();
        }
        return context.getOriginalQuestion();
    }
    
    /**
     * 截断长字符串用于日志
     */
    private String truncate(String text) {
        if (text == null) return "";
        return text.length() > 50 ? text.substring(0, 50) + "..." : text;
    }
}
