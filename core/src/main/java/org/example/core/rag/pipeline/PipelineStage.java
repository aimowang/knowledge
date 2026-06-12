package org.example.core.rag.pipeline;

import org.example.core.rag.context.RagContext;

/**
 * 管道阶段接口
 * 每个阶段负责处理 RagContext 中的特定部分
 */
public interface PipelineStage {
    
    /**
     * 处理阶段
     * @param context RAG 执行上下文（会被修改）
     */
    void process(RagContext context);
    
    /**
     * 阶段名称（唯一标识）
     * @return 阶段名称
     */
    String getName();
    
    /**
     * 阶段描述
     * @return 阶段描述
     */
    default String getDescription() {
        return getName();
    }
    
    /**
     * 是否跳过该阶段
     * @param context RAG 执行上下文
     * @return true 表示跳过
     */
    default boolean shouldSkip(RagContext context) {
        return false;
    }
}
