package org.example.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Agentic RAG 问答响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgenticAskResponse {
    /** 最终答案 */
    private String answer;

    /** 引用来源 */
    private List<String> sources;

    /** 执行轨迹 ID */
    private String trajectoryId;

    /** Agent 循环轮次 */
    private int loopCount;

    /** 质量评分 */
    private Map<String, Double> qualityScores;

    /** 总耗时 (ms) */
    private long totalDurationMs;

    /** 是否为 Agent 模式 */
    private boolean agenticMode;
}
