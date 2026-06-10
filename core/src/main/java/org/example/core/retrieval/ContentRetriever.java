package org.example.core.retrieval;

import org.example.core.document.State;
import org.springframework.ai.document.Document;

import java.util.List;

public interface ContentRetriever extends State {
    List<Document> retrieve(String query, String source);
    List<Document> retrieve(String query, int topK, double similarityThreshold, String source);
}
