package org.example.core.rerank;

import org.example.core.document.State;
import org.springframework.ai.document.Document;

import java.util.List;

public interface ReRanker extends State {
    List<Document> rerank(List<Document> documents, String query, int k, String... args);
}
