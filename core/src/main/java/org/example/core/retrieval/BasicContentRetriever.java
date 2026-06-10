package org.example.core.retrieval;

import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BasicContentRetriever implements ContentRetriever {

    @Resource
    private VectorStore vectorStore;

    @Value("${retriever.max-results:5}")
    private int maxResults;

    @Value("${retriever.min-score:0.5}")
    private double minScore;

    @Override
    public List<Document> retrieve(String query) {
        return retrieve(query, maxResults, minScore);
    }

    /**
     * 向量相似度召回
     */
    @Override
    public List<Document> retrieve(String query, int topK, double similarityThreshold) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build();
        return vectorStore.similaritySearch(searchRequest);
    }

    @Override
    public List<String> support() {
        return List.of();
    }
}
