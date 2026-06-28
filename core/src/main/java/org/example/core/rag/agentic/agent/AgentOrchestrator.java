package org.example.core.rag.agentic.agent;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.example.core.rag.agentic.quality.ContextVerdict;
import org.example.core.rag.agentic.quality.CorrectiveRepair;
import org.example.core.rag.agentic.quality.LlmJudge;
import org.example.core.rag.agentic.quality.QualityScores;
import org.example.core.rag.agentic.quality.ReflectionReport;
import org.example.core.rag.agentic.quality.SelfReflection;
import org.example.core.rag.agentic.quality.SufficientContextAgent;
import org.example.core.rag.agentic.transform.QueryDecomposer;
import org.example.core.rag.agentic.transform.TransformationGate;
import org.example.core.rag.agentic.sandbox.SandboxConfigurator;
import org.example.core.metrics.RagMetrics;
import org.example.core.rag.agentic.tool.ToolRegistry;
import org.example.core.rag.agentic.trajectory.TrajectoryRecorder;
import org.example.core.rag.agentic.tool.ToolResult;
import org.example.core.rag.agentic.trajectory.StepRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Agent 主循环引擎 — 基于 AgentScope HarnessAgent。
 *
 * <p>核心职责：
 * <ol>
 *   <li>构建并管理 HarnessAgent 实例（工作区、工具、Hook 管道）</li>
 *   <li>通过 RuntimeContext 传递多租户上下文</li>
 *   <li>协调 Quality Pipeline 各阶段</li>
 *   <li>控制循环边界（最大次数/超时/异常降级）</li>
 * </ol>
 */
@Component
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    private final ToolRegistry toolRegistry;
    private final AgentConfig config;
    private final TrajectoryRecorder recorder;
    /** 全能力模型 — 答案生成/纠错等重量任务 */
    private final ChatClient fullChatClient;

    /** 快速模型 — 分类/检查/评分/转换等轻量任务 */
    private final ChatClient fastChatClient;
    private final SandboxConfigurator sandboxConfigurator;
    private final RagMetrics ragMetrics;

    @Value("${spring.ai.dashscope.api-key}")
    private String dashScopeApiKey;

    private HarnessAgent harnessAgent;

    public AgentOrchestrator(ToolRegistry toolRegistry,
                             AgentConfig config,
                             TrajectoryRecorder recorder,
                             @Qualifier("fullChatClient") ChatClient fullChatClient,
                             @Qualifier("fastChatClient") ChatClient fastChatClient,
                             SandboxConfigurator sandboxConfigurator,
                             RagMetrics ragMetrics) {
        this.toolRegistry = toolRegistry;
        this.config = config;
        this.recorder = recorder;
        this.fullChatClient = fullChatClient;
        this.fastChatClient = fastChatClient;
        this.sandboxConfigurator = sandboxConfigurator;
        this.ragMetrics = ragMetrics;
    }

    @PostConstruct
    public void init() {
        if (!config.isEnabled()) {
            log.info("Agentic RAG 已禁用，跳过 HarnessAgent 初始化");
            return;
        }
        this.harnessAgent = buildHarnessAgent();
        log.info("HarnessAgent 初始化完成: name={}, tools={}, workspace={}",
            config.getAgent().getName(),
            toolRegistry.getToolNames(),
            config.getWorkspace().getPath());
    }

    /**
     * 构建 HarnessAgent。
     */
    private HarnessAgent buildHarnessAgent() {
        HarnessAgent.Builder builder = HarnessAgent.builder()
            .name(config.getAgent().getName())
            .workspace(java.nio.file.Paths.get(config.getWorkspace().getPath()))
            .model(DashScopeChatModel.builder()
                .apiKey(dashScopeApiKey)
                .modelName(config.getAgent().getModel())
                .stream(config.getAgent().isEnableStreaming())
                .build())
            .maxIters(config.getAgent().getMaxLoops())
            .middleware(new ToolCallCaptureMiddleware());

        // 可选：按需启用沙箱隔离
        sandboxConfigurator.configure(builder);

        return builder.build();
    }

    /**
     * 执行 Agent 主循环。
     */
    public synchronized AgentState execute(String query, String userId) {
        AgentState state = new AgentState(query, userId, config);
        long startTime = System.currentTimeMillis();

        // 设置 MDC 上下文（用于 JSON 日志关联）
        MDC.put("trajectoryId", state.getTrajectoryId());
        MDC.put("userId", userId);

        try {
            // ════════════════════════════════════════════════════════
            // 阶段 1: HarnessAgent 自主推理（ReAct 循环）
            // ════════════════════════════════════════════════════════
            RuntimeContext ctx = RuntimeContext.builder()
                .userId(userId)
                .sessionId(state.getTrajectoryId())
                .put("query", query)
                .put("agentState", state)
                .build();
            // FR-12.4: 转换门控 - 简单查询跳过深度转换
            TransformationGate gate = new TransformationGate(config.getRouting().getSimpleThreshold());
            String processedQuery = query;

            // FR-12.1: 查询分解 - 复合问题拆解为原子子查询
            if (gate.shouldTransform(query)) {
                QueryDecomposer decomposer = new QueryDecomposer(fastChatClient);
                var subQueries = decomposer.decompose(query);
                if (subQueries != null && !subQueries.isEmpty()) {
                    java.util.List<String> queries = subQueries.stream()
                        .map(sq -> sq.getQuery())
                        .collect(java.util.stream.Collectors.toList());
                    state.getSubQueriesInternal().addAll(queries);
                    processedQuery = String.join(" ", queries);
                    state.addStepRecord(StepRecord.builder()
                        .stepNumber(1).loopNumber(0).type("QUERY_DECOMPOSE")
                        .description("查询分解为 " + queries.size() + " 个子查询")
                        .build());
                }
            }
            Msg response = harnessAgent.call(
                Msg.builder()
                    .textContent(processedQuery)
                    .build(),
                ctx
            ).block(Duration.ofMillis(config.getAgent().getMaxTimeoutMs()));

            if (response == null) {
                throw new TimeoutException("HarnessAgent 调用超时");
            }

            state.setAgentRawResponse(response.getTextContent());
            state.setSynthesizedContext(response.getTextContent());
            state.addStepRecord(StepRecord.builder()
                .stepNumber(2).loopNumber(0).type("AGENT_REASONING")
                .description("Agent 自主推理完成")
                .durationMs(System.currentTimeMillis() - startTime)
                .build());

            // Step-Back 查询：当检索结果过短或无结果时放宽查询
            if (response.getTextContent() == null || response.getTextContent().length() < 50) {
                org.example.core.rag.agentic.transform.StepBackQuery stepBack = 
                    new org.example.core.rag.agentic.transform.StepBackQuery(fastChatClient);
                String steppedQuery = stepBack.stepBack(query, "检索结果为空或过短");
                if (!steppedQuery.equals(query)) {
                    log.info("Step-Back 查询: '{}' → '{}'", query, steppedQuery);
                    state.addStepRecord(StepRecord.builder()
                        .stepNumber(2).loopNumber(0).type("STEP_BACK_QUERY")
                        .description("退一步查询: " + steppedQuery)
                        .build());
                    // 用放宽的查询重新调用 Agent
                    Msg stepBackResponse = harnessAgent.call(
                        Msg.builder().textContent(steppedQuery).build(), ctx
                    ).block(Duration.ofMillis(config.getAgent().getMaxTimeoutMs()));
                    if (stepBackResponse != null) {
                        state.setSynthesizedContext(stepBackResponse.getTextContent());
                    }
                }
            }

            // ════════════════════════════════════════════════════════
            // 阶段 2: SufficientContextAgent 完备性检查
            // ════════════════════════════════════════════════════════
            if (config.getQuality().getContextCheck().isEnabled()) {
                SufficientContextAgent contextAgent = new SufficientContextAgent(fastChatClient);
                ContextVerdict verdict = contextAgent.check(query, state.getSynthesizedContext());

                int retryCount = 0;
                while (!verdict.isSufficient()
                    && retryCount < config.getAgent().getMaxContextRetries()) {
                    String supplementalQuery = verdict.getMissingInfoQuery();
                    log.info("Context 不完备，补充检索: {}", supplementalQuery);

                    Msg supplementalResponse = harnessAgent.call(
                        Msg.builder()
                            .textContent("补充检索：" + supplementalQuery)
                            .build(),
                        ctx
                    ).block(Duration.ofMillis(config.getAgent().getSingleToolTimeoutMs()));

                    retryCount++;
                    ragMetrics.incrementRetry("context_check");
                    state.incrementContextRetryCount();
                    state.incrementLoopCount();
                    if (supplementalResponse != null) {
                        state.mergeContext(supplementalResponse.getTextContent());
                    }
                    verdict = contextAgent.check(query, state.getSynthesizedContext());
                }
                state.setContextVerdict(verdict);
                state.addStepRecord(StepRecord.builder()
                    .stepNumber(3).loopNumber(state.getLoopCount()).type("CONTEXT_CHECK")
                    .description("上下文完备性检查: " + (verdict.isSufficient() ? "通过" : "失败"))
                    .durationMs(System.currentTimeMillis() - startTime)
                    .build());
            }

            // ════════════════════════════════════════════════════════
            // 阶段 3: 生成答案草稿
            // ════════════════════════════════════════════════════════
            state.setDraftAnswer(generateDraft(query, state.getSynthesizedContext(), fullChatClient));
            state.addStepRecord(StepRecord.builder()
                .stepNumber(4).loopNumber(state.getLoopCount()).type("GENERATE_DRAFT")
                .description("答案草稿生成完成")
                .durationMs(System.currentTimeMillis() - startTime)
                .build());

            // ════════════════════════════════════════════════════════
            // 阶段 4: Self-Reflection + Corrective Repair
            // ════════════════════════════════════════════════════════
            if (config.getQuality().getSelfReflection().isEnabled()) {
                SelfReflection reflection = new SelfReflection(fastChatClient);
                ReflectionReport report = reflection.reflect(
                    query, null, state.getDraftAnswer(), state.getSynthesizedContext());
                state.setReflectionReport(report);

                if (report.hasIssues() && config.getQuality().getCorrectiveRepair().isEnabled()) {
                    CorrectiveRepair repair = new CorrectiveRepair(fullChatClient, toolRegistry);
                    int repairCount = 0;
                    while (report.hasIssues()
                        && repairCount < config.getAgent().getMaxRepairRetries()) {
                        String repaired = repair.repair(
                            query, state.getDraftAnswer(), report, state.getSynthesizedContext());
                        state.setDraftAnswer(repaired);
                        report = reflection.reflect(
                            query, null, repaired, state.getSynthesizedContext());
                        repairCount++;
                        state.incrementRepairCount();
                    }
                    state.setReflectionReport(report);
                    state.setDraftAnswer(state.getDraftAnswer());
                    state.addStepRecord(StepRecord.builder()
                        .stepNumber(5).loopNumber(state.getLoopCount()).type("SELF_REFLECTION")
                        .description("自反思" + (report.hasIssues() ? "发现" + state.getRepairCount() + "个问题" : "通过"))
                        .durationMs(System.currentTimeMillis() - startTime)
                        .build());
                }
            }

            // ════════════════════════════════════════════════════════
            // 阶段 5: LLM Judge 质量评估
            // ════════════════════════════════════════════════════════
            if (config.getQuality().getLlmJudge().isEnabled()) {
                LlmJudge judge = new LlmJudge(fastChatClient);
                QualityScores scores = judge.evaluate(
                    query, state.getDraftAnswer(), state.getSynthesizedContext());
                state.setQualityScores(scores);

                if (!scores.isPassing(
                    config.getQuality().getLlmJudge().getThresholds().getFaithfulness(),
                    config.getQuality().getLlmJudge().getThresholds().getAnswerRelevancy(),
                    config.getQuality().getLlmJudge().getThresholds().getCitationGrounding())) {

                    log.warn("LLM Judge 质量门禁未通过");
                    state.setQualityGateFailed(true);

                    // 尝试重生成（最多 2 次）
                    int retryCount = 0;
                    int maxRetries = 2;
                    // 答案重生成使用 fullChatClient
                    while (!scores.isPassing(
                        config.getQuality().getLlmJudge().getThresholds().getFaithfulness(),
                        config.getQuality().getLlmJudge().getThresholds().getAnswerRelevancy(),
                        config.getQuality().getLlmJudge().getThresholds().getCitationGrounding())
                        && retryCount < maxRetries) {

                        String regenerated = fullChatClient.prompt()
                            .system("请重新生成答案。上次评分未通过质量门禁。"
                                + "确保答案严格基于检索上下文，使用 [N] 标记引用，直接回答用户问题。")
                            .user("问题: " + query + "\n上下文:\n" + state.getSynthesizedContext())
                            .call()
                            .content();
                        state.setDraftAnswer(regenerated);
                        scores = judge.evaluate(query, regenerated, state.getSynthesizedContext());
                        retryCount++;
                        state.incrementRepairCount();
                    }
                    state.setQualityScores(scores);
                    state.addStepRecord(StepRecord.builder()
                        .stepNumber(6).loopNumber(state.getLoopCount()).type("QUALITY_JUDGE")
                        .description("LLM Judge 质量评估" + (state.isQualityGateFailed() ? "未通过" : "通过"))
                        .durationMs(System.currentTimeMillis() - startTime)
                        .build());
                }
            }

            // ════════════════════════════════════════════════════════
            // 最终输出
            // ════════════════════════════════════════════════════════
            state.setFinalAnswer(state.getDraftAnswer());
            ragMetrics.incrementAgentDecision();
            state.setStatus(AgentStatus.COMPLETED);

        } catch (TimeoutException e) {
            log.warn("Agent 执行超时: query='{}', userId='{}'", truncate(query, 50), userId);
            state.setStatus(AgentStatus.TIMEOUT);
            state.setFinalAnswer(buildFallbackAnswer(state, "处理超时，请简化问题后重试"));
        } catch (Exception e) {
            log.error("Agent 执行异常: query='{}'", truncate(query, 50), e);
            state.setStatus(AgentStatus.FAILED);
            state.setError(e.getMessage());
            state.setFinalAnswer(buildFallbackAnswer(state, "处理过程中出现错误"));
        } finally {
            state.setTotalDurationMs(System.currentTimeMillis() - startTime);
            recorder.record(state);
            // 清理 MDC 上下文
            MDC.remove("trajectoryId");
            MDC.remove("userId");
            MDC.remove("loopCount");
            MDC.remove("agentStatus");
        }

        return state;
    }

    // ════════════════════════════════════════════════════════════
    // 流式执行
    // ════════════════════════════════════════════════════════════

    /**
     * 流式执行 Agent 主循环 — 通过 Consumer 回调逐阶段推送 StreamEvent。
     *
     * <p>与 execute() 逻辑相同，但在每个阶段边界发射事件，
     * 适合 SSE（Server-Sent Events）场景，让客户端实时看到执行进度。
     */
    public synchronized void executeStream(String query, String userId, Consumer<StreamEvent> onEvent) {
        AgentState state = new AgentState(query, userId, config);
        long startTime = System.currentTimeMillis();

        MDC.put("trajectoryId", state.getTrajectoryId());
        MDC.put("userId", userId);

        try {
            // ── 发射: 开始分析 ──
            onEvent.accept(StreamEvent.thinking("正在分析问题..."));

            RuntimeContext ctx = RuntimeContext.builder()
                .userId(userId)
                .sessionId(state.getTrajectoryId())
                .put("query", query)
                .build();

            // ── 发射: HarnessAgent 推理中 ──
            onEvent.accept(StreamEvent.thinking("正在检索知识库..."));

            Msg response = harnessAgent.call(
                Msg.builder().textContent(query).build(), ctx
            ).block(Duration.ofMillis(config.getAgent().getMaxTimeoutMs()));

            if (response == null) {
                throw new TimeoutException("HarnessAgent 调用超时");
            }

            state.setAgentRawResponse(response.getTextContent());
            state.setSynthesizedContext(response.getTextContent());
            onEvent.accept(StreamEvent.toolResult("vector_search", 5));

            // ── 阶段 2: 上下文完备性检查 ──
            if (config.getQuality().getContextCheck().isEnabled()) {
                onEvent.accept(StreamEvent.check("正在检查信息完备性..."));
                SufficientContextAgent contextAgent = new SufficientContextAgent(fastChatClient);
                ContextVerdict verdict = contextAgent.check(query, state.getSynthesizedContext());

                int retryCount = 0;
                while (!verdict.isSufficient()
                    && retryCount < config.getAgent().getMaxContextRetries()) {
                    String sq = verdict.getMissingInfoQuery();
                    onEvent.accept(StreamEvent.thinking("信息不完备，补充检索: " + sq));

                    Msg sr = harnessAgent.call(
                        Msg.builder().textContent("补充检索：" + sq).build(), ctx
                    ).block(Duration.ofMillis(config.getAgent().getSingleToolTimeoutMs()));

                    retryCount++;
                    ragMetrics.incrementRetry("context_check");
                    state.incrementContextRetryCount();
                    state.incrementLoopCount();
                    if (sr != null) state.mergeContext(sr.getTextContent());
                    verdict = contextAgent.check(query, state.getSynthesizedContext());
                }
                state.setContextVerdict(verdict);
                onEvent.accept(StreamEvent.check(
                    verdict.isSufficient() ? "信息完备" : "已尽力但仍不完备"));
            }

            // ── 阶段 3: 流式生成答案 ──
            onEvent.accept(StreamEvent.generating());
            String draft = streamGenerate(query, state.getSynthesizedContext(), onEvent, fullChatClient);
            state.setDraftAnswer(draft);

            // ── 阶段 4: Self-Reflection — 流式场景下简化处理 ──
            if (config.getQuality().getSelfReflection().isEnabled()) {
                onEvent.accept(StreamEvent.check("正在检查答案质量..."));
                SelfReflection reflection = new SelfReflection(fastChatClient);
                ReflectionReport report = reflection.reflect(
                    query, null, draft, state.getSynthesizedContext());
                state.setReflectionReport(report);

                if (report.hasIssues() && config.getQuality().getCorrectiveRepair().isEnabled()) {
                    onEvent.accept(StreamEvent.check("发现问题，正在修复..."));
                    CorrectiveRepair repair = new CorrectiveRepair(fullChatClient, toolRegistry);
                    int rc = 0;
                    while (report.hasIssues() && rc < config.getAgent().getMaxRepairRetries()) {
                        draft = repair.repair(query, draft, report, state.getSynthesizedContext());
                        report = reflection.reflect(query, null, draft, state.getSynthesizedContext());
                        rc++;
                        state.incrementRepairCount();
                    }
                    state.setDraftAnswer(draft);
                    state.setReflectionReport(report);
                }
            }

            // ── 阶段 5: LLM Judge (可选) ──
            if (config.getQuality().getLlmJudge().isEnabled()) {
                LlmJudge judge = new LlmJudge(fastChatClient);
                QualityScores scores = judge.evaluate(query, draft, state.getSynthesizedContext());
                state.setQualityScores(scores);
                if (!scores.isPassing(
                    config.getQuality().getLlmJudge().getThresholds().getFaithfulness(),
                    config.getQuality().getLlmJudge().getThresholds().getAnswerRelevancy(),
                    config.getQuality().getLlmJudge().getThresholds().getCitationGrounding())) {
                    state.setQualityGateFailed(true);
                }
            }

            // ── 最终: 发射答案 ──
            state.setFinalAnswer(state.getDraftAnswer());
            state.setStatus(AgentStatus.COMPLETED);
            onEvent.accept(StreamEvent.done(state.getFinalAnswer()));

        } catch (TimeoutException e) {
            onEvent.accept(StreamEvent.error("处理超时，请简化问题后重试"));
            state.setStatus(AgentStatus.TIMEOUT);
        } catch (Exception e) {
            onEvent.accept(StreamEvent.error("处理出错: " + e.getMessage()));
            state.setStatus(AgentStatus.FAILED);
            state.setError(e.getMessage());
        } finally {
            state.setTotalDurationMs(System.currentTimeMillis() - startTime);
            recorder.record(state);
            MDC.remove("trajectoryId");
            MDC.remove("userId");
            MDC.remove("loopCount");
            MDC.remove("agentStatus");
        }
    }

    /**
     * 流式生成答案（逐 Token 发射，带重试容错）。
     */
    private String streamGenerate(String query, String context, Consumer<StreamEvent> onEvent, ChatClient chatClient) {
        if (context == null || context.isBlank()) {
            String msg = "抱歉，未检索到与问题相关的信息。";
            onEvent.accept(StreamEvent.token(msg));
            return msg;
        }
        try {
            StringBuilder full = new StringBuilder();
            chatClient.prompt()
                .system("基于检索到的上下文信息生成准确、带引用的答案。"
                    + "使用 [N] 标注引用来源。")
                .user("问题: " + query + "\n\n上下文:\n" + context)
                .stream()
                .content()
                .doOnNext(chunk -> {
                    full.append(chunk);
                    onEvent.accept(StreamEvent.token(chunk));
                })
                .blockLast();
            return full.toString();
        } catch (Exception e) {
            log.warn("流式生成异常，回退到非流式: {}", e.getMessage());
            String fallback = generateDraft(query, context, chatClient);
            onEvent.accept(StreamEvent.token(fallback));
            return fallback;
        }
    }

    private String generateDraft(String query, String context, ChatClient chatClient) {
        if (context == null || context.isBlank()) {
            return "抱歉，未检索到与问题相关的信息。";
        }
        return chatClient.prompt()
            .system("基于检索到的上下文信息生成准确、带引用的答案。"
                + "使用 [N] 标注引用来源。如果上下文不足以回答问题，请明确告知。")
            .user("问题: " + query + "\n\n上下文:\n" + context)
            .call()
            .content();
    }

    private String buildFallbackAnswer(AgentState state, String reason) {
        StringBuilder sb = new StringBuilder();
        sb.append("抱歉，").append(reason).append("。\n\n");
        if (state.getSynthesizedContext() != null && !state.getSynthesizedContext().isBlank()) {
            sb.append("以下是我已找到的相关信息：\n").append(state.getSynthesizedContext());
        } else {
            sb.append("（暂无相关信息）");
        }
        return sb.toString();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    // ════════════════════════════════════════════════════════════
    // 工具调用捕获 Middleware
    // ════════════════════════════════════════════════════════════

    /**
     * 中间件 — 捕获 HarnessAgent 执行过程中的工具调用。
     * 通过 RuntimeContext 获取 AgentState 并记录工具名和参数。
     */
    public static class ToolCallCaptureMiddleware implements MiddlewareBase {

        @Override
        public Flux<AgentEvent> onActing(io.agentscope.core.agent.Agent agent,
                                          RuntimeContext ctx,
                                          ActingInput input,
                                          java.util.function.Function<ActingInput, Flux<AgentEvent>> next) {
            AgentState state = ctx.get("agentState");
            if (state != null && input.toolCalls() != null) {
                for (var tc : input.toolCalls()) {
                    state.addToolCall(
                        tc.getName(),
                        tc.getInput() != null ? tc.getInput() : java.util.Map.of(),
                        new ToolResult(true, tc.getContent() != null ? tc.getContent() : "pending", null, 0L)
                    );
                }
            }
            return next.apply(input);
        }
    }

    @PreDestroy
    public void cleanup() {
        if (harnessAgent != null) {
            harnessAgent.close();
        }
    }
}
