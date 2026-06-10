package org.example.core.rag;

import org.example.core.document.State;
import org.example.core.rerank.ReRanker;
import org.example.core.retrieval.ContentRetriever;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.function.BiFunction;

public interface RagFlow extends State {

    String executeRag(String query, BiFunction<String, String, String> chatModel);
    String overrideQuery(String query);
    List<String> multiQuery(String query);
    ContentRetriever getContextRetriever();
    ReRanker getReRanker();
    String buildSystemMessage(List<Document> docs);
    String buildUserMessage(String query, List<Document> docs);
}
