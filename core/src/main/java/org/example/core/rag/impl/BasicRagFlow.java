package org.example.core.rag.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.core.metrics.RagMetrics;
import org.example.core.rag.AbstractRagFlow;
import org.example.core.rag.orchestrator.DefaultRagOrchestrator;
import org.example.core.rag.orchestrator.RagOrchestrator;
import org.example.core.rag.orchestrator.RagOrchestratorConfig;
import org.example.core.rag.pipeline.RagPipeline;
import org.example.core.rag.pipeline.DefaultRagPipeline;
import org.example.core.rag.pipeline.stage.CompressionStage;
import org.example.core.rag.pipeline.stage.DeduplicationStage;
import org.example.core.rag.pipeline.stage.FilteringStage;
import org.example.core.rag.pipeline.stage.GenerationStage;
import org.example.core.rag.pipeline.stage.QueryCleaningStage;
import org.example.core.rag.pipeline.stage.QueryEnhancementStage;
import org.example.core.rag.pipeline.stage.RetrievalStage;
import org.example.core.rag.strategy.QueryEnhancementStrategy;
import org.example.core.rag.strategy.RetrievalStrategy;
import org.example.core.rag.strategy.impl.CompressionStrategy;
import org.example.core.rag.strategy.impl.DefaultRetrievalStrategy;
import org.example.core.rag.strategy.impl.DeduplicationStrategy;
import org.example.core.rag.strategy.impl.FilteringStrategy;
import org.example.core.rag.strategy.impl.MemoryBasedQueryEnhancer;
import org.example.core.retrieval.ContentRetriever;
import org.example.core.resilience.ResilienceHelper;
import org.example.model.enums.CategoryEnum;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基础 RAG 流程实现 - 基于新架构
 * 
 * 特点：
 * - 不使用多查询生成
 * - 不使用 CRAG 流程
 * - 使用基础的检索和压缩策略
 * - 禁用记忆和评估功能（保持简单）
 */
@Slf4j
@Component
public class BasicRagFlow extends AbstractRagFlow {

    private final ContentRetriever contentRetriever;
    private final DeduplicationStrategy dedupStrategy;
    private final FilteringStrategy filterStrategy;
    private final CompressionStrategy compressionStrategy;
    private final MemoryBasedQueryEnhancer memoryEnhancer;
    private final ChatClient chatClient;
    private final ResilienceHelper resilienceHelper;
    private final RagMetrics ragMetrics;

    public BasicRagFlow(DefaultRagPipeline pipeline,
                       DefaultRagOrchestrator orchestrator,
                       ContentRetriever contentRetriever,
                       DeduplicationStrategy dedupStrategy,
                       FilteringStrategy filterStrategy,
                       CompressionStrategy compressionStrategy,
                       MemoryBasedQueryEnhancer memoryEnhancer,
                       ChatClient chatClient,
                       ResilienceHelper resilienceHelper,
                       RagMetrics ragMetrics) {
        super(pipeline, orchestrator, ragMetrics);
        this.contentRetriever = contentRetriever;
        this.dedupStrategy = dedupStrategy;
        this.filterStrategy = filterStrategy;
        this.compressionStrategy = compressionStrategy;
        this.memoryEnhancer = memoryEnhancer;
        this.chatClient = chatClient;
        this.resilienceHelper = resilienceHelper;
        this.ragMetrics = ragMetrics;
        log.info("BasicRagFlow 初始化完成 - 基于新架构");
    }

    @Override
    protected void configurePipeline(RagPipeline pipeline) {
        // 配置简单管道：使用独立的查询处理 Stage
        // 基础版只启用记忆增强，不启用关键词扩展和多查询
        List<QueryEnhancementStrategy> strategies = List.of(memoryEnhancer);
        
        // 创建默认检索策略
        RetrievalStrategy retrievalStrategy = new DefaultRetrievalStrategy(contentRetriever);
        
        pipeline.addStage(new QueryCleaningStage())                    // 1. 清理
                .addStage(new QueryEnhancementStage(strategies))       // 2. 增强（仅记忆）
                // 3. 跳过多查询生成
                .addStage(new RetrievalStage(retrievalStrategy, ragMetrics))  // 4. 检索
                .addStage(new DeduplicationStage(dedupStrategy))
                .addStage(new FilteringStage(filterStrategy))
                .addStage(new CompressionStage(compressionStrategy))
                .addStage(new GenerationStage(chatClient, resilienceHelper, ragMetrics));
        
        log.debug("BasicRagFlow 管道配置完成 - 使用独立 Stage + 记忆增强");
    }

    @Override
    protected void configureOrchestrator(RagOrchestrator orchestrator) {
        // BasicRagFlow 禁用所有高级功能（使用默认配置）
        RagOrchestratorConfig config = RagOrchestratorConfig.defaultConfig();
        orchestrator.updateConfig(config);
        
        log.debug("BasicRagFlow 编排器配置完成 - 已禁用记忆和评估");
    }

    @Override
    public List<String> support() {
        return List.of(CategoryEnum.BASIC.getValue());
    }
}
