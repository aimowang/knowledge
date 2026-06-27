package org.example.core.rag.agentic.tool;

import org.example.core.rag.agentic.agent.AgentConfig;
import org.example.core.rag.handler.ExternalSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 外部搜索工具 — 包装 ExternalSearchService。
 *
 * <p>工具定义：
 * <ul>
 *   <li>name: "external_search"</li>
 *   <li>description: 搜索互联网获取实时信息</li>
 *   <li>params: query(必填), top_k(选填,默认3)</li>
 * </ul>
 *
 * <p>通过 {@code agentic-rag.tool.external-search.enabled} 控制启用。
 */
@Component
@ConditionalOnProperty(name = "agentic-rag.tool.external-search.enabled",
                       havingValue = "true", matchIfMissing = false)
public class ExternalSearchTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(ExternalSearchTool.class);

    private final ExternalSearchService searchService;
    private final AgentConfig config;

    public ExternalSearchTool(ExternalSearchService searchService, AgentConfig config) {
        this.searchService = searchService;
        this.config = config;
    }

    @Override
    public String getName() {
        return "external_search";
    }

    @Override
    public String getDescription() {
        return "搜索互联网获取实时信息。" +
               "适用于时效性强的查询、内部知识库未覆盖的内容、最新新闻/技术动态。" +
               "返回文档列表，每篇包含标题、摘要和来源链接。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
            "query", Map.of(
                "type", "string",
                "description", "搜索查询",
                "required", true
            ),
            "top_k", Map.of(
                "type", "integer",
                "description", "返回结果数量",
                "default", config.getTool().getExternalSearch().getTopK()
            )
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolResult execute(Map<String, Object> params) {
        long startTime = System.currentTimeMillis();
        try {
            String query = (String) params.get("query");
            if (query == null || query.isBlank()) {
                return ToolResult.failure("搜索查询 'query' 不能为空");
            }

            int topK = params.containsKey("top_k")
                ? ((Number) params.get("top_k")).intValue()
                : config.getTool().getExternalSearch().getTopK();

            log.debug("ExternalSearchTool 执行: query='{}', top_k={}", query, topK);
            List<Document> results = searchService.search(query);
            long duration = System.currentTimeMillis() - startTime;

            // 限制返回数量
            List<Document> limited = results.size() > topK
                ? results.subList(0, topK) : results;

            log.debug("ExternalSearchTool 完成: 返回 {} 篇结果, 耗时 {}ms",
                limited.size(), duration);
            return ToolResult.success(limited, duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("ExternalSearchTool 执行失败: {}", e.getMessage(), e);
            return ToolResult.failure("外部搜索失败: " + e.getMessage());
        }
    }

    @Override
    public boolean isAvailable() {
        // 外部搜索需要 API Key 配置
        return config.getTool().getExternalSearch().isEnabled();
    }
}
