package org.example.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.example.core.cache.CacheService;
import org.example.core.evaluation.EvaluationManager;
import org.example.core.memory.ShortTermMemoryManager;
import org.example.core.service.KnowledgeQAService;
import org.example.model.AskRequest;
import org.example.model.RagAnswer;
import org.example.model.RagEvaluation;
import org.springframework.web.bind.annotation.*;

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

    public KnowledgeQAController(KnowledgeQAService qaService, EvaluationManager evaluationManager,
                                  ShortTermMemoryManager shortTermMemoryManager, CacheService cacheService) {
        this.qaService = qaService;
        this.evaluationManager = evaluationManager;
        this.shortTermMemoryManager = shortTermMemoryManager;
        this.cacheService = cacheService;
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
        // 1. 清除 MySQL 评估历史
//        evaluationManager.clearUserEvaluations(userId);
        // 2. 清除 Redis 短时会话
        shortTermMemoryManager.clearSession(userId);
        // 3. 清除 Redis 问答缓存
        cacheService.clearUserCache(userId);
        log.info("用户 {} 的会话、缓存和评估已全部清空", userId);
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
}
