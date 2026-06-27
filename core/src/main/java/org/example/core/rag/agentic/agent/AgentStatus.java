package org.example.core.rag.agentic.agent;

/**
 * Agent 执行状态枚举。
 */
public enum AgentStatus {
    /** 运行中 */
    RUNNING,
    /** 已完成 */
    COMPLETED,
    /** 执行失败 */
    FAILED,
    /** 执行超时 */
    TIMEOUT
}
