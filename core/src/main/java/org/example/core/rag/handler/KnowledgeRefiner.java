package org.example.core.rag.handler;

import org.example.core.compress.HybridCompressor;
import org.example.core.retrieval.ContentRetriever;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KnowledgeRefiner {
    private final ContentRetriever retriever;
    private final ChatClient chatClient;
    private final HybridCompressor compressor;

    public KnowledgeRefiner(ContentRetriever retriever, @Qualifier("fastChatClient") ChatClient chatClient, HybridCompressor compressor) {
        this.retriever = retriever;
        this.chatClient = chatClient;
        this.compressor = compressor;
    }

    public List<Document> refine(String question, List<Document> originalDocs) {
        // 策略1：从文档中抽取最相关句子（类似Embedding压缩）
        List<Document> refinedDocs = extractRelevantSentences(question, originalDocs);
        // 策略2：重写查询再检索 todo: 重写
        String refinedQuery = rewriteQuery(question);
        return retriever.retrieve(refinedQuery, null);
    }

    private String rewriteQuery(String question) {
        return chatClient.prompt(new Prompt("请重写查询：" + question)).call().content();
    }

    private List<Document> extractRelevantSentences(String question, List<Document> originalDocs) {
        // 假设这里是一个模拟的实现
        return compressor.compress(originalDocs, question);
    }
}
