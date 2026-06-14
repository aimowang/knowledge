package org.example.core.retrieval;

import io.micrometer.common.util.StringUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 混合检索器 — 向量相似度 (稠密) + BM25 关键词 (稀疏)
 * <p>
 * 采用 <b>RRF (Reciprocal Rank Fusion)</b> 算法将两种检索结果合并:
 * <pre>
 *   RRF(d) = w_vec · 1/(k + r_vec(d)) + w_bm25 · 1/(k + r_bm25(d))
 * </pre>
 * 其中 k = 60 (RRF 常数), w_vec / w_bm25 通过配置控制向量 / 关键词比重。
 * <p>
 * 当 BM25 索引为空时自动降级为纯向量检索。
 */
@Slf4j
@Primary
@Component
public class HybirdContentRetriever implements ContentRetriever {

    @Resource
    private VectorStore vectorStore;

    @Resource
    private Bm25Indexer bm25Indexer;

    @Value("${retriever.max-results:5}")
    private int maxResults;

    @Value("${retriever.min-score:0.5}")
    private double minScore;

    /** BM25 检索权重 (0~1)，与 vectorWeight 之和不必为 1 */
    @Value("${retriever.hybrid.weight-bm25:0.3}")
    private double bm25Weight;

    /** 向量检索权重 (0~1) */
    @Value("${retriever.hybrid.weight-vector:0.7}")
    private double vectorWeight;

    /** RRF 常数 (越大则排名靠后的文档获得的分数越高) */
    private static final int RRF_K = 60;

    @Override
    public List<Document> retrieve(String query, String source) {
        return retrieve(query, maxResults, minScore, source);
    }

    @Override
    public List<Document> retrieve(String query, int topK, double similarityThreshold, String source) {
        // 1. 向量检索
        List<Document> vectorDocs = vectorSearch(query, topK * 2, similarityThreshold, source);

        // 2. BM25 关键词检索
        List<Bm25Indexer.ScoredDocument> bm25Docs = bm25Indexer.search(query, topK * 2);

        if (bm25Docs.isEmpty()) {
            // BM25 索引为空 → 降级为纯向量
            log.debug("BM25 索引为空，降级为纯向量检索, query={}", truncate(query));
            return vectorDocs.size() > topK ? vectorDocs.subList(0, topK) : vectorDocs;
        }

        // 3. RRF 合并
        List<Document> merged = mergeWithRrf(vectorDocs, bm25Docs, topK);

        log.info("混合检索完成 — 向量={}, BM25={}, 合并后={}",
                vectorDocs.size(), bm25Docs.size(), merged.size());

        return merged;
    }

    // ==================== 向量检索 ====================

    /**
     * 执行向量相似度搜索
     */
    private List<Document> vectorSearch(String query, int topK, double similarityThreshold, String source) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(topK);

        if (StringUtils.isNotBlank(source)) {
            builder.filterExpression(String.format("metadata['source'] == '%s'", source));
        } else {
            builder.similarityThreshold(similarityThreshold);
        }

        return vectorStore.similaritySearch(builder.build());
    }

    // ==================== RRF 融合 ====================

    /**
     * 使用 Reciprocal Rank Fusion 合并两种检索结果
     * <p>
     * 以文档文本内容 (content) 作为匹配 Key，兼容 Milvus 自动 ID 与 BM25 索引 Key 不一致的问题。
     */
    private List<Document> mergeWithRrf(
            List<Document> vectorDocs,
            List<Bm25Indexer.ScoredDocument> bm25Docs,
            int topK) {

        // 构建 内容→文档 映射 (用于最终返回时还原 Document 对象)
        Map<String, Document> vectorDocByContent = new HashMap<>();
        for (Document doc : vectorDocs) {
            if (doc.getContent() != null) {
                vectorDocByContent.put(doc.getContent(), doc);
            }
        }
        Map<String, Document> bm25DocByContent = new HashMap<>();
        for (Bm25Indexer.ScoredDocument sd : bm25Docs) {
            if (sd.getDocument() != null && sd.getDocument().getContent() != null) {
                bm25DocByContent.put(sd.getDocument().getContent(), sd.getDocument());
            }
        }

        // 计算 RRF 得分: content -> score
        Map<String, Double> rrfScores = new HashMap<>();

        // 向量排名贡献
        for (int i = 0; i < vectorDocs.size(); i++) {
            String content = vectorDocs.get(i).getContent();
            if (content != null) {
                rrfScores.merge(content, vectorWeight / (RRF_K + i + 1), Double::sum);
            }
        }

        // BM25 排名贡献
        for (int i = 0; i < bm25Docs.size(); i++) {
            Document doc = bm25Docs.get(i).getDocument();
            if (doc != null && doc.getContent() != null) {
                rrfScores.merge(doc.getContent(), bm25Weight / (RRF_K + i + 1), Double::sum);
            }
        }

        // 按 RRF 得分排序 → 取 topK → 还原 Document
        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> {
                    String content = entry.getKey();
                    // 优先返回带完整 metadata 的向量文档
                    Document doc = vectorDocByContent.get(content);
                    return doc != null ? doc : bm25DocByContent.get(content);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ==================== 公共方法 ====================

    /**
     * 向 BM25 索引中添加文档（供 KnowledgeEmbeddingService 等外部调用）
     */
    public void addDocuments(List<Document> documents) {
        bm25Indexer.addDocuments(documents);
    }

    /** 获取 BM25 索引大小 */
    public int getBm25IndexSize() {
        return bm25Indexer.size();
    }

    @Override
    public List<String> support() {
        return List.of();
    }

    private String truncate(String text) {
        if (text == null) return "";
        return text.length() > 50 ? text.substring(0, 50) + "..." : text;
    }
}
