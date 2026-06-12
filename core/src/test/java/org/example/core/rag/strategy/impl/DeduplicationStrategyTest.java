package org.example.core.rag.strategy.impl;

import org.example.core.rag.context.RagContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DeduplicationStrategy 单元测试
 */
class DeduplicationStrategyTest {
    
    private DeduplicationStrategy strategy;
    private RagContext context;
    
    @BeforeEach
    void setUp() {
        strategy = new DeduplicationStrategy();
        context = RagContext.builder()
            .originalQuestion("test question")
            .userId("user123")
            .build();
    }
    
    @Test
    void testRemoveExactDuplicates() {
        List<Document> documents = List.of(
            new Document("相同内容", Map.of("source", "doc1")),
            new Document("相同内容", Map.of("source", "doc2")),
            new Document("不同内容", Map.of("source", "doc3"))
        );
        
        List<Document> result = strategy.process(documents, context);
        
        assertNotNull(result);
        assertEquals(2, result.size()); // 应该去重后只剩2个
    }
    
    @Test
    void testRemoveSimilarDocuments() {
        String content1 = "Spring Boot是一个基于Java的开源框架，用于简化Spring应用的初始搭建和开发过程";
        String content2 = "Spring Boot是一个基于Java的开源框架，用于简化Spring应用的初始搭建和开发过程。它提供了默认配置";
        
        List<Document> documents = List.of(
            new Document(content1, Map.of("source", "doc1")),
            new Document(content2, Map.of("source", "doc2")),
            new Document("完全不同的内容", Map.of("source", "doc3"))
        );
        
        List<Document> result = strategy.process(documents, context);
        
        assertNotNull(result);
        assertTrue(result.size() <= 2); // 相似文档应该被去重
    }
    
    @Test
    void testNoDuplicates() {
        List<Document> documents = List.of(
            new Document("内容1", Map.of("source", "doc1")),
            new Document("内容2", Map.of("source", "doc2")),
            new Document("内容3", Map.of("source", "doc3"))
        );
        
        List<Document> result = strategy.process(documents, context);
        
        assertNotNull(result);
        assertEquals(3, result.size()); // 没有重复，应该全部保留
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
    void testSingleDocument() {
        List<Document> documents = List.of(
            new Document("唯一内容", Map.of("source", "doc1"))
        );
        
        List<Document> result = strategy.process(documents, context);
        
        assertNotNull(result);
        assertEquals(1, result.size());
    }
    
    @Test
    void testGetName() {
        assertEquals("DeduplicationStrategy", strategy.getName());
    }
}
