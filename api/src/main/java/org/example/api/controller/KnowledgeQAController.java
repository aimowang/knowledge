package org.example.api.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.model.RagAnswer;
import org.example.model.RagEvaluation;
import org.example.core.evaluation.EvaluationManager;
import org.example.core.service.KnowledgeQAService;
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
     * 普通问答（不带来源）
     */
    @GetMapping("/ask")
    public String ask(@RequestParam String question, 
                     @RequestParam(defaultValue = "default_user") String userId) {
        return qaService.askInFlowWithMemory(userId, question);
    }
    
    /**
     * 带记忆的问答（推荐）
     */
    @PostMapping("/ask-with-memory")
    public String askWithMemory(@RequestBody AskRequest request) {
        return qaService.askInFlowWithMemory(request.getUserId(), request.getQuestion());
    }
    
    /**
     * 清空用户会话
     */
    @DeleteMapping("/session/{userId}")
    public String clearSession(@PathVariable String userId) {
        // TODO: 注入 memoryManager 并调用 clearSession
        return "会话已清空";
    }
    
    /**
     * 请求对象
     */
    @lombok.Data
    public static class AskRequest {
        private String userId;
        private String question;
    }
    
    /**
     * 带来源的问答（推荐）
     * 返回格式化的答案，包含参考资料
     */
    @GetMapping("/ask-with-sources")
    public String askWithSources(@RequestParam String question) {
        RagAnswer result = qaService.askInFlowWithSources(question);
        
        // 返回带引用格式的答案
        return result.formatWithCitations();
    }
    
    /**
     * 结构化答案（JSON 格式）
     * 适合前端自定义展示
     */
    @GetMapping("/ask-structured")
    public RagAnswer askStructured(@RequestParam String question) {
        return qaService.askInFlowWithSources(question);
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
