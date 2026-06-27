package org.example.core.rag.agentic.trajectory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Agent 执行单步记录 — 用于轨迹回放和审计。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepRecord {

    /** 步骤序号 */
    private int stepNumber;

    /** 所在循环轮次 */
    private int loopNumber;

    /** 步骤类型 */
    private String type;

    /** 步骤描述 */
    private String description;

    /** 耗时 (ms) */
    private long durationMs;

    // ── 工具调用相关 ──

    /** 工具名称（仅 TOOL_CALL 类型） */
    private String toolName;

    /** 工具输入参数 */
    private Map<String, Object> toolInput;

    /** 工具输出结果摘要 */
    private Object toolOutput;

    // ── LLM 决策相关 ──

    /** LLM 调用时的 Prompt 摘要 */
    private String llmPromptSummary;

    /** LLM 响应摘要 */
    private String llmResponseSummary;
}
