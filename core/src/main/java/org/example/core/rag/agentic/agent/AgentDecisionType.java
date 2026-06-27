package org.example.core.rag.agentic.agent;

/**
 * Agent 决策类型枚举。
 */
public enum AgentDecisionType {
    /** 查询分解 */
    DECOMPOSE,
    /** 调用工具 */
    CALL_TOOL,
    /** 综合多源结果 */
    SYNTHESIZE,
    /** 生成答案 */
    GENERATE,
    /** 终止 */
    TERMINATE
}
