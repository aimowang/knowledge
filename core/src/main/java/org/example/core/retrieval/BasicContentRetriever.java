package org.example.core.retrieval;

import io.micrometer.common.util.StringUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class BasicContentRetriever implements ContentRetriever {

    @Resource
    private VectorStore vectorStore;

    @Value("${retriever.max-results:5}")
    private int maxResults;

    @Value("${retriever.min-score:0.5}")
    private double minScore;

    @Override
    public List<Document> retrieve(String query, String source) {
        return retrieve(query, maxResults, minScore, null);
    }

    /**
     * 向量相似度召回
     */
    @Override
    public List<Document> retrieve(String query, int topK, double similarityThreshold, String source) {
        SearchRequest searchRequest;
        if (StringUtils.isNotBlank(source)) {
            searchRequest = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .filterExpression(String.format("metadata['source'] == '%s'", source))  // ← 原生过滤
                    .build();
        } else {
            searchRequest = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .similarityThreshold(similarityThreshold)
                    .build();
        }
        return vectorStore.similaritySearch(searchRequest);
    }

    @Override
    public List<String> support() {
        return List.of();
    }
}
