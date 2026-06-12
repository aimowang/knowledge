package org.example.core.rag.pipeline.stage;

import lombok.extern.slf4j.Slf4j;
import org.example.core.rag.context.RagContext;
import org.example.core.rag.pipeline.PipelineStage;
import org.example.core.rag.strategy.QueryEnhancementStrategy;

import java.util.List;

/**
 * 查询增强阶段
 * 应用查询增强策略（指代消解、关键词扩展等）
 */
@Slf4j
public class QueryEnhancementStage implements PipelineStage {
    
    private final List<QueryEnhancementStrategy> enhancementStrategies;
    
    public QueryEnhancementStage(List<QueryEnhancementStrategy> enhancementStrategies) {
        this.enhancementStrategies = enhancementStrategies != null ? enhancementStrategies : List.of();
    }
    
    @Override
    public void process(RagContext context) {
        String query = context.getCurrentQuery();
        
        if (query == null || query.isEmpty()) {
            log.debug("无当前查询，跳过增强");
            context.setCurrentQuery("");
            return;
        }
        
        // 应用所有支持的增强策略
        String enhanced = applyEnhancements(query, context);
        context.setCurrentQuery(enhanced);
        
        if (!enhanced.equals(query)) {
            log.info("查询增强完成: '{}' -> '{}'", truncate(query), truncate(enhanced));
        }
    }
    
    /**
     * 应用查询增强策略
     */
    private String applyEnhancements(String query, RagContext context) {
        String enhanced = query;
        
        for (QueryEnhancementStrategy strategy : enhancementStrategies) {
            // 检查策略是否支持当前复杂度级别
            if (strategy.supports(context.getComplexity())) {
                try {
                    String result = strategy.enhance(enhanced, context);
                    if (result != null && !result.equals(enhanced)) {
                        log.debug("应用增强策略 {}: '{}' -> '{}'", 
                            strategy.getName(), truncate(enhanced), truncate(result));
                        enhanced = result;
                    }
                } catch (Exception e) {
                    log.warn("增强策略 {} 失败，使用原查询: {}", strategy.getName(), e.getMessage());
                }
            } else {
                log.debug("跳过增强策略 {}（不支持当前复杂度）", strategy.getName());
            }
        }
        
        return enhanced;
    }
    
    @Override
    public String getName() {
        return "QueryEnhancementStage";
    }
    
    @Override
    public boolean shouldSkip(RagContext context) {
        // 如果没有增强策略，跳过
        return enhancementStrategies.isEmpty();
    }
    
    /**
     * 截断长字符串用于日志
     */
    private String truncate(String text) {
        if (text == null) return "";
        return text.length() > 50 ? text.substring(0, 50) + "..." : text;
    }
}
