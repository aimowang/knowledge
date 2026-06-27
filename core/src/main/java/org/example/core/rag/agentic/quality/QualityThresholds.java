package org.example.core.rag.agentic.quality;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 质量阈值配置。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QualityThresholds {
    private double faithfulness = 0.7;
    private double answerRelevancy = 0.6;
    private double citationGrounding = 0.8;
}
