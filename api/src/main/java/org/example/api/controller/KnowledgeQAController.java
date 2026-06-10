package org.example.api.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.core.evaluation.EvaluationManager;
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
public class KnowledgeQAController {

    private final KnowledgeQAService qaService;
    private final EvaluationManager evaluationManager;

    public KnowledgeQAController(KnowledgeQAService qaService, EvaluationManager evaluationManager) {
        this.qaService = qaService;
        this.evaluationManager = evaluationManager;
    }

    /**
     * 带记忆的问答（推荐）
     * 用户请求 (POST /api/qa/ask)
     * ↓
     * Controller (异常处理 + 日志)
     * ↓
     * KnowledgeQAService
     * ├─ classifyQuestion() [LLM 分类]
     * └─ selectRagFlow() [选择 RAG 实现]
     * ↓
     * AbstractBasicRag.executeRag()
     * ├─ loadMemoryContext() [加载记忆]
     * ├─ classifyComplexity() [复杂度分类]
     * ├─ handleSimple/Moderate/Complex() [执行策略]
     * │   └─ executeRagFlow()
     * │       ├─ enhanceQueryWithMemory() [Query 增强]
     * │       ├─ retriever.retrieve(query, source) [向量检索 + 过滤]
     * │       ├─ postProcessDocuments() [重排序 + 压缩]
     * │       └─ generateAnswerWithContext() [生成答案]
     * ├─ saveToShortTermMemory() [保存短期记忆]
     * └─ triggerEvaluation() [质量评估]
     * ↓
     * 返回 RagAnswer
     */
    @PostMapping("/ask")
    public RagAnswer askWithMemory(@RequestBody AskRequest request) {
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
    public String clearSession(@PathVariable String userId) {
        evaluationManager.clearUserEvaluations(userId);
        return "会话已清空";
    }

    /**
     * 获取用户的评估历史
     */
    @GetMapping("/evaluations/{userId}")
    public List<RagEvaluation> getUserEvaluations(@PathVariable String userId) {
        return evaluationManager.getUserEvaluations(userId);
    }

    /**
     * 获取用户的平均评分
     */
    @GetMapping("/evaluations/{userId}/average")
    public double getAverageScore(@PathVariable String userId) {
        return evaluationManager.getAverageScore(userId);
    }

    /**
     * 获取全局统计信息
     */
    @GetMapping("/evaluations/statistics")
    public Map<String, Object> getStatistics() {
        return evaluationManager.getStatistics();
    }

    /**
     * 获取低质量评估（用于改进）
     */
    @GetMapping("/evaluations/low-quality")
    public List<RagEvaluation> getLowQualityEvaluations(
            @RequestParam(defaultValue = "0.6") double threshold) {
        return evaluationManager.getLowQualityEvaluations(threshold);
    }
}
