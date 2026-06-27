package org.example.core.rag.agentic.tool;

import org.example.core.rag.agentic.agent.AgentConfig;
import org.example.core.retrieval.HybirdContentRetriever;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 向量检索工具 — 包装 HybirdContentRetriever。
 *
 * <p>工具定义：
 * <ul>
 *   <li>name: "vector_search"</li>
 *   <li>description: 基于向量相似度和 BM25 关键词混合检索知识库文档</li>
 *   <li>params: query(必填), top_k(选填,默认5), source(选填)</li>
 * </ul>
 */
@Component
public class VectorSearchTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(VectorSearchTool.class);

    private final HybirdContentRetriever retriever;
    private final AgentConfig config;

    public VectorSearchTool(HybirdContentRetriever retriever, AgentConfig config) {
        this.retriever = retriever;
        this.config = config;
    }

    @Override
    public String getName() {
        return "vector_search";
    }

    @Override
    public String getDescription() {
        return "基于向量相似度和 BM25 关键词混合检索知识库文档。" +
               "适用于语义搜索、开放域问答、中文/英文文档检索。" +
               "返回文档列表，每篇包含文档内容和来源信息。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
            "query", Map.of(
                "type", "string",
                "description", "检索查询文本",
                "required", true
            ),
            "top_k", Map.of(
                "type", "integer",
                "description", "返回文档数量",
                "default", config.getTool().getVectorSearch().getTopK()
            ),
            "source", Map.of(
                "type", "string",
                "description", "按来源过滤（文件名）"
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
                return ToolResult.failure("查询参数 'query' 不能为空");
            }

            int topK = params.containsKey("top_k")
                ? ((Number) params.get("top_k")).intValue()
                : config.getTool().getVectorSearch().getTopK();

            String source = (String) params.get("source");

            log.debug("VectorSearchTool 执行: query='{}', top_k={}, source={}", query, topK, source);
            List<Document> docs = retriever.retrieve(query, topK,
                config.getTool().getVectorSearch().getSimilarityThreshold(), source);
            long duration = System.currentTimeMillis() - startTime;
            log.debug("VectorSearchTool 完成: 返回 {} 篇文档, 耗时 {}ms", docs.size(), duration);

            return ToolResult.success(docs, duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("VectorSearchTool 执行失败: {}", e.getMessage(), e);
            return ToolResult.failure("向量检索失败: " + e.getMessage());
        }
    }
}
