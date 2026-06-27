package org.example.core.rag.agentic.agent;

import lombok.Data;

/**
 * Agent 执行流事件 — 用于 SSE 流式输出。
 *
 * <p>每个事件代表 Agent 执行过程中的一个里程碑。
 * type 字段用于前端区分不同阶段。
 */
@Data
public class StreamEvent {

    /** 事件类型 */
    private final String type;

    /** 事件内容 */
    private final String content;

    /** 事件时间戳 */
    private final long timestamp = System.currentTimeMillis();

    // ── 事件类型常量 ──
    public static final String TYPE_THINKING = "thinking";
    public static final String TYPE_TOOL_CALL = "tool_call";
    public static final String TYPE_TOOL_RESULT = "tool_result";
    public static final String TYPE_CHECK = "check";
    public static final String TYPE_GENERATING = "generating";
    public static final String TYPE_TOKEN = "token";
    public static final String TYPE_DONE = "done";
    public static final String TYPE_ERROR = "error";

    public static StreamEvent thinking(String message) {
        return new StreamEvent(TYPE_THINKING, message);
    }

    public static StreamEvent toolCall(String toolName, Object params) {
        return new StreamEvent(TYPE_TOOL_CALL, toolName + ": " + params);
    }

    public static StreamEvent toolResult(String toolName, int resultCount) {
        return new StreamEvent(TYPE_TOOL_RESULT,
            toolName + " 返回 " + resultCount + " 条结果");
    }

    public static StreamEvent check(String message) {
        return new StreamEvent(TYPE_CHECK, message);
    }

    public static StreamEvent generating() {
        return new StreamEvent(TYPE_GENERATING, "正在生成答案...");
    }

    public static StreamEvent token(String text) {
        return new StreamEvent(TYPE_TOKEN, text);
    }

    public static StreamEvent done(String finalAnswer) {
        return new StreamEvent(TYPE_DONE, finalAnswer);
    }

    public static StreamEvent error(String message) {
        return new StreamEvent(TYPE_ERROR, message);
    }
}
