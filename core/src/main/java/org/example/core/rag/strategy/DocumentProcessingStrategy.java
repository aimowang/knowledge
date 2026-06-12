package org.example.core.rag.strategy;

import org.example.core.rag.context.RagContext;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 文档处理策略接口
 * 负责对检索到的文档进行处理（去重、过滤、压缩等）
 */
public interface DocumentProcessingStrategy {
    
    /**
     * 处理文档列表
     * @param documents 原始文档列表
     * @param context RAG 执行上下文
     * @return 处理后的文档列表
     */
    List<Document> process(List<Document> documents, RagContext context);
    
    /**
     * 策略类型
     * @return 策略类型（DEDUPLICATION, FILTERING, COMPRESSION, RERANKING等）
     */
    ProcessingType getType();
    
    /**
     * 策略名称
     * @return 策略名称
     */
    String getName();
    
    /**
     * 处理类型枚举
     */
    enum ProcessingType {
        DEDUPLICATION,    // 去重
        FILTERING,        // 过滤
        COMPRESSION,      // 压缩
        RERANKING,        // 重排序
        PERSONALIZATION   // 个性化
    }
}
