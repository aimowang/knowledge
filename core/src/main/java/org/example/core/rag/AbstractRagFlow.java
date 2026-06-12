package org.example.core.rag;

import lombok.extern.slf4j.Slf4j;
import org.example.core.rag.context.RagContext;
import org.example.core.rag.orchestrator.RagOrchestrator;
import org.example.core.rag.pipeline.RagPipeline;
import org.example.core.rerank.ReRanker;
import org.example.core.retrieval.ContentRetriever;
import org.example.model.RagAnswer;

import java.util.List;

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
    
    public AbstractRagFlow(RagPipeline pipeline, RagOrchestrator orchestrator) {
        this.pipeline = pipeline;
        this.orchestrator = orchestrator;
        
        // 子类配置管道和编排器
        configurePipeline(pipeline);
        configureOrchestrator(orchestrator);
        
        log.info("{} 初始化完成", getClass().getSimpleName());
    }
    
    @Override
    public RagAnswer executeRag(String question, String userId, String source) {
        log.debug("开始执行 RAG 流程 - 用户: {}, 来源: {}", userId, source);
        
        // 1. 创建上下文
        RagContext context = createContext(question, userId, source);
        
        // 2. 执行前编排（缓存检查、加载记忆、分类复杂度）
        orchestrator.beforeExecute(context);
        
        // 3. 检查缓存命中
        if (isCacheHit(context)) {
            log.info("✅ 缓存命中 - 用户: {}", userId);
            return buildCachedAnswer(context);
        }
        
        // 4. 执行管道
        RagAnswer answer = pipeline.execute(context);
        
        // 5. 执行后编排（保存记忆、触发评估、缓存结果）
        orchestrator.afterExecute(context, answer);
        
        log.debug("RAG 流程完成 - 来源数: {}", answer.getSources().size());
        return answer;
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
    
    // ==================== RagFlow 接口的默认实现 ====================
    
    @Override
    public String overrideQuery(String query) {
        // 默认不重写查询，子类可以override
        return query;
    }
    
    @Override
    public List<String> multiQuery(String query) {
        // 默认不支持多查询，子类可以override
        return List.of();
    }
    
    @Override
    public ContentRetriever getContextRetriever() {
        // 默认返回 null，子类可以override
        return null;
    }
    
    @Override
    public ReRanker getReRanker() {
        // 默认返回 null，子类可以override
        return null;
    }
}
