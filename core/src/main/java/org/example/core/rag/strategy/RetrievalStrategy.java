package org.example.core.rag.strategy;

import org.example.core.rag.context.RagContext;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 检索策略接口
 * 负责从向量数据库中检索相关文档
 */
public interface RetrievalStrategy {
    
    /**
     * 执行检索
     * @param query 查询文本
     * @param context RAG 执行上下文
     * @return 检索到的文档列表
     */
    List<Document> retrieve(String query, RagContext context);
    
    /**
     * 是否支持并行检索
     * @return true 表示支持
     */
    default boolean supportsParallel() {
        return false;
    }
    
    /**
     * 策略名称
     * @return 策略名称
     */
    String getName();
}
