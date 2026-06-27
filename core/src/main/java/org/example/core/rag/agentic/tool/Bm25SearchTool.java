package org.example.core.rag.agentic.tool;

import org.example.core.rag.agentic.agent.AgentConfig;
import org.example.core.retrieval.Bm25Indexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * BM25 关键词检索工具 — 包装 Bm25Indexer。
 *
 * <p>工具定义：
 * <ul>
 *   <li>name: "bm25_search"</li>
 *   <li>description: 基于 BM25 算法的关键词精确搜索</li>
 *   <li>params: query(必填), top_k(选填,默认5)</li>
 * </ul>
 */
@Component
public class Bm25SearchTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(Bm25SearchTool.class);

    private final Bm25Indexer bm25Indexer;
    private final AgentConfig config;

    public Bm25SearchTool(Bm25Indexer bm25Indexer, AgentConfig config) {
        this.bm25Indexer = bm25Indexer;
        this.config = config;
    }

    @Override
    public String getName() {
        return "bm25_search";
    }

    @Override
    public String getDescription() {
        return "基于 BM25 算法的关键词精确搜索。" +
               "适用于专业术语、代码片段、配置项、型号等精确匹配场景。" +
               "返回文档列表，每篇包含文档内容和相关性评分。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
            "query", Map.of(
                "type", "string",
                "description", "关键词查询",
                "required", true
            ),
            "top_k", Map.of(
                "type", "integer",
                "description", "返回文档数量",
                "default", config.getTool().getBm25Search().getTopK()
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
                : config.getTool().getBm25Search().getTopK();

            log.debug("BM25SearchTool 执行: query='{}', top_k={}", query, topK);
            List<Bm25Indexer.ScoredDocument> scoredDocs = bm25Indexer.search(query, topK);
            long duration = System.currentTimeMillis() - startTime;

            // 将 ScoredDocument 转为 Document
            List<Document> docs = scoredDocs.stream()
                .map(sd -> {
                    Document doc = new Document(sd.getDocument().getText(),
                        sd.getDocument().getMetadata());
                    doc.getMetadata().put("bm25_score", sd.getScore());
                    return doc;
                })
                .collect(Collectors.toList());

            log.debug("BM25SearchTool 完成: 返回 {} 篇文档, 耗时 {}ms", docs.size(), duration);
            return ToolResult.success(docs, duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("BM25SearchTool 执行失败: {}", e.getMessage(), e);
            return ToolResult.failure("BM25 搜索失败: " + e.getMessage());
        }
    }
}
