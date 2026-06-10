package org.example.core.rag.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.core.compress.HybridCompressor;
import org.example.core.rag.AbstractBasicRag;
import org.example.core.rag.QueryComplexityClassifier;
import org.example.core.retrieval.BasicContentRetriever;
import org.example.core.retrieval.ContentRetriever;
import org.example.model.RetrievalConfig;
import org.example.model.enums.CategoryEnum;
import org.example.model.enums.ComplexityLevelEnum;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 基础 RAG 流程实现
 * 
 * 特点：
 * - 不使用多查询生成
 * - 不使用 CRAG 流程
 * - 使用基础的检索和压缩策略
 */
@Slf4j
@Component
public class BasicRagFlow extends AbstractBasicRag {

    private final BasicContentRetriever contentRetriever;

    public BasicRagFlow(BasicContentRetriever contentRetriever, 
                       HybridCompressor hybridCompressor, 
                       QueryComplexityClassifier queryComplexityClassifier,
                       ChatClient chatClient) {
        super(queryComplexityClassifier, chatClient, hybridCompressor);
        this.contentRetriever = contentRetriever;
        log.info("BasicRagFlow 初始化完成");
    }

    @Override
    public List<String> support() {
        return List.of(CategoryEnum.BASIC.getValue());
    }

    @Override
    public ContentRetriever getContextRetriever() {
        return contentRetriever;
    }

    // ==================== 个性化定制（钩子方法重写）====================

    /**
     * BasicRagFlow 不启用多查询（保持简单）
     */
    @Override
    protected boolean shouldUseMultiQuery(String question, ComplexityLevelEnum complexity) {
        log.debug("BasicRagFlow: 禁用多查询功能");
        return false;
    }

    /**
     * BasicRagFlow 不启用 CRAG（保持简单）
     */
    @Override
    protected boolean shouldUseCRAG(ComplexityLevelEnum complexity) {
        log.debug("BasicRagFlow: 禁用 CRAG 功能");
        return false;
    }

    /**
     * 自定义检索配置（更保守的参数）
     */
    @Override
    protected RetrievalConfig getRetrievalConfig(ComplexityLevelEnum complexity) {
        return switch (complexity) {
            case SIMPLE -> new RetrievalConfig(0, 0.0);
            case MODERATE -> new RetrievalConfig(3, 0.6);  // 比默认更少的文档
            case COMPLEX -> new RetrievalConfig(5, 0.7);   // 比默认更少的文档
        };
    }

    /**
     * 自定义文档过滤策略（更严格）
     */
    @Override
    protected List<Document> filterDocuments(List<Document> docs, String query) {
        log.debug("BasicRagFlow: 应用严格的文档过滤");
        return docs.stream()
                .filter(doc -> doc.getText() != null && doc.getText().length() > 50)  // 更长的最小长度
                .collect(Collectors.toList());
    }
}
