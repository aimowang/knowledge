package org.example.core.evaluation;

import lombok.extern.slf4j.Slf4j;
import org.example.model.RagEvaluation;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 质量监控服务
 * 定期分析评估结果，提供改进建议
 */
@Slf4j
@Component
public class QualityMonitor {
    
    private final EvaluationManager evaluationManager;
    
    public QualityMonitor(EvaluationManager evaluationManager) {
        this.evaluationManager = evaluationManager;
    }
    
    /**
     * 每天凌晨3点执行质量分析
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void dailyQualityAnalysis() {
        log.info("开始每日 RAG 质量分析...");
        
        Map<String, Object> stats = evaluationManager.getStatistics();
        log.info("全局统计: {}", stats);
        
        // 分析低质量评估
        List<RagEvaluation> lowQuality = evaluationManager.getLowQualityEvaluations(0.6);
        
        if (!lowQuality.isEmpty()) {
            log.warn("发现 {} 条低质量评估", lowQuality.size());
            analyzeLowQualityIssues(lowQuality);
        } else {
            log.info("未发现低质量评估，系统运行良好");
        }
        
        log.info("每日质量分析完成");
    }
    
    /**
     * 分析低质量问题的原因
     */
    private void analyzeLowQualityIssues(List<RagEvaluation> evaluations) {
        Map<String, Integer> issueCounts = new HashMap<>();
        
        for (RagEvaluation eval : evaluations) {
            // 分析各个指标
            if (eval.getAnswerRelevance() != null && eval.getAnswerRelevance() < 0.6) {
                issueCounts.merge("答案相关性低", 1, Integer::sum);
            }
            if (eval.getFaithfulness() != null && eval.getFaithfulness() < 0.6) {
                issueCounts.merge("忠实度低（可能有幻觉）", 1, Integer::sum);
            }
            if (eval.getContextRelevance() != null && eval.getContextRelevance() < 0.6) {
                issueCounts.merge("上下文相关性低", 1, Integer::sum);
            }
            if (eval.getContextPrecision() != null && eval.getContextPrecision() < 0.6) {
                issueCounts.merge("上下文精确率低", 1, Integer::sum);
            }
            if (eval.getContextRecall() != null && eval.getContextRecall() < 0.6) {
                issueCounts.merge("上下文召回率低", 1, Integer::sum);
            }
        }
        
        // 输出改进建议
        log.info("===== RAG 质量改进建议 =====");
        issueCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> {
                    String issue = entry.getKey();
                    int count = entry.getValue();
                    String suggestion = getSuggestion(issue);
                    log.warn("- {} ({} 次): {}", issue, count, suggestion);
                });
        log.info("===========================");
    }
    
    /**
     * 根据问题类型提供改进建议
     */
    private String getSuggestion(String issue) {
        return switch (issue) {
            case "答案相关性低" -> 
                "优化 Prompt，要求 LLM 更直接地回答问题";
            case "忠实度低（可能有幻觉）" -> 
                "加强上下文约束，在 Prompt 中强调只使用提供的资料";
            case "上下文相关性低" -> 
                "改进检索策略，使用查询扩展或重写";
            case "上下文精确率低" -> 
                "减少检索文档数量，提高重排序阈值";
            case "上下文召回率低" -> 
                "增加检索文档数量，或使用多查询检索";
            default -> 
                "需要进一步分析";
        };
    }
    
    /**
     * 获取用户质量报告
     */
    public Map<String, Object> getUserQualityReport(String userId) {
        List<RagEvaluation> evaluations = evaluationManager.getUserEvaluations(userId);
        
        if (evaluations.isEmpty()) {
            return Map.of("message", "该用户暂无评估数据");
        }
        
        Map<String, Object> report = new HashMap<>();
        report.put("userId", userId);
        report.put("totalEvaluations", evaluations.size());
        report.put("averageScore", evaluationManager.getAverageScore(userId));
        
        // 计算各指标的平均值
        double avgRelevance = evaluations.stream()
                .mapToDouble(e -> e.getAnswerRelevance() != null ? e.getAnswerRelevance() : 0)
                .average().orElse(0);
        
        double avgFaithfulness = evaluations.stream()
                .mapToDouble(e -> e.getFaithfulness() != null ? e.getFaithfulness() : 0)
                .average().orElse(0);
        
        double avgContextRelevance = evaluations.stream()
                .mapToDouble(e -> e.getContextRelevance() != null ? e.getContextRelevance() : 0)
                .average().orElse(0);
        
        report.put("averageAnswerRelevance", Math.round(avgRelevance * 100.0) / 100.0);
        report.put("averageFaithfulness", Math.round(avgFaithfulness * 100.0) / 100.0);
        report.put("averageContextRelevance", Math.round(avgContextRelevance * 100.0) / 100.0);
        
        // 识别主要问题
        List<String> issues = identifyUserIssues(evaluations);
        report.put("mainIssues", issues);
        
        return report;
    }
    
    /**
     * 识别用户的主要问题
     */
    private List<String> identifyUserIssues(List<RagEvaluation> evaluations) {
        List<String> issues = new ArrayList<>();
        
        double avgRelevance = evaluations.stream()
                .mapToDouble(e -> e.getAnswerRelevance() != null ? e.getAnswerRelevance() : 0)
                .average().orElse(0);
        
        if (avgRelevance < 0.7) {
            issues.add("答案相关性需要改进");
        }
        
        double avgFaithfulness = evaluations.stream()
                .mapToDouble(e -> e.getFaithfulness() != null ? e.getFaithfulness() : 0)
                .average().orElse(0);
        
        if (avgFaithfulness < 0.7) {
            issues.add("存在幻觉问题，需要加强约束");
        }
        
        return issues;
    }
}
