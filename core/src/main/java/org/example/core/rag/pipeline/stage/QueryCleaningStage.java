package org.example.core.rag.pipeline.stage;

import lombok.extern.slf4j.Slf4j;
import org.example.core.rag.context.RagContext;
import org.example.core.rag.pipeline.PipelineStage;

/**
 * 查询清理阶段
 * 负责基础清理和标准化用户查询
 */
@Slf4j
public class QueryCleaningStage implements PipelineStage {
    
    @Override
    public void process(RagContext context) {
        String originalQuery = context.getOriginalQuestion();
        
        if (originalQuery == null || originalQuery.trim().isEmpty()) {
            log.warn("原始查询为空");
            context.setCurrentQuery("");
            return;
        }
        
        // 1. 去除首尾空格
        String cleaned = originalQuery.trim();
        
        // 2. 规范化空白字符（多个空格合并为一个）
        cleaned = cleaned.replaceAll("\\s+", " ");
        
        context.setCurrentQuery(cleaned);
        
        if (!cleaned.equals(originalQuery)) {
            log.debug("查询清理: '{}' -> '{}'", truncate(originalQuery), truncate(cleaned));
        }
    }
    
    @Override
    public String getName() {
        return "QueryCleaningStage";
    }
    
    @Override
    public boolean shouldSkip(RagContext context) {
        String query = context.getOriginalQuestion();
        return query == null || query.trim().isEmpty();
    }
    
    /**
     * 截断长字符串用于日志
     */
    private String truncate(String text) {
        if (text == null) return "";
        return text.length() > 50 ? text.substring(0, 50) + "..." : text;
    }
}
