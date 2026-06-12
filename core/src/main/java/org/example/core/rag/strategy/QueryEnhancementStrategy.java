package org.example.core.rag.strategy;

import org.example.core.rag.context.RagContext;
import org.example.model.enums.ComplexityLevelEnum;

/**
 * 查询增强策略接口
 * 负责对原始查询进行各种增强（指代消解、关键词扩展等）
 */
public interface QueryEnhancementStrategy {
    
    /**
     * 增强查询
     * @param query 原始查询
     * @param context RAG 执行上下文
     * @return 增强后的查询
     */
    String enhance(String query, RagContext context);
    
    /**
     * 是否支持该复杂度级别
     * @param complexity 复杂度级别
     * @return true 表示支持
     */
    boolean supports(ComplexityLevelEnum complexity);
    
    /**
     * 策略名称
     * @return 策略名称
     */
    String getName();
}
