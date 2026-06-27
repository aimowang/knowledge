package org.example.core.rag.agentic.tool;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 工具执行结果封装。
 *
 * <p>包含执行状态、返回数据、错误信息和耗时，统一工具调用返回值类型。</p>
 */
@Data
@AllArgsConstructor
public class ToolResult {

    /** 执行是否成功 */
    private final boolean success;

    /** 返回数据（文档列表、查询结果等） */
    private final Object data;

    /** 错误信息（success=false 时有效） */
    private final String errorMessage;

    /** 执行耗时（ms） */
    private final long durationMs;

    public static ToolResult success(Object data) {
        return new ToolResult(true, data, null, System.currentTimeMillis());
    }

    public static ToolResult success(Object data, long durationMs) {
        return new ToolResult(true, data, null, durationMs);
    }

    public static ToolResult failure(String errorMessage) {
        return new ToolResult(false, null, errorMessage, System.currentTimeMillis());
    }

    @SuppressWarnings("unchecked")
    public <T> T getData() {
        return (T) data;
    }
}
