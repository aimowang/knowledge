package org.example.core.rag;

import lombok.extern.slf4j.Slf4j;
import org.example.core.metrics.RagMetrics;
import org.example.core.rag.context.RagContext;
import org.example.core.rag.orchestrator.RagOrchestrator;
import org.example.core.rag.pipeline.RagPipeline;
import org.example.model.RagAnswer;

/**
 * RAG 流程抽象基类 - 简化版
 * 
 * 设计原则：
 * 1. 极简核心：只负责编排管道和协调器
 * 2. 配置化：通过配置管道阶段实现不同功能
 * 3. 消除重复：所有通用逻辑在管道和编排器中
 */
@Slf4j
public abstract class AbstractRagFlow implements RagFlow {
    
    protected final RagPipeline pipeline;
    protected final RagOrchestrator orchestrator;
    protected final RagMetrics ragMetrics;
    
    public AbstractRagFlow(RagPipeline pipeline, RagOrchestrator orchestrator, RagMetrics ragMetrics) {
        this.pipeline = pipeline;
        this.orchestrator = orchestrator;
        this.ragMetrics = ragMetrics;
        
        // 子类配置管道和编排器
        configurePipeline(pipeline);
        configureOrchestrator(orchestrator);
        
        log.info("{} 初始化完成", getClass().getSimpleName());
    }
    
    @Override
    public RagAnswer executeRag(String question, String userId, String source) {
        log.debug("开始执行 RAG 流程 - 用户: {}, 来源: {}", userId, source);
        
        // 记录开始时间
        long startTime = System.currentTimeMillis();
        
        try {
            // 1. 创建上下文
            RagContext context = createContext(question, userId, source);
            
            // 2. 执行前编排（缓存检查、加载记忆、分类复杂度）
            orchestrator.beforeExecute(context);
            
            // 3. 检查缓存命中
            if (isCacheHit(context)) {
                log.info("✅ 缓存命中 - 用户: {}", userId);
                RagAnswer answer = buildCachedAnswer(context);
                
                // 记录缓存命中的耗时
                recordTotalDuration(startTime);
                return answer;
            }
            
            // 4. 执行管道
            RagAnswer answer = pipeline.execute(context);
            
            // 5. 执行后编排（保存记忆、触发评估、缓存结果）
            orchestrator.afterExecute(context, answer);
            
            log.debug("RAG 流程完成 - 来源数: {}", answer.getSources().size());
            
            // 记录总耗时
            recordTotalDuration(startTime);
            return answer;
            
        } catch (Exception e) {
            // 异常时也记录耗时
            recordTotalDuration(startTime);
            log.error("RAG 流程执行失败: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 钩子方法：配置管道阶段
     * 子类必须实现此方法来定义自己的处理流程
     */
    protected abstract void configurePipeline(RagPipeline pipeline);
    
    /**
     * 钩子方法：配置编排器
     * 子类可以启用/禁用特定功能
     */
    protected abstract void configureOrchestrator(RagOrchestrator orchestrator);
    
    /**
     * 创建 RAG 上下文
     */
    protected RagContext createContext(String question, String userId, String source) {
        return RagContext.builder()
            .originalQuestion(question)
            .userId(userId)
            .source(source)
            .build();
    }
    
    /**
     * 检查是否缓存命中
     */
    protected boolean isCacheHit(RagContext context) {
        Boolean cacheHit = context.getMetadata("cacheHit", Boolean.class);
        return cacheHit != null && cacheHit;
    }
    
    /**
     * 构建缓存答案
     */
    protected RagAnswer buildCachedAnswer(RagContext context) {
        return new RagAnswer(context.getAnswer(), context.getSources());
    }
    
    /**
     * 记录 RAG 总耗时
     */
    protected void recordTotalDuration(long startTime) {
        if (ragMetrics != null) {
            long durationMs = System.currentTimeMillis() - startTime;
            ragMetrics.recordRagAnswerDuration(durationMs / 1000.0);
            log.debug("RAG 总耗时: {}ms", durationMs);
        }
    }
}
