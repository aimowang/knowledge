package org.example.core.rag.agentic.quality;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 质量评分模型 — LLM Judge 的输出。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QualityScores {

    /** 忠实度：答案是否严格基于检索上下文，不包含无依据内容 */
    private double faithfulness;

    /** 相关性：答案是否直接回答用户问题，不偏题 */
    private double answerRelevancy;

    /** 引用完整性：每个关键主张是否有明确的 [N] 引用 */
    private double citationGrounding;

    /**
     * 判断是否通过质量阈值。
     */
    public boolean isPassing(double faithfulnessThreshold,
                              double relevancyThreshold,
                              double citationThreshold) {
        return faithfulness >= faithfulnessThreshold
            && answerRelevancy >= relevancyThreshold
            && citationGrounding >= citationThreshold;
    }

    /**
     * 判断是否通过质量阈值（使用配置对象）。
     */
    public boolean isPassing(QualityThresholds thresholds) {
        return isPassing(
            thresholds.getFaithfulness(),
            thresholds.getAnswerRelevancy(),
            thresholds.getCitationGrounding()
        );
    }
}
