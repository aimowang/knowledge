package org.example.core.evaluation;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.core.repository.RagEvaluationRepository;
import org.example.model.RagEvaluation;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 评估结果管理器（基于 MySQL）
 */
@Slf4j
@Component
public class EvaluationManager {
    
    private final RagEvaluationRepository repository;
    
    public EvaluationManager(RagEvaluationRepository repository) {
        this.repository = repository;
    }
    
    @PostConstruct
    public void init() {
        log.info("RAG 评估管理器初始化完成，使用 MySQL 持久化");
    }
    
    /**
     * 保存评估结果
     */
    public void saveEvaluation(RagEvaluation evaluation) {
        org.example.model.entity.RagEvaluationEntity entity = new org.example.model.entity.RagEvaluationEntity();
        entity.setId(evaluation.getId());
        entity.setUserId(evaluation.getUserId());
        entity.setQuestion(evaluation.getQuestion());
        entity.setAnswer(evaluation.getAnswer());
        entity.setContext(evaluation.getContext());
        entity.setGroundTruth(evaluation.getGroundTruth());
        entity.setAnswerRelevance(evaluation.getAnswerRelevance());
        entity.setFaithfulness(evaluation.getFaithfulness());
        entity.setContextRelevance(evaluation.getContextRelevance());
        entity.setContextPrecision(evaluation.getContextPrecision());
        entity.setContextRecall(evaluation.getContextRecall());
        entity.setOverallScore(evaluation.getOverallScore());
        
        repository.save(entity);
        log.info("保存评估结果 - 用户: {}, 综合评分: {:.2f}", 
            evaluation.getUserId(), evaluation.getOverallScore());
    }
    
    /**
     * 获取用户的评估历史
     */
    public List<RagEvaluation> getUserEvaluations(String userId) {
        return repository.findByUserId(userId).stream()
                .map(this::convertToModel)
                .collect(Collectors.toList());
    }
    
    private RagEvaluation convertToModel(org.example.model.entity.RagEvaluationEntity entity) {
        RagEvaluation eval = new RagEvaluation();
        eval.setId(entity.getId());
        eval.setUserId(entity.getUserId());
        eval.setQuestion(entity.getQuestion());
        eval.setAnswer(entity.getAnswer());
        eval.setContext(entity.getContext());
        eval.setGroundTruth(entity.getGroundTruth());
        eval.setAnswerRelevance(entity.getAnswerRelevance());
        eval.setFaithfulness(entity.getFaithfulness());
        eval.setContextRelevance(entity.getContextRelevance());
        eval.setContextPrecision(entity.getContextPrecision());
        eval.setContextRecall(entity.getContextRecall());
        eval.setOverallScore(entity.getOverallScore());
        eval.setEvaluatedAt(entity.getEvaluatedAt());
        return eval;
    }
    
    /**
     * 获取用户的平均评分
     */
    public double getAverageScore(String userId) {
        List<RagEvaluation> evaluations = getUserEvaluations(userId);
        
        if (evaluations.isEmpty()) {
            return 0.0;
        }
        
        return evaluations.stream()
                .mapToDouble(e -> e.getOverallScore() != null ? e.getOverallScore() : 0.0)
                .average()
                .orElse(0.0);
    }
    
    /**
     * 获取所有用户的统计信息（优化版：使用 SQL 聚合）
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            Object[] result = repository.getGlobalStatistics();

            // JPA 聚合查询可能返回嵌套 Object[]，自动解一层
            if (result != null && result.length == 1 && result[0] instanceof Object[]) {
                result = (Object[]) result[0];
            }

            long totalUsers = result != null && result.length > 0 && result[0] != null
                    ? ((Number) result[0]).longValue() : 0;
            long totalEvaluations = result != null && result.length > 1 && result[1] != null
                    ? ((Number) result[1]).longValue() : 0;
            double globalAverage = result != null && result.length > 2 && result[2] != null
                    ? ((Number) result[2]).doubleValue() : 0.0;
            
            stats.put("totalUsers", totalUsers);
            stats.put("totalEvaluations", totalEvaluations);
            stats.put("globalAverageScore", Math.round(globalAverage * 100.0) / 100.0);
        } catch (Exception e) {
            log.error("获取统计信息失败", e);
            stats.put("totalUsers", 0);
            stats.put("totalEvaluations", 0);
            stats.put("globalAverageScore", 0.0);
        }
        
        return stats;
    }
    
    /**
     * 获取低质量评估（用于改进）
     */
    public List<RagEvaluation> getLowQualityEvaluations(double threshold) {
        return repository.findAll().stream()
                .map(this::convertToModel)
                .filter(e -> e.getOverallScore() != null && e.getOverallScore() < threshold)
                .sorted((a, b) -> Double.compare(
                    a.getOverallScore(), 
                    b.getOverallScore()
                ))
                .limit(20)
                .collect(Collectors.toList());
    }
    
    /**
     * 清空用户的评估历史
     */
    public void clearUserEvaluations(String userId) {
        repository.deleteByUserId(userId);
        log.info("清空用户 {} 的评估历史", userId);
    }
}
