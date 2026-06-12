package org.example.core.rag.context;

import org.example.model.RetrievalConfig;
import org.example.model.enums.ComplexityLevelEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RagContext 单元测试
 */
class RagContextTest {
    
    private RagContext context;
    
    @BeforeEach
    void setUp() {
        context = RagContext.builder()
            .originalQuestion("Spring Boot 是什么？")
            .userId("user123")
            .source("docs")
            .complexity(ComplexityLevelEnum.MODERATE)
            .retrievalConfig(new RetrievalConfig(5, 0.7))
            .build();
    }
    
    @Test
    void testBuilder() {
        assertNotNull(context);
        assertEquals("Spring Boot 是什么？", context.getOriginalQuestion());
        assertEquals("user123", context.getUserId());
        assertEquals("docs", context.getSource());
        assertEquals(ComplexityLevelEnum.MODERATE, context.getComplexity());
        assertNotNull(context.getRetrievalConfig());
    }
    
    @Test
    void testStartTime() {
        long beforeTime = System.currentTimeMillis();
        context.setStartTime(beforeTime);
        
        assertEquals(beforeTime, context.getStartTime());
    }
    
    @Test
    void testRecordStageDuration() {
        context.recordStageDuration("RetrievalStage", 150L);
        context.recordStageDuration("GenerationStage", 800L);
        
        Map<String, Long> durations = context.getStageDurations();
        assertNotNull(durations);
        assertEquals(2, durations.size());
        assertEquals(150L, durations.get("RetrievalStage"));
        assertEquals(800L, durations.get("GenerationStage"));
    }
    
    @Test
    void testGetTotalDuration() {
        context.setStartTime(System.currentTimeMillis() - 1000);
        
        long totalDuration = context.getTotalDuration();
        assertTrue(totalDuration >= 1000);
        assertTrue(totalDuration < 1100); // 允许少量误差
    }
    
    @Test
    void testAddAndGetMetadata() {
        context.addMetadata("cacheHit", true);
        context.addMetadata("retryCount", 3);
        context.addMetadata("customKey", "customValue");
        
        assertTrue(context.getMetadata("cacheHit", Boolean.class));
        assertEquals(3, context.getMetadata("retryCount", Integer.class));
        assertEquals("customValue", context.getMetadata("customKey", String.class));
    }
    
    @Test
    void testGetNonExistentMetadata() {
        Object value = context.getMetadata("nonExistent", String.class);
        assertNull(value);
    }
    
    @Test
    void testSetCurrentQuery() {
        context.setCurrentQuery("Spring Boot framework definition");
        
        assertEquals("Spring Boot framework definition", context.getCurrentQuery());
    }
    
    @Test
    void testSetDocuments() {
        List<Document> docs = List.of(
            new Document("Document 1"),
            new Document("Document 2")
        );
        context.setDocuments(docs);
        
        assertNotNull(context.getDocuments());
        assertEquals(2, context.getDocuments().size());
    }
    
    @Test
    void testSetAnswer() {
        context.setAnswer("Spring Boot 是一个 Java 框架");
        context.setSources(List.of("source1", "source2"));
        
        assertEquals("Spring Boot 是一个 Java 框架", context.getAnswer());
        assertEquals(2, context.getSources().size());
    }
    
    @Test
    void testMemoryContext() {
        MemoryContext memoryContext = new MemoryContext(List.of(), List.of());
        context.setMemoryContext(memoryContext);
        
        assertNotNull(context.getMemoryContext());
        assertEquals(memoryContext, context.getMemoryContext());
    }
    
    @Test
    void testNullValues() {
        RagContext nullContext = RagContext.builder().build();
        
        assertNotNull(nullContext);
        assertNull(nullContext.getOriginalQuestion());
        assertNull(nullContext.getUserId());
        assertNotNull(nullContext.getStageDurations()); // 应该初始化为空Map
    }
}
