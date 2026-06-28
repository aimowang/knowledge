package org.example.api.controller;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.example.core.cache.CacheService;
import org.example.core.evaluation.EvaluationManager;
import org.example.core.memory.ShortTermMemoryManager;
import org.example.core.service.KnowledgeQAService;
import org.example.model.AgenticAskRequest;
import org.example.model.AgenticAskResponse;
import org.example.model.AskRequest;
import org.example.model.RagAnswer;
import org.example.model.RagEvaluation;
import org.example.core.rag.agentic.trajectory.TrajectoryRepository;
import org.example.core.rag.agentic.trajectory.TrajectoryEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 知识库问答控制器（演示带来源的 RAG）
 */
@Slf4j
@RestController
@RequestMapping("/api/qa")
@Tag(name = "RAG 问答", description = "知识库问答相关接口，支持长期记忆和质量评估")
public class KnowledgeQAController {

    private final KnowledgeQAService qaService;
    private final EvaluationManager evaluationManager;
    private final ShortTermMemoryManager shortTermMemoryManager;
    private final CacheService cacheService;
    private final ThreadPoolTaskExecutor ragRetrievalExecutor;
    private final TrajectoryRepository trajectoryRepository;

    public KnowledgeQAController(KnowledgeQAService qaService, EvaluationManager evaluationManager,
                                  ShortTermMemoryManager shortTermMemoryManager, CacheService cacheService,
                                  ThreadPoolTaskExecutor ragRetrievalExecutor,
                                  TrajectoryRepository trajectoryRepository) {
        this.qaService = qaService;
        this.evaluationManager = evaluationManager;
        this.shortTermMemoryManager = shortTermMemoryManager;
        this.cacheService = cacheService;
        this.ragRetrievalExecutor = ragRetrievalExecutor;
        this.trajectoryRepository = trajectoryRepository;
    }

    /**
     * 带记忆的问答（推荐）
     */
    @PostMapping("/ask")
    @Operation(summary = "RAG 问答", description = "基于知识库的智能问答，支持长期记忆和自适应检索策略")
    public RagAnswer askWithMemory(
            @Parameter(description = "问答请求", required = true) @RequestBody AskRequest request) {
        try {
            log.info("收到问答请求 - 用户: {}, 问题: {}, 来源: {}",
                    request.getUserId(),
                    request.getQuestion().substring(0, Math.min(50, request.getQuestion().length())),
                    request.getSource());

            RagAnswer answer = qaService.askInFlowWithSources(
                    request.getUserId(),
                    request.getQuestion(),
                    request.getSource()
            );

            log.info("问答完成 - 用户: {}, 来源数: {}",
                    request.getUserId(),
                    answer.getSources() != null ? answer.getSources().size() : 0);

            return answer;
        } catch (Exception e) {
            log.error("问答失败 - 用户: {}, 问题: {}",
                    request.getUserId(),
                    request.getQuestion(),
                    e);
            // 返回友好错误信息
            return new RagAnswer("抱歉，处理您的问题时出现了错误，请稍后重试。", List.of());
        }
    }

    /**
     * 清空用户会话
     */
    @DeleteMapping("/session/{userId}")
    @Operation(summary = "清空会话", description = "清除指定用户的会话、Redis 缓存和评估历史（前端操作 + MySQL + Redis）")
    public String clearSession(
            @Parameter(description = "用户ID", required = true, example = "user123")
            @PathVariable String userId) {
        // 清除 Redis 短时会话和问答缓存
        shortTermMemoryManager.clearSession(userId);
        cacheService.clearUserCache(userId);
        log.info("用户 {} 的会话和缓存已全部清空", userId);
        return "会话已清空";
    }

    /**
     * 获取用户的评估历史
     */
    @GetMapping("/evaluations/{userId}")
    @Operation(summary = "获取评估历史", description = "查询指定用户的所有 RAG 评估记录")
    public List<RagEvaluation> getUserEvaluations(
            @Parameter(description = "用户ID", required = true) @PathVariable String userId) {
        return evaluationManager.getUserEvaluations(userId);
    }

    /**
     * 获取用户的平均评分
     */
    @GetMapping("/evaluations/{userId}/average")
    @Operation(summary = "获取平均评分", description = "计算指定用户的平均评估分数")
    public double getAverageScore(
            @Parameter(description = "用户ID", required = true) @PathVariable String userId) {
        return evaluationManager.getAverageScore(userId);
    }

    /**
     * 获取全局统计信息
     */
    @GetMapping("/evaluations/statistics")
    @Operation(summary = "全局统计", description = "获取所有用户的评估统计信息（总用户数、总评估数、平均分）")
    public Map<String, Object> getStatistics() {
        return evaluationManager.getStatistics();
    }

    /**
     * 获取低质量评估（用于改进）
     */
    @GetMapping("/evaluations/low-quality")
    @Operation(summary = "低质量评估", description = "获取低于阈值的评估记录，用于优化 RAG 流程")
    public List<RagEvaluation> getLowQualityEvaluations(
            @Parameter(description = "评分阈值，默认 0.6", example = "0.6")
            @RequestParam(defaultValue = "0.6") double threshold) {
        return evaluationManager.getLowQualityEvaluations(threshold);
    }

    // ════════════════════════════════════════════════════════════════
    // Agentic RAG 端点
    // ════════════════════════════════════════════════════════════════

    /**
     * Agentic RAG 问答 — 基于 AgentScope HarnessAgent 的自主推理问答。
     */
    @PostMapping("/ask/agent")
    @Operation(summary = "Agentic RAG 问答", description = "基于 Agent 的自主推理问答，支持多步推理和质量检查")
    public ResponseEntity<AgenticAskResponse> askWithAgent(
            @RequestBody @Valid AgenticAskRequest request) {
        try {
            AgenticAskResponse response = qaService.askWithAgentic(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Agentic 问答失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(
                AgenticAskResponse.builder()
                    .answer("抱歉，Agent 处理失败，请稍后重试")
                    .agenticMode(true)
                    .build()
            );
        }
    }

    /**
     * 查询 Agent 执行轨迹。
     */
    @GetMapping("/trajectory/{trajectoryId}")
    @Operation(summary = "查询 Agent 轨迹", description = "获取 Agent 执行的完整决策路径")
    public ResponseEntity<?> getTrajectory(
            @PathVariable String trajectoryId) {
        var entity = trajectoryRepository.findById(trajectoryId);
        if (entity.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        TrajectoryEntity t = entity.get();
        return ResponseEntity.ok(Map.of(
            "trajectoryId", t.getId(),
            "userId", t.getUserId(),
            "query", t.getQuery(),
            "status", t.getStatus(),
            "totalDurationMs", t.getTotalDurationMs(),
            "totalLoops", t.getTotalLoops(),
            "totalSteps", t.getTotalSteps(),
            "qualityScores", t.getQualityScores(),
            "createdAt", t.getCreatedAt().toString()
        ));
    }

    // ════════════════════════════════════════════════════════════════
    // 流式 SSE 端点
    // ════════════════════════════════════════════════════════════════

    /**
     * Agentic RAG 流式问答（SSE）— 实时推送执行进度和答案 Token。
     *
     * <p>返回 {@code text/event-stream}，事件类型：
     * <ul>
     *   <li>{@code thinking} — Agent 正在思考/检索</li>
     *   <li>{@code tool_call} — 调用工具</li>
     *   <li>{@code check} — 质量检查</li>
     *   <li>{@code generating} — 开始生成</li>
     *   <li>{@code token} — 答案 Token（逐个推送）</li>
     *   <li>{@code done} — 执行完成（含完整答案）</li>
     *   <li>{@code error} — 执行出错</li>
     * </ul>
     */
    @PostMapping("/ask/agent/stream")
    @Operation(summary = "Agentic RAG 流式问答（SSE）",
               description = "基于 Agent 的自主推理问答，通过 SSE 实时推送执行进度和答案 Token")
    public SseEmitter askWithAgentStream(@RequestBody @Valid AgenticAskRequest request) {
        // 校验
        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            SseEmitter emitter = new SseEmitter(0L);
            try {
                emitter.send(SseEmitter.event().name("error").data("question must not be blank"));
            } catch (Exception ignored) {}
            emitter.complete();
            return emitter;
        }

        // 60 秒超时，连接断开时自动取消
        SseEmitter emitter = new SseEmitter(60_000L);
        emitter.onCompletion(() -> log.debug("SSE 连接完成"));
        emitter.onTimeout(() -> log.debug("SSE 连接超时"));

        // 异步执行（使用 ragRetrievalExecutor 线程池）
        SecurityContext securityContext = SecurityContextHolder.getContext();
        ragRetrievalExecutor.submit(() -> {
            // 将 HTTP 线程的 SecurityContext 传播到异步线程
            SecurityContextHolder.setContext(securityContext);
            try {
                qaService.askWithAgentStream(request, event -> {
                    try {
                        emitter.send(SseEmitter.event()
                            .name(event.getType())
                            .data(event.getContent()));
                    } catch (java.io.IOException e) {
                        // 客户端断开连接，停止执行
                        log.debug("SSE 客户端断开，停止流式输出");
                        throw new RuntimeException(e);
                    }
                });
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data("流式处理失败，请稍后重试"));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            } finally {
                SecurityContextHolder.clearContext();
            }
        });

        return emitter;
    }
}
