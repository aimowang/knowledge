package org.example.core.rag.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.core.metrics.RagMetrics;
import org.example.core.rag.AbstractRagFlow;
import org.example.core.rag.handler.ComplexRAGHandler;
import org.example.core.rag.orchestrator.DefaultRagOrchestrator;
import org.example.core.rag.orchestrator.RagOrchestrator;
import org.example.core.rag.orchestrator.RagOrchestratorConfig;
import org.example.core.rag.pipeline.RagPipeline;
import org.example.core.rag.pipeline.DefaultRagPipeline;
import org.example.core.rag.pipeline.stage.CompressionStage;
import org.example.core.rag.pipeline.stage.CragStage;
import org.example.core.rag.pipeline.stage.DeduplicationStage;
import org.example.core.rag.pipeline.stage.FilteringStage;
import org.example.core.rag.pipeline.stage.GenerationStage;
import org.example.core.rag.pipeline.stage.MultiQueryGenerationStage;
import org.example.core.rag.pipeline.stage.ParallelRetrievalStage;
import org.example.core.rag.pipeline.stage.QueryCleaningStage;
import org.example.core.rag.pipeline.stage.QueryEnhancementStage;
import org.example.core.rag.pipeline.stage.ReRankingStage;
import org.example.core.rag.pipeline.stage.RetrievalStage;
import org.example.core.rag.strategy.QueryEnhancementStrategy;
import org.example.core.rag.strategy.RetrievalStrategy;
import org.example.core.rag.strategy.impl.CompressionStrategy;
import org.example.core.rag.strategy.impl.DefaultRetrievalStrategy;
import org.example.core.rag.strategy.impl.DeduplicationStrategy;
import org.example.core.rag.strategy.impl.FilteringStrategy;
import org.example.core.rag.strategy.impl.KeywordExpansionEnhancer;
import org.example.core.rag.strategy.impl.MemoryBasedQueryEnhancer;
import org.example.core.rag.strategy.impl.MultiQueryGenerator;
import org.example.core.retrieval.ContentRetriever;
import org.example.core.resilience.ResilienceHelper;
import org.example.core.rerank.ReRanker;
import org.example.core.retrieval.HybirdContentRetriever;
import org.example.model.enums.CategoryEnum;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 高级 RAG 流程实现 - 基于新架构
 * 
 * 特点：
 * - ✅ 启用短期记忆（对话历史）
 * - ✅ 启用长期记忆（用户偏好、事实）
 * - ✅ 启用质量评估（异步）
 * - ✅ 支持多查询生成 + 并行检索
 * - ✅ 支持关键词扩展
 * - ✅ 支持 CRAG（Corrective RAG）
 * - ✅ 支持重排序
 */
@Slf4j
@Component
public class AdvancedRagFlow extends AbstractRagFlow {

    private final ContentRetriever contentRetriever;
    private final DeduplicationStrategy dedupStrategy;
    private final FilteringStrategy filterStrategy;
    private final CompressionStrategy compressionStrategy;
    private final MemoryBasedQueryEnhancer memoryEnhancer;
    private final KeywordExpansionEnhancer keywordEnhancer;
    private final MultiQueryGenerator multiQueryGenerator;
    private final ComplexRAGHandler cragHandler;
    private final ReRanker reRanker;
    private final ChatClient chatClient;
    private final ResilienceHelper resilienceHelper;
    private final RagMetrics ragMetrics;

    public AdvancedRagFlow(DefaultRagPipeline pipeline,
                          DefaultRagOrchestrator orchestrator,
                           HybirdContentRetriever contentRetriever,
                          DeduplicationStrategy dedupStrategy,
                          FilteringStrategy filterStrategy,
                          CompressionStrategy compressionStrategy,
                          MemoryBasedQueryEnhancer memoryEnhancer,
                          KeywordExpansionEnhancer keywordEnhancer,
                          MultiQueryGenerator multiQueryGenerator,
                          ComplexRAGHandler cragHandler,
                          ReRanker reRanker,
                          ChatClient chatClient,
                          ResilienceHelper resilienceHelper,
                          RagMetrics ragMetrics) {
        super(pipeline, orchestrator, ragMetrics);
        this.contentRetriever = contentRetriever;
        this.dedupStrategy = dedupStrategy;
        this.filterStrategy = filterStrategy;
        this.compressionStrategy = compressionStrategy;
        this.memoryEnhancer = memoryEnhancer;
        this.keywordEnhancer = keywordEnhancer;
        this.multiQueryGenerator = multiQueryGenerator;
        this.cragHandler = cragHandler;
        this.reRanker = reRanker;
        this.chatClient = chatClient;
        this.resilienceHelper = resilienceHelper;
        this.ragMetrics = ragMetrics;
        log.info("AdvancedRagFlow 初始化完成 - 基于新架构，已启用所有高级功能");
    }

    @Override
    protected void configurePipeline(RagPipeline pipeline) {
        // 配置增强管道：使用独立的查询处理 Stage，包含所有增强策略
        List<QueryEnhancementStrategy> strategies = List.of(
            memoryEnhancer,      // 记忆增强（指代消解）
            keywordEnhancer      // 关键词扩展
        );
        
        // 创建默认检索策略
        RetrievalStrategy retrievalStrategy = new DefaultRetrievalStrategy(contentRetriever);
        
        pipeline.addStage(new QueryCleaningStage())                    // 1. 清理
                .addStage(new QueryEnhancementStage(strategies))       // 2. 增强（记忆+关键词）
                .addStage(new MultiQueryGenerationStage(multiQueryGenerator))  // 3. 多查询生成
                .addStage(new ParallelRetrievalStage(retrievalStrategy, ragMetrics))  // 4. 并行检索（如果有多个查询）
                .addStage(new RetrievalStage(retrievalStrategy, ragMetrics))  // 5. 单次检索（如果没有多查询）
                .addStage(new CragStage(cragHandler))                  // 6. CRAG 评估与修正
                .addStage(new DeduplicationStage(dedupStrategy))
//                .addStage(new FilteringStage(filterStrategy))
                .addStage(new ReRankingStage(reRanker))                // 7. 重排序
                .addStage(new CompressionStage(compressionStrategy))   // 8. 压缩
                .addStage(new GenerationStage(chatClient, resilienceHelper, ragMetrics));  // 9. 生成
        
        log.debug("AdvancedRagFlow 管道配置完成 - 使用独立 Stage + 全量查询增强 + 并行检索 + CRAG + 重排序");
    }

    @Override
    protected void configureOrchestrator(RagOrchestrator orchestrator) {
        // AdvancedRagFlow 启用所有高级功能
        RagOrchestratorConfig config = RagOrchestratorConfig.allEnabled();
        orchestrator.updateConfig(config);
        
        log.debug("AdvancedRagFlow 编排器配置完成 - 已启用记忆和评估");
    }

    @Override
    public List<String> support() {
        // AdvancedRagFlow 支持所有分类，作为默认的高级实现
        return List.of(CategoryEnum.ALL.getValue());
    }
}
