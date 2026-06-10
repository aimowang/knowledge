package org.example.core.rag.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.core.rag.AbstractBasicRag;
import org.example.core.rerank.ReRanker;
import org.example.core.retrieval.ContentRetriever;
import org.example.model.enums.CategoryEnum;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基础 RAG 实现示例
 * 支持所有分类（category = "all"）
 */
@Slf4j
@Component
public class BasicRagFlow extends AbstractBasicRag {

    private final ContentRetriever contentRetriever;
    private final ReRanker reRanker;
    private final ChatClient chatClient;

    public BasicRagFlow(ContentRetriever contentRetriever, ReRanker reRanker, ChatClient chatClient) {
        this.contentRetriever = contentRetriever;
        this.reRanker = reRanker;
        this.chatClient = chatClient;
    }

    @Override
    public ContentRetriever getContextRetriever(String category) {
        // 可以根据分类返回不同的 Retriever
        // 这里简单返回默认的 retriever
        log.debug("获取分类 [{}] 的检索器", category);
        return contentRetriever;
    }

    @Override
    public ReRanker getReRanker() {
        return reRanker;
    }

    @Override
    public List<String> support() {
        // 支持所有分类
        return List.of(CategoryEnum.ALL.getValue());
    }

    /**
     * 可选：重写分类方法，实现智能分类
     */
    @Override
    public String classifyQuestion(String question) {
        // 这里可以调用 LLM 进行分类
        // 为了演示，我们简单返回 "all"
        return "all";
    }
}
