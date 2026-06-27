package org.example.core.rag.agentic.tool;

import java.util.Map;

/**
 * Agent 工具接口 — 适配 AgentScope HarnessAgent 的 .tool() 注册机制。
 *
 * <p>每个 AgentTool 实现类包装一个现有检索组件（如 HybirdContentRetriever、Bm25Indexer），
 * 通过 ToolRegistry 收集后统一注册到 HarnessAgent。</p>
 *
 * <p>命名规范：
 * <ul>
 *   <li>name 使用 snake_case，如 "vector_search"</li>
 *   <li>description 包含适用场景、限制、返回格式</li>
 *   <li>parametersSchema 使用 JSON Schema 格式的 Map 描述</li>
 * </ul>
 */
public interface AgentTool {

    /**
     * 工具名称，LLM 选择工具的依据。
     * 格式：snake_case，如 "vector_search", "bm25_search"
     */
    String getName();

    /**
     * 工具描述，LLM 据此理解工具用途。
     * 应包含：适用场景、使用限制、返回格式
     */
    String getDescription();

    /**
     * 工具参数 Schema，用于 LLM 生成 Function Calling 参数。
     * <p>JSON Schema 格式的 Map，示例：
     * <pre>
     * {
     *   "query": {"type": "string", "description": "检索查询文本", "required": true},
     *   "top_k": {"type": "integer", "description": "返回文档数量", "default": 5}
     * }
     * </pre>
     */
    Map<String, Object> getParametersSchema();

    /**
     * 执行工具。
     *
     * @param params 参数键值对（由 LLM 根据 parametersSchema 生成）
     * @return 执行结果，含成功/失败状态
     */
    ToolResult execute(Map<String, Object> params);

    /**
     * 工具是否可用。
     * 例如：数据库断开时 SQL 工具返回 false，外部搜索 API Key 未配置时返回 false。
     */
    default boolean isAvailable() {
        return true;
    }
}
