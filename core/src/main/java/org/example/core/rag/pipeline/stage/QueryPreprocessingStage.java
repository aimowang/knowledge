package org.example.core.rag.pipeline.stage;

import lombok.extern.slf4j.Slf4j;
import org.example.core.rag.context.RagContext;
import org.example.core.rag.pipeline.PipelineStage;
import org.springframework.stereotype.Component;

/**
 * 查询预处理阶段
 * 负责清理和标准化用户查询
 */
@Slf4j
@Component
public class QueryPreprocessingStage implements PipelineStage {
    
    @Override
    public void process(RagContext context) {
        String originalQuery = context.getOriginalQuestion();
        
        if (originalQuery == null || originalQuery.trim().isEmpty()) {
            log.warn("原始查询为空");
            context.setPreprocessedQuery("");
            return;
        }
        
        // 1. 去除首尾空格
        String preprocessed = originalQuery.trim();
        
        // 2. 规范化空白字符（多个空格合并为一个）
        preprocessed = preprocessed.replaceAll("\\s+", " ");
        
        // 3. 记录预处理结果
        context.setPreprocessedQuery(preprocessed);
        
        if (!preprocessed.equals(originalQuery)) {
            log.debug("查询预处理: '{}' -> '{}'", 
                truncate(originalQuery), truncate(preprocessed));
        }
    }
    
    @Override
    public String getName() {
        return "QueryPreprocessingStage";
    }
    
    @Override
    public String getDescription() {
        return "查询预处理：清理和标准化用户输入";
    }
    
    /**
     * 截断长字符串用于日志
     */
    private String truncate(String text) {
        if (text == null) return "";
        return text.length() > 50 ? text.substring(0, 50) + "..." : text;
    }
}
