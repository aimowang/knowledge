package org.example.core.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.model.RagEvaluation;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * RAG 评估结果管理器
 */
@Slf4j
@Component
public class EvaluationManager {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 用户ID -> 评估结果列表
     */
    private final Map<String, List<RagEvaluation>> userEvaluations = new ConcurrentHashMap<>();
    
    /**
     * 评估结果存储路径
     */
    private static final String EVALUATION_DIR = "data/rag-evaluations";
    
    @PostConstruct
    public void init() {
        try {
            Path dir = Paths.get(EVALUATION_DIR);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
                log.info("创建 RAG 评估结果目录: {}", EVALUATION_DIR);
            }
            loadAllEvaluations();
        } catch (IOException e) {
            log.error("初始化评估结果失败", e);
        }
    }
    
    /**
     * 保存评估结果
     */
    public void saveEvaluation(RagEvaluation evaluation) {
        userEvaluations.computeIfAbsent(evaluation.getUserId(), k -> new ArrayList<>())
                      .add(evaluation);
        
        saveUserEvaluations(evaluation.getUserId());
        log.info("保存评估结果 - 用户: {}, 综合评分: {:.2f}", 
            evaluation.getUserId(), evaluation.getOverallScore());
    }
    
    /**
     * 获取用户的评估历史
     */
    public List<RagEvaluation> getUserEvaluations(String userId) {
        return List.copyOf(userEvaluations.getOrDefault(userId, List.of()));
    }
    
    /**
     * 获取用户的平均评分
     */
    public double getAverageScore(String userId) {
        List<RagEvaluation> evaluations = userEvaluations.getOrDefault(userId, List.of());
        
        if (evaluations.isEmpty()) {
            return 0.0;
        }
        
        return evaluations.stream()
                .mapToDouble(e -> e.getOverallScore() != null ? e.getOverallScore() : 0.0)
                .average()
                .orElse(0.0);
    }
    
    /**
     * 获取所有用户的统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        int totalUsers = userEvaluations.size();
        int totalEvaluations = userEvaluations.values().stream()
                .mapToInt(List::size)
                .sum();
        
        double globalAverage = userEvaluations.values().stream()
                .flatMap(List::stream)
                .mapToDouble(e -> e.getOverallScore() != null ? e.getOverallScore() : 0.0)
                .average()
                .orElse(0.0);
        
        stats.put("totalUsers", totalUsers);
        stats.put("totalEvaluations", totalEvaluations);
        stats.put("globalAverageScore", Math.round(globalAverage * 100.0) / 100.0);
        
        return stats;
    }
    
    /**
     * 获取低质量评估（用于改进）
     */
    public List<RagEvaluation> getLowQualityEvaluations(double threshold) {
        return userEvaluations.values().stream()
                .flatMap(List::stream)
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
        userEvaluations.remove(userId);
        
        try {
            Path file = Paths.get(EVALUATION_DIR, userId + ".json");
            if (Files.exists(file)) {
                Files.delete(file);
            }
            log.info("清空用户 {} 的评估历史", userId);
        } catch (IOException e) {
            log.error("删除评估文件失败", e);
        }
    }
    
    /**
     * 从文件加载所有评估结果
     */
    private void loadAllEvaluations() {
        try {
            Path dir = Paths.get(EVALUATION_DIR);
            if (!Files.exists(dir)) {
                return;
            }
            
            Files.list(dir)
                .filter(p -> p.toString().endsWith(".json"))
                .forEach(this::loadUserEvaluations);
            
            log.info("加载评估结果完成，共 {} 个用户", userEvaluations.size());
        } catch (IOException e) {
            log.error("加载评估结果失败", e);
        }
    }
    
    /**
     * 加载单个用户的评估结果
     */
    private void loadUserEvaluations(Path filePath) {
        try {
            String fileName = filePath.getFileName().toString();
            String userId = fileName.replace(".json", "");
            
            String json = Files.readString(filePath);
            List<RagEvaluation> evaluations = objectMapper.readValue(
                json, 
                new TypeReference<List<RagEvaluation>>() {}
            );
            
            userEvaluations.put(userId, evaluations);
            log.debug("加载用户 {} 的 {} 条评估结果", userId, evaluations.size());
        } catch (IOException e) {
            log.error("加载用户评估结果失败: {}", filePath, e);
        }
    }
    
    /**
     * 保存用户评估结果到文件
     */
    private void saveUserEvaluations(String userId) {
        try {
            Path dir = Paths.get(EVALUATION_DIR);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            
            Path file = dir.resolve(userId + ".json");
            List<RagEvaluation> evaluations = userEvaluations.get(userId);
            
            if (evaluations != null && !evaluations.isEmpty()) {
                String json = objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(evaluations);
                Files.writeString(file, json);
            }
        } catch (IOException e) {
            log.error("保存用户评估结果失败: {}", userId, e);
        }
    }
}
