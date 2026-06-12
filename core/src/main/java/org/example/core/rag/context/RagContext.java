package org.example.core.rag.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.model.RetrievalConfig;
import org.example.model.enums.ComplexityLevelEnum;
import org.springframework.ai.document.Document;

import java.util.*;

/**
 * RAG 执行上下文 - 贯穿整个流程
 * 封装所有中间状态和元数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagContext {
    
    // ==================== 输入参数 ====================
    
    /**
     * 原始问题
     */
    private String originalQuestion;
    
    /**
     * 用户ID（可选）
     */
    private String userId;
    
    /**
     * 文档来源过滤（可选）
     */
    private String source;
    
    /**
     * 问题复杂度级别
     */
    private ComplexityLevelEnum complexity;
    
    // ==================== 查询处理 ====================
    
    /**
     * 当前查询（会被各个 Stage 逐步更新）
     * 流程：originalQuestion → preprocessed → enhanced → expanded
     */
    private String currentQuery;
    
    /**
     * 多查询列表（仅复杂问题使用）
     */
    @Builder.Default
    private List<String> multiQueries = new ArrayList<>();
    
    // ==================== 文档处理 ====================
    
    /**
     * 当前文档列表（会被各个 Stage 逐步更新）
     * 流程：retrieved → deduplicated → filtered → reranked → final
     */
    @Builder.Default
    private List<Document> documents = new ArrayList<>();
    
    // ==================== 生成结果 ====================
    
    /**
     * 生成的答案
     */
    private String answer;
    
    /**
     * 引用来源列表
     */
    @Builder.Default
    private List<String> sources = new ArrayList<>();
    
    // ==================== 上下文配置 ====================
    
    /**
     * 记忆上下文
     */
    @Builder.Default
    private MemoryContext memoryContext = MemoryContext.EMPTY;
    
    /**
     * 检索配置
     */
    @Builder.Default
    private RetrievalConfig retrievalConfig = new RetrievalConfig(5, 0.7);
    
    // ==================== 元数据和监控 ====================
    
    /**
     * 自定义元数据
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
    
    /**
     * 开始时间（毫秒）
     */
    private long startTime;
    
    /**
     * 各阶段耗时（毫秒）
     */
    @Builder.Default
    private Map<String, Long> stageDurations = new HashMap<>();
    
    /**
     * 是否使用增强流程（多查询+CRAG）
     */
    private boolean useEnhancedFlow;
    
    // ==================== 工具方法 ====================
    
    /**
     * 记录阶段耗时
     */
    public void recordStageDuration(String stageName, long durationMs) {
        this.stageDurations.put(stageName, durationMs);
    }
    
    /**
     * 获取总耗时
     */
    public long getTotalDuration() {
        if (startTime == 0) {
            return 0;
        }
        return System.currentTimeMillis() - startTime;
    }
    
    /**
     * 设置当前查询
     */
    public void setCurrentQuery(String query) {
        this.currentQuery = query;
    }
    
    /**
     * 获取当前查询
     */
    public String getCurrentQuery() {
        return currentQuery != null ? currentQuery : originalQuestion;
    }
    
    /**
     * 设置当前文档列表
     */
    public void setDocuments(List<Document> docs) {
        this.documents = docs != null ? docs : new ArrayList<>();
    }
    
    /**
     * 获取当前文档列表
     */
    public List<Document> getDocuments() {
        return documents != null ? documents : new ArrayList<>();
    }
    
    /**
     * 添加元数据
     */
    public void addMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }
    
    /**
     * 获取元数据
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, Class<T> type) {
        Object value = this.metadata.get(key);
        return value != null && type.isInstance(value) ? (T) value : null;
    }
}
