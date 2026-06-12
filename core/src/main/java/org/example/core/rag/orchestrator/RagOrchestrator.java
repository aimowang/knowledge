package org.example.core.rag.orchestrator;

import org.example.core.rag.context.RagContext;
import org.example.model.RagAnswer;

/**
 * RAG 编排器接口
 * 负责协调缓存、记忆、评估等横切关注点
 */
public interface RagOrchestrator {
    
    /**
     * 执行前编排：检查缓存、加载记忆、分类复杂度
     * @param context RAG 执行上下文
     */
    void beforeExecute(RagContext context);
    
    /**
     * 执行后编排：保存记忆、触发评估、缓存结果
     * @param context RAG 执行上下文
     * @param answer RAG 答案
     */
    void afterExecute(RagContext context, RagAnswer answer);
    
    /**
     * 获取当前配置
     */
    RagOrchestratorConfig getConfig();
    
    /**
     * 更新配置
     */
    void updateConfig(RagOrchestratorConfig config);
}
