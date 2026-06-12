package org.example.core.rag.strategy.impl;

import org.example.core.compress.HybridCompressor;
import org.example.core.rag.context.RagContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * CompressionStrategy 单元测试
 */
class CompressionStrategyTest {
    
    private CompressionStrategy strategy;
    private RagContext context;
    private HybridCompressor mockCompressor;
    
    @BeforeEach
    void setUp() {
        mockCompressor = mock(HybridCompressor.class);
        strategy = new CompressionStrategy(mockCompressor);
        context = RagContext.builder()
            .originalQuestion("Spring Boot 是什么？")
            .userId("user123")
            .build();
    }
    
    @Test
    void testCompressLongDocuments() {
        String longContent = "Spring Boot是一个基于Java的开源框架，用于简化Spring应用的初始搭建和开发过程。" +
            "它提供了默认配置，使得开发者可以快速开始一个新的Spring项目。" +
            "Spring Boot内置了Tomcat服务器，不需要部署WAR文件。" +
            "它还提供了自动配置功能，根据classpath中的依赖自动配置Spring应用。" +
            "Spring Boot的目标是简化Spring应用的开发、运行和调试过程。";
        
        List<Document> documents = List.of(
            new Document(longContent, Map.of("source", "doc1"))
        );
        
        List<Document> result = strategy.process(documents, context);
        
        assertNotNull(result);
        assertEquals(1, result.size());
        // 压缩后的内容应该更短
        assertTrue(result.get(0).getText().length() <= longContent.length());
    }
    
    @Test
    void testKeepShortDocuments() {
        List<Document> documents = List.of(
            new Document("短内容", Map.of("source", "doc1"))
        );
        
        List<Document> result = strategy.process(documents, context);
        
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("短内容", result.get(0).getText());
    }
    
    @Test
    void testMultipleDocuments() {
        List<Document> documents = List.of(
            new Document("文档1内容", Map.of("source", "doc1")),
            new Document("文档2内容", Map.of("source", "doc2")),
            new Document("文档3内容", Map.of("source", "doc3"))
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
        assertEquals("CompressionStrategy", strategy.getName());
    }
}
