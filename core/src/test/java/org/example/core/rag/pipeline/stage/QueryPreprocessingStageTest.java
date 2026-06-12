package org.example.core.rag.pipeline.stage;

import org.example.core.rag.context.RagContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QueryPreprocessingStage 单元测试
 */
class QueryPreprocessingStageTest {
    
    private QueryPreprocessingStage stage;
    private RagContext context;
    
    @BeforeEach
    void setUp() {
        stage = new QueryPreprocessingStage();
    }
    
    @Test
    void testPreprocessQueryWithSpaces() {
        context = RagContext.builder()
            .originalQuestion("  Spring   Boot  是什么？  ")
            .userId("user123")
            .build();
        
        stage.process(context);
        
        assertNotNull(context.getPreprocessedQuery());
        assertEquals("Spring Boot 是什么？", context.getPreprocessedQuery());
    }
    
    @Test
    void testPreprocessQueryWithNewlines() {
        context = RagContext.builder()
            .originalQuestion("Spring\nBoot\n是什么？")
            .userId("user123")
            .build();
        
        stage.process(context);
        
        assertNotNull(context.getPreprocessedQuery());
        assertTrue(context.getPreprocessedQuery().contains("Spring"));
    }
    
    @Test
    void testPreprocessNormalQuery() {
        context = RagContext.builder()
            .originalQuestion("Java是什么？")
            .userId("user123")
            .build();
        
        stage.process(context);
        
        assertNotNull(context.getPreprocessedQuery());
        assertEquals("Java是什么？", context.getPreprocessedQuery());
    }
    
    @Test
    void testGetName() {
        assertEquals("QueryPreprocessingStage", stage.getName());
    }
    
    @Test
    void testShouldNotSkip() {
        context = RagContext.builder()
            .originalQuestion("test")
            .build();
        
        assertFalse(stage.shouldSkip(context));
    }
}
