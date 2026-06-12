package org.example.core.rag.pipeline.stage;

import org.example.core.rag.context.RagContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QueryCleaningStage 单元测试
 */
class QueryCleaningStageTest {
    
    private QueryCleaningStage stage;
    private RagContext context;
    
    @BeforeEach
    void setUp() {
        stage = new QueryCleaningStage();
    }
    
    @Test
    void testPreprocessQueryWithSpaces() {
        context = RagContext.builder()
            .originalQuestion("  Spring   Boot  是什么？  ")
            .userId("user123")
            .build();
        
        stage.process(context);
        
        assertNotNull(context.getCurrentQuery());
        assertEquals("Spring Boot 是什么？", context.getCurrentQuery());
    }
    
    @Test
    void testPreprocessQueryWithNewlines() {
        context = RagContext.builder()
            .originalQuestion("Spring\nBoot\n是什么？")
            .userId("user123")
            .build();
        
        stage.process(context);
        
        assertNotNull(context.getCurrentQuery());
        assertTrue(context.getCurrentQuery().contains("Spring"));
    }
    
    @Test
    void testPreprocessNormalQuery() {
        context = RagContext.builder()
            .originalQuestion("Java是什么？")
            .userId("user123")
            .build();
        
        stage.process(context);
        
        assertNotNull(context.getCurrentQuery());
        assertEquals("Java是什么？", context.getCurrentQuery());
    }
    
    @Test
    void testGetName() {
        assertEquals("QueryCleaningStage", stage.getName());
    }
    
    @Test
    void testShouldNotSkip() {
        context = RagContext.builder()
            .originalQuestion("test")
            .build();
        
        assertFalse(stage.shouldSkip(context));
    }
}
