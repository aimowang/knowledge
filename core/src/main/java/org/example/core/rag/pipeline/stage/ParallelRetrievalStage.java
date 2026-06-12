package org.example.core.rag.pipeline.stage;

import lombok.extern.slf4j.Slf4j;
import org.example.core.rag.context.RagContext;
import org.example.core.rag.pipeline.PipelineStage;
import org.example.core.rag.strategy.RetrievalStrategy;
import org.springframework.ai.document.Document;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 并行检索阶段
 * 对多查询列表进行并行检索，提升召回率
 * 
 * 特性：
 * - 并行执行多个查询（线程池）
 * - 超时控制（单个查询3秒，总计10秒）
 * - 结果合并和去重
 */
@Slf4j
public class ParallelRetrievalStage implements PipelineStage {
    
    private final RetrievalStrategy retrievalStrategy;
    private final int queryTimeoutSeconds;
    private final int totalTimeoutSeconds;
    
    public ParallelRetrievalStage(RetrievalStrategy retrievalStrategy) {
        this(retrievalStrategy, 3, 10);
    }
    
    public ParallelRetrievalStage(RetrievalStrategy retrievalStrategy, 
                                  int queryTimeoutSeconds, 
                                  int totalTimeoutSeconds) {
        this.retrievalStrategy = retrievalStrategy;
        this.queryTimeoutSeconds = queryTimeoutSeconds;
        this.totalTimeoutSeconds = totalTimeoutSeconds;
    }
    
    @Override
    public void process(RagContext context) {
        List<String> multiQueries = context.getMultiQueries();
        
        // 如果没有多查询，跳过并行检索
        if (multiQueries == null || multiQueries.isEmpty()) {
            log.debug("无多查询，跳过并行检索");
            return;
        }
        
        // 添加原始查询到列表开头（确保原始查询也被检索）
        String originalQuery = context.getCurrentQuery();
        Set<String> allQueries = new LinkedHashSet<>();
        allQueries.add(originalQuery);
        allQueries.addAll(multiQueries);
        
        log.info("开始并行检索 - {} 个查询", allQueries.size());
        
        try {
            // 并行执行所有查询
            List<Document> allDocs = Collections.synchronizedList(new ArrayList<>());
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            for (String query : allQueries) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        log.debug("执行查询: {}", truncate(query));
                        
                        // 创建临时上下文用于单次检索
                        RagContext tempContext = createTempContext(context, query);
                        
                        // 执行检索（带超时控制）
                        CompletableFuture<List<Document>> retrievalFuture = 
                            CompletableFuture.supplyAsync(() -> 
                                retrievalStrategy.retrieve(query, tempContext)
                            );
                        
                        List<Document> docs = retrievalFuture.get(queryTimeoutSeconds, TimeUnit.SECONDS);
                        
                        if (docs != null && !docs.isEmpty()) {
                            allDocs.addAll(docs);
                            log.debug("查询 '{}' 检索到 {} 个文档", 
                                truncate(query), docs.size());
                        }
                        
                    } catch (Exception e) {
                        log.warn("查询 '{}' 检索失败: {}", truncate(query), e.getMessage());
                    }
                });
                
                futures.add(future);
            }
            
            // 等待所有查询完成（总超时控制）
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(totalTimeoutSeconds, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("部分查询未完成（超时 {} 秒），使用已获取的结果", totalTimeoutSeconds);
            }
            
            // 去重合并结果
            List<Document> deduplicatedDocs = deduplicateDocuments(allDocs);
            
            context.setDocuments(deduplicatedDocs);
            log.info("并行检索完成 - 获得 {} 个不重复文档", deduplicatedDocs.size());
            
        } catch (Exception e) {
            log.error("并行检索失败: {}", e.getMessage(), e);
            // 失败时保留原结果或设置为空
            context.setDocuments(List.of());
        }
    }
    
    /**
     * 创建临时上下文（复制配置，替换查询）
     */
    private RagContext createTempContext(RagContext original, String query) {
        RagContext temp = new RagContext();
        temp.setOriginalQuestion(original.getOriginalQuestion());
        temp.setCurrentQuery(query);
        temp.setUserId(original.getUserId());
        temp.setSource(original.getSource());
        temp.setComplexity(original.getComplexity());
        temp.setRetrievalConfig(original.getRetrievalConfig());
        temp.setMemoryContext(original.getMemoryContext());
        return temp;
    }
    
    /**
     * 简单的文档去重（基于内容哈希）
     */
    private List<Document> deduplicateDocuments(List<Document> docs) {
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }
        
        Map<String, Document> uniqueDocs = new LinkedHashMap<>();
        
        for (Document doc : docs) {
            if (doc.getText() != null && !doc.getText().trim().isEmpty()) {
                String key = generateDocKey(doc);
                uniqueDocs.putIfAbsent(key, doc);
            }
        }
        
        return new ArrayList<>(uniqueDocs.values());
    }
    
    /**
     * 生成文档唯一键（用于去重）
     */
    private String generateDocKey(Document doc) {
        // 使用前100个字符的哈希作为键
        String content = doc.getText();
        if (content.length() > 100) {
            content = content.substring(0, 100);
        }
        return Integer.toString(content.hashCode());
    }
    
    @Override
    public String getName() {
        return "ParallelRetrievalStage";
    }
    
    @Override
    public boolean shouldSkip(RagContext context) {
        // 如果没有多查询或检索策略为空，跳过
        List<String> multiQueries = context.getMultiQueries();
        return multiQueries == null || multiQueries.isEmpty() || retrievalStrategy == null;
    }
    
    /**
     * 截断长字符串用于日志
     */
    private String truncate(String text) {
        if (text == null) return "";
        return text.length() > 50 ? text.substring(0, 50) + "..." : text;
    }
}
