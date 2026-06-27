package org.example.core.rag.agentic.agent;

import io.agentscope.core.agent.RuntimeContext;

import org.slf4j.MDC;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.example.core.rag.agentic.quality.ContextVerdict;
import org.example.core.rag.agentic.quality.CorrectiveRepair;
import org.example.core.rag.agentic.quality.ReflectionReport;
import org.example.core.rag.agentic.quality.SelfReflection;
import org.example.core.rag.agentic.quality.SufficientContextAgent;
import org.example.core.rag.agentic.tool.ToolRegistry;
import org.example.core.rag.agentic.trajectory.TrajectoryRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

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
    private final ChatClient chatClient;

    private HarnessAgent harnessAgent;

    public AgentOrchestrator(ToolRegistry toolRegistry,
                             AgentConfig config,
                             TrajectoryRecorder recorder,
                             ChatClient chatClient) {
        this.toolRegistry = toolRegistry;
        this.config = config;
        this.recorder = recorder;
        this.chatClient = chatClient;
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
        return HarnessAgent.builder()
            .name(config.getAgent().getName())
            .workspace(java.nio.file.Paths.get(config.getWorkspace().getPath()))
            .model(DashScopeChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName(config.getAgent().getModel())
                .stream(config.getAgent().isEnableStreaming())
                .build())
            .maxIters(config.getAgent().getMaxLoops())
            .hooks(List.of(
                new QualityCheckHook(),
                new MetricsCollectHook()
            ))
            .build();
    }

    /**
     * 执行 Agent 主循环。
     */
    public AgentState execute(String query, String userId) {
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
                .build();

            Msg response = harnessAgent.call(
                Msg.builder()
                    .textContent(query)
                    .build(),
                ctx
            ).block(Duration.ofMillis(config.getAgent().getMaxTimeoutMs()));

            if (response == null) {
                throw new TimeoutException("HarnessAgent 调用超时");
            }

            state.setAgentRawResponse(response.getTextContent());
            state.setSynthesizedContext(response.getTextContent());

            // ════════════════════════════════════════════════════════
            // 阶段 2: SufficientContextAgent 完备性检查
            // ════════════════════════════════════════════════════════
            if (config.getQuality().getContextCheck().isEnabled()) {
                SufficientContextAgent contextAgent = new SufficientContextAgent(chatClient);
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
                    state.incrementContextRetryCount();
                    state.incrementLoopCount();
                    if (supplementalResponse != null) {
                        state.mergeContext(supplementalResponse.getTextContent());
                    }
                    verdict = contextAgent.check(query, state.getSynthesizedContext());
                }
                state.setContextVerdict(verdict);
            }

            // ════════════════════════════════════════════════════════
            // 阶段 3: 生成答案草稿
            // ════════════════════════════════════════════════════════
            state.setDraftAnswer(generateDraft(query, state.getSynthesizedContext()));

            // ════════════════════════════════════════════════════════
            // 阶段 4: Self-Reflection + Corrective Repair
            // ════════════════════════════════════════════════════════
            if (config.getQuality().isSelfReflection()) {
                SelfReflection reflection = new SelfReflection(chatClient);
                ReflectionReport report = reflection.reflect(
                    query, null, state.getDraftAnswer(), state.getSynthesizedContext());
                state.setReflectionReport(report);

                if (report.hasIssues() && config.getQuality().isCorrectiveRepair()) {
                    CorrectiveRepair repair = new CorrectiveRepair(chatClient, toolRegistry);
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
                }
            }

            // ════════════════════════════════════════════════════════
            // 阶段 5: LLM Judge (Phase 3)
            // ════════════════════════════════════════════════════════
            if (config.getQuality().getLlmJudge().isEnabled()) {
                log.debug("LLM Judge 将在 Phase 3 中完整实现");
            }

            // ════════════════════════════════════════════════════════
            // 最终输出
            // ════════════════════════════════════════════════════════
            state.setFinalAnswer(state.getDraftAnswer());
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

    private String generateDraft(String query, String context) {
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
    // 自定义 HarnessAgent Hook
    // ════════════════════════════════════════════════════════════

    public static class QualityCheckHook implements Hook {
        private static final Logger log = LoggerFactory.getLogger(QualityCheckHook.class);

        @Override
        public <T extends io.agentscope.core.hook.HookEvent> Mono<T> onEvent(T event) {
            log.debug("QualityCheckHook 处理事件: {}", event.getClass().getSimpleName());
            return Mono.just(event);
        }

        @Override
        public int priority() {
            return 200;
        }
    }

    public static class MetricsCollectHook implements Hook {
        private static final Logger log = LoggerFactory.getLogger(MetricsCollectHook.class);

        @Override
        public <T extends io.agentscope.core.hook.HookEvent> Mono<T> onEvent(T event) {
            log.debug("MetricsCollectHook: {}", event.getClass().getSimpleName());
            return Mono.just(event);
        }

        @Override
        public int priority() {
            return 100;
        }
    }

    @PreDestroy
    public void cleanup() {
        if (harnessAgent != null) {
            harnessAgent.close();
        }
    }
}
