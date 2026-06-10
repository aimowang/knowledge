package org.example.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * RAG 质量评估结果
 */
@Data
public class RagEvaluation {
    
    /**
     * 评估ID
     */
    private String id;
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 问题
     */
    private String question;
    
    /**
     * 生成的答案
     */
    private String answer;
    
    /**
     * 检索到的上下文
     */
    private String context;
    
    /**
     * 预期答案（可选，用于有监督评估）
     */
    private String groundTruth;
    
    // ===== Ragas 核心指标 =====
    
    /**
     * 答案相关性 (0-1)
     * 评估答案是否与问题相关
     */
    private Double answerRelevance;
    
    /**
     * 忠实度 (0-1)
     * 评估答案是否基于提供的上下文，没有幻觉
     */
    private Double faithfulness;
    
    /**
     * 上下文相关性 (0-1)
     * 评估检索的上下文是否与问题相关
     */
    private Double contextRelevance;
    
    /**
     * 上下文精确率 (0-1)
     * 评估上下文中有多少比例的信息被用于生成答案
     */
    private Double contextPrecision;
    
    /**
     * 上下文召回率 (0-1)
     * 评估是否检索到了所有必要的信息
     */
    private Double contextRecall;
    
    /**
     * 综合评分 (0-1)
     */
    private Double overallScore;
    
    /**
     * 评估时间
     */
    private LocalDateTime evaluatedAt;
    
    public RagEvaluation() {
        this.evaluatedAt = LocalDateTime.now();
    }
    
    /**
     * 计算综合评分
     */
    public void calculateOverallScore() {
        double sum = 0.0;
        int count = 0;
        
        if (answerRelevance != null) {
            sum += answerRelevance;
            count++;
        }
        if (faithfulness != null) {
            sum += faithfulness;
            count++;
        }
        if (contextRelevance != null) {
            sum += contextRelevance;
            count++;
        }
        if (contextPrecision != null) {
            sum += contextPrecision;
            count++;
        }
        if (contextRecall != null) {
            sum += contextRecall;
            count++;
        }
        
        this.overallScore = count > 0 ? sum / count : 0.0;
    }
    
    /**
     * 判断评估是否通过
     */
    public boolean isPassed(double threshold) {
        return overallScore != null && overallScore >= threshold;
    }
}
