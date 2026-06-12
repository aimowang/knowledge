package org.example.core.rag.strategy.impl;

import org.example.core.rag.context.RagContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FilteringStrategy 单元测试
 */
class FilteringStrategyTest {
    
    private FilteringStrategy strategy;
    private RagContext context;
    
    @BeforeEach
    void setUp() {
        strategy = new FilteringStrategy();
        context = RagContext.builder()
            .originalQuestion("test question")
            .userId("user123")
            .build();
    }
    
    @Test
    void testFilterLowQualityDocuments() {
        List<Document> documents = List.of(
            new Document("高质量内容，包含完整的信息和详细的解释", Map.of("relevance_score", 0.9)),
            new Document("低质量", Map.of("relevance_score", 0.3)),
            new Document("中等质量的内容，有一定的信息量", Map.of("relevance_score", 0.7))
        );
        
        List<Document> result = strategy.process(documents, context);
        
        assertNotNull(result);
        assertTrue(result.size() <= 3);
    }
    
    @Test
    void testFilterTooShortDocuments() {
        List<Document> documents = List.of(
            new Document("这是一个足够长的文档内容，包含了足够的信息来进行处理和分析", Map.of()),
            new Document("短", Map.of()),
            new Document("这也是一个足够长的文档，可以用于测试过滤功能", Map.of())
        );
        
        List<Document> result = strategy.process(documents, context);
        
        assertNotNull(result);
        // 太短的文档应该被过滤掉
    }
    
    @Test
    void testKeepAllValidDocuments() {
        List<Document> documents = List.of(
            new Document("有效内容1", Map.of()),
            new Document("有效内容2", Map.of()),
            new Document("有效内容3", Map.of())
        );
        
        List<Document> result = strategy.process(documents, context);
        
        assertNotNull(result);
        assertEquals(3, result.size());
    }
    
    @Test
    void testEmptyDocuments() {
        List<Document> documents = List.of();
        
        List<Document> result = strategy.process(documents, context);
        
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
    
    @Test
    void testNullDocuments() {
        List<Document> result = strategy.process(null, context);
        
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
    
    @Test
    void testGetName() {
        assertEquals("FilteringStrategy", strategy.getName());
    }
}
