package org.example.core.rag.pipeline;

import org.example.core.rag.context.RagContext;
import org.example.model.RagAnswer;

/**
 * RAG 处理管道接口 - 责任链模式
 * 负责编排各个处理阶段的执行
 */
public interface RagPipeline {
    
    /**
     * 执行管道，返回最终答案
     * @param context RAG 执行上下文
     * @return RAG 答案
     */
    RagAnswer execute(RagContext context);
    
    /**
     * 添加处理阶段到管道末尾
     * @param stage 处理阶段
     * @return 当前管道实例（支持链式调用）
     */
    RagPipeline addStage(PipelineStage stage);
    
    /**
     * 在指定位置插入处理阶段
     * @param index 插入位置
     * @param stage 处理阶段
     * @return 当前管道实例
     */
    RagPipeline insertStage(int index, PipelineStage stage);
    
    /**
     * 移除处理阶段
     * @param stageName 阶段名称
     * @return 是否移除成功
     */
    boolean removeStage(String stageName);
    
    /**
     * 获取所有阶段
     * @return 阶段列表
     */
    java.util.List<PipelineStage> getStages();
}
