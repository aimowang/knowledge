package org.example.core.rag.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.example.core.rag.context.RagContext;
import org.example.model.RagAnswer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 默认 RAG 管道实现 - 责任链模式
 * 按顺序执行各个处理阶段
 */
@Slf4j
@Component
public class DefaultRagPipeline implements RagPipeline {
    
    private final List<PipelineStage> stages = new ArrayList<>();
    
    @Override
    public RagAnswer execute(RagContext context) {
        log.info("开始执行 RAG 管道 - {} 个阶段", stages.size());
        
        // 记录开始时间
        context.setStartTime(System.currentTimeMillis());
        
        // 依次执行各个阶段
        for (PipelineStage stage : stages) {
            try {
                // 检查是否跳过该阶段
                if (stage.shouldSkip(context)) {
                    log.debug("跳过阶段: {}", stage.getName());
                    continue;
                }
                
                log.debug("执行阶段: {}", stage.getName());
                long start = System.currentTimeMillis();
                
                // 执行阶段处理
                stage.process(context);
                
                // 记录阶段耗时
                long duration = System.currentTimeMillis() - start;
                context.recordStageDuration(stage.getName(), duration);
                
                log.debug("阶段完成: {}, 耗时: {}ms", stage.getName(), duration);
                
            } catch (Exception e) {
                log.error("阶段执行失败: {}, 错误: {}", stage.getName(), e.getMessage(), e);
                handleStageFailure(context, stage, e);
            }
        }
        
        log.info("RAG 管道执行完成 - 总耗时: {}ms", context.getTotalDuration());
        
        // 构建最终答案
        return buildAnswer(context);
    }
    
    @Override
    public RagPipeline addStage(PipelineStage stage) {
        this.stages.add(stage);
        log.debug("添加阶段到管道: {}", stage.getName());
        return this;
    }
    
    @Override
    public RagPipeline insertStage(int index, PipelineStage stage) {
        if (index < 0 || index > stages.size()) {
            throw new IllegalArgumentException("Invalid index: " + index);
        }
        this.stages.add(index, stage);
        log.debug("在位置 {} 插入阶段: {}", index, stage.getName());
        return this;
    }
    
    @Override
    public boolean removeStage(String stageName) {
        boolean removed = stages.removeIf(stage -> stage.getName().equals(stageName));
        if (removed) {
            log.debug("移除阶段: {}", stageName);
        }
        return removed;
    }
    
    @Override
    public List<PipelineStage> getStages() {
        return new ArrayList<>(stages);
    }
    
    /**
     * 处理阶段失败
     */
    private void handleStageFailure(RagContext context, PipelineStage stage, Exception e) {
        // 根据阶段类型决定如何处理失败
        String stageName = stage.getName();
        
        if (stageName.contains("Retrieval")) {
            // 检索失败：设置空文档列表，继续后续流程
            log.warn("检索阶段失败，使用空文档列表继续");
            context.setRetrievedDocs(List.of());
        } else if (stageName.contains("Generation")) {
            // 生成失败：设置降级答案
            log.error("生成阶段失败，返回降级答案");
            context.setAnswer("抱歉，服务暂时不可用，请稍后重试。");
            context.setSources(List.of());
        } else {
            // 其他阶段失败：记录日志，继续执行
            log.warn("阶段 {} 失败，继续执行后续阶段", stageName);
        }
    }
    
    /**
     * 构建最终答案
     */
    private RagAnswer buildAnswer(RagContext context) {
        String answer = context.getAnswer();
        List<String> sources = context.getSources();
        
        if (answer == null || answer.trim().isEmpty()) {
            answer = "该知识点暂未收录";
        }
        
        return new RagAnswer(answer, sources != null ? sources : List.of());
    }
}
