package org.example.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RAG 答案和评估结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagAnswerWithEvaluation {
    
    /**
     * 答案内容
     */
    private String answer;
    
    /**
     * 评估结果
     */
    private RagEvaluation evaluation;
}
