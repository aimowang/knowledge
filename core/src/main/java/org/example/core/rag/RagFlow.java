package org.example.core.rag;

import org.example.core.document.State;
import org.example.core.rerank.ReRanker;
import org.example.core.retrieval.ContentRetriever;
import org.example.model.RagAnswer;

import java.util.List;

public interface RagFlow extends State {

    
    /**
     * 执行 RAG 流程（支持文档来源过滤）
     * @param question 问题
     * @param userId 用户ID（可选，用于加载记忆和个性化）
     * @param source 文档来源过滤（可选，为 null 时不过滤）
     * @return 带来源的答案
     */
    RagAnswer executeRag(String question, String userId, String source);
    
    // ==================== 辅助方法（供内部使用）====================
    
    String overrideQuery(String query);
    List<String> multiQuery(String query);
    ContentRetriever getContextRetriever();
    ReRanker getReRanker();
}
