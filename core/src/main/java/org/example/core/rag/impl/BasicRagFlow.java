package org.example.core.rag.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.core.rag.AbstractRagFlow;
import org.example.core.rag.orchestrator.DefaultRagOrchestrator;
import org.example.core.rag.orchestrator.RagOrchestrator;
import org.example.core.rag.pipeline.RagPipeline;
import org.example.core.rag.pipeline.DefaultRagPipeline;
import org.example.core.rag.pipeline.stage.GenerationStage;
import org.example.core.rag.pipeline.stage.PostProcessingStage;
import org.example.core.rag.pipeline.stage.QueryPreprocessingStage;
import org.example.core.rag.pipeline.stage.RetrievalStage;
import org.example.core.rag.strategy.impl.CompressionStrategy;
import org.example.core.rag.strategy.impl.DeduplicationStrategy;
import org.example.core.rag.strategy.impl.FilteringStrategy;
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
    private final ChatClient chatClient;
    private final ResilienceHelper resilienceHelper;

    public BasicRagFlow(DefaultRagPipeline pipeline,
                       DefaultRagOrchestrator orchestrator,
                       ContentRetriever contentRetriever,
                       DeduplicationStrategy dedupStrategy,
                       FilteringStrategy filterStrategy,
                       CompressionStrategy compressionStrategy,
                       ChatClient chatClient,
                       ResilienceHelper resilienceHelper) {
        super(pipeline, orchestrator);
        this.contentRetriever = contentRetriever;
        this.dedupStrategy = dedupStrategy;
        this.filterStrategy = filterStrategy;
        this.compressionStrategy = compressionStrategy;
        this.chatClient = chatClient;
        this.resilienceHelper = resilienceHelper;
        log.info("BasicRagFlow 初始化完成 - 基于新架构");
    }

    @Override
    protected void configurePipeline(RagPipeline pipeline) {
        // 配置简单管道：不包含多查询、CRAG 等高级功能
        pipeline.addStage(new QueryPreprocessingStage())
                .addStage(new RetrievalStage(contentRetriever))
                .addStage(new PostProcessingStage(
                    dedupStrategy, 
                    filterStrategy, 
                    compressionStrategy, 
                    null)) // 不使用重排序
                .addStage(new GenerationStage(chatClient, resilienceHelper));
        
        log.debug("BasicRagFlow 管道配置完成");
    }

    @Override
    protected void configureOrchestrator(RagOrchestrator orchestrator) {
        // BasicRagFlow 禁用所有高级功能
        orchestrator.disableShortTermMemory();
        orchestrator.disableLongTermMemory();
        orchestrator.disableEvaluation();
        
        log.debug("BasicRagFlow 编排器配置完成 - 已禁用记忆和评估");
    }

    @Override
    public List<String> support() {
        return List.of(CategoryEnum.BASIC.getValue());
    }
}
