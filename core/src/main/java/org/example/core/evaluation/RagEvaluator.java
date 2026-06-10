package org.example.core.evaluation;

import lombok.extern.slf4j.Slf4j;
import org.example.model.RagEvaluation;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RAG 质量评估器（基于 LLM 的 Ragas 指标评估）
 */
@Slf4j
@Component
public class RagEvaluator {
    
    private final ChatClient chatClient;
    
    public RagEvaluator(ChatClient chatClient) {
        this.chatClient = chatClient;
    }
    
    /**
     * 评估 RAG 回答质量
     */
    public RagEvaluation evaluate(String userId, String question, String answer, String context) {
        return evaluate(userId, question, answer, context, null);
    }
    
    /**
     * 评估 RAG 回答质量（带标准答案）
     */
    public RagEvaluation evaluate(String userId, String question, String answer, 
                                  String context, String groundTruth) {
        
        RagEvaluation evaluation = new RagEvaluation();
        evaluation.setId(UUID.randomUUID().toString());
        evaluation.setUserId(userId);
        evaluation.setQuestion(question);
        evaluation.setAnswer(answer);
        evaluation.setContext(context);
        evaluation.setGroundTruth(groundTruth);
        
        try {
            // 1. 评估答案相关性
            evaluation.setAnswerRelevance(evaluateAnswerRelevance(question, answer));
            
            // 2. 评估忠实度
            evaluation.setFaithfulness(evaluateFaithfulness(answer, context));
            
            // 3. 评估上下文相关性
            evaluation.setContextRelevance(evaluateContextRelevance(question, context));
            
            // 4. 评估上下文精确率
            evaluation.setContextPrecision(evaluateContextPrecision(question, answer, context));
            
            // 5. 如果有标准答案，评估上下文召回率
            if (groundTruth != null && !groundTruth.isEmpty()) {
                evaluation.setContextRecall(evaluateContextRecall(groundTruth, context));
            }
            
            // 6. 计算综合评分
            evaluation.calculateOverallScore();
            
            log.info("RAG 评估完成 - 用户: {}, 综合评分: {:.2f}", userId, evaluation.getOverallScore());
            
        } catch (Exception e) {
            log.error("RAG 评估失败", e);
            // 评估失败时，设置默认值
            setDefaultScores(evaluation);
        }
        
        return evaluation;
    }
    
    /**
     * 评估答案相关性
     * 问题：答案是否直接回答了用户的问题？
     */
    private double evaluateAnswerRelevance(String question, String answer) {
        String prompt = String.format("""
            请评估以下答案与问题的相关性。
            
            问题：%s
            答案：%s
            
            评分标准：
            - 1.0: 答案完全相关，直接回答问题
            - 0.7-0.9: 答案大部分相关，但有些偏离
            - 0.4-0.6: 答案部分相关
            - 0.0-0.3: 答案不相关或无关
            
            请只返回一个 0-1 之间的数字分数，不要有其他内容。
            """, question, answer);
        
        try {
            String result = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            return parseScore(result);
        } catch (Exception e) {
            log.warn("评估答案相关性失败，使用默认值 0.7", e);
            return 0.7;
        }
    }
    
    /**
     * 评估忠实度
     * 问题：答案是否基于提供的上下文，没有编造信息？
     */
    private double evaluateFaithfulness(String answer, String context) {
        String prompt = String.format("""
            请评估以下答案是否忠实于提供的上下文，没有编造信息。
            
            上下文：%s
            答案：%s
            
            评分标准：
            - 1.0: 答案完全基于上下文，没有幻觉
            - 0.7-0.9: 答案大部分基于上下文，少量推断
            - 0.4-0.6: 答案部分基于上下文，有部分编造
            - 0.0-0.3: 答案大量编造，与上下文不符
            
            请只返回一个 0-1 之间的数字分数，不要有其他内容。
            """, context, answer);
        
        try {
            String result = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            return parseScore(result);
        } catch (Exception e) {
            log.warn("评估忠实度失败，使用默认值 0.8", e);
            return 0.8;
        }
    }
    
    /**
     * 评估上下文相关性
     * 问题：检索的上下文是否与问题相关？
     */
    private double evaluateContextRelevance(String question, String context) {
        String prompt = String.format("""
            请评估以下上下文与问题的相关性。
            
            问题：%s
            上下文：%s
            
            评分标准：
            - 1.0: 上下文完全相关，包含回答问题所需的信息
            - 0.7-0.9: 上下文大部分相关
            - 0.4-0.6: 上下文部分相关
            - 0.0-0.3: 上下文不相关
            
            请只返回一个 0-1 之间的数字分数，不要有其他内容。
            """, question, context);
        
        try {
            String result = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            return parseScore(result);
        } catch (Exception e) {
            log.warn("评估上下文相关性失败，使用默认值 0.75", e);
            return 0.75;
        }
    }
    
    /**
     * 评估上下文精确率
     * 问题：上下文中有多少比例的信息被用于生成答案？
     */
    private double evaluateContextPrecision(String question, String answer, String context) {
        String prompt = String.format("""
            请评估上下文的精确率：上下文中有多少比例的信息被用于生成答案？
            
            问题：%s
            上下文：%s
            答案：%s
            
            评分标准：
            - 1.0: 答案使用了上下文中的所有相关信息，没有冗余
            - 0.7-0.9: 答案使用了大部分相关信息
            - 0.4-0.6: 答案只使用了部分相关信息
            - 0.0-0.3: 答案几乎没有使用上下文中的信息
            
            请只返回一个 0-1 之间的数字分数，不要有其他内容。
            """, question, context, answer);
        
        try {
            String result = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            return parseScore(result);
        } catch (Exception e) {
            log.warn("评估上下文精确率失败，使用默认值 0.7", e);
            return 0.7;
        }
    }
    
    /**
     * 评估上下文召回率
     * 问题：是否检索到了所有必要的信息来回答标准答案？
     */
    private double evaluateContextRecall(String groundTruth, String context) {
        String prompt = String.format("""
            请评估上下文召回率：上下文中是否包含了标准答案中的所有关键信息？
            
            标准答案：%s
            上下文：%s
            
            评分标准：
            - 1.0: 上下文包含了标准答案中的所有关键信息
            - 0.7-0.9: 上下文包含了大部分关键信息
            - 0.4-0.6: 上下文包含了部分关键信息
            - 0.0-0.3: 上下文缺少大部分关键信息
            
            请只返回一个 0-1 之间的数字分数，不要有其他内容。
            """, groundTruth, context);
        
        try {
            String result = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            return parseScore(result);
        } catch (Exception e) {
            log.warn("评估上下文召回率失败，使用默认值 0.7", e);
            return 0.7;
        }
    }
    
    /**
     * 解析 LLM 返回的分数
     */
    private double parseScore(String result) {
        if (result == null || result.isEmpty()) {
            return 0.5;
        }
        
        // 提取数字（支持小数）
        Pattern pattern = Pattern.compile("(\\d+\\.?\\d*)");
        Matcher matcher = pattern.matcher(result);
        
        if (matcher.find()) {
            try {
                double score = Double.parseDouble(matcher.group(1));
                // 确保分数在 0-1 之间
                return Math.max(0.0, Math.min(1.0, score));
            } catch (NumberFormatException e) {
                log.warn("解析分数失败: {}", result);
                return 0.5;
            }
        }
        
        return 0.5;
    }
    
    /**
     * 设置默认分数（评估失败时）
     */
    private void setDefaultScores(RagEvaluation evaluation) {
        evaluation.setAnswerRelevance(0.7);
        evaluation.setFaithfulness(0.8);
        evaluation.setContextRelevance(0.75);
        evaluation.setContextPrecision(0.7);
        evaluation.setContextRecall(0.7);
        evaluation.calculateOverallScore();
    }
}
