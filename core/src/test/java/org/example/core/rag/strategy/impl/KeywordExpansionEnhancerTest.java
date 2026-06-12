package org.example.core.rag.strategy.impl;

import org.example.core.rag.context.RagContext;
import org.example.core.resilience.ResilienceHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * KeywordExpansionEnhancer 单元测试
 */
class KeywordExpansionEnhancerTest {
    
    private KeywordExpansionEnhancer enhancer;
    private RagContext context;
    private ChatClient mockChatClient;
    private ResilienceHelper mockResilienceHelper;
    
    @BeforeEach
    void setUp() {
        mockChatClient = mock(ChatClient.class);
        mockResilienceHelper = mock(ResilienceHelper.class);
        enhancer = new KeywordExpansionEnhancer(mockChatClient, mockResilienceHelper);
        context = RagContext.builder()
            .originalQuestion("Spring Boot")
            .userId("user123")
            .build();
    }
    
    @Test
    void testExpandKeywords() {
        String expanded = enhancer.enhance("Spring Boot", context);
        
        assertNotNull(expanded);
        // 应该包含原始关键词和扩展词
        assertTrue(expanded.contains("Spring Boot"));
    }
    
    @Test
    void testExpandMultipleKeywords() {
        String expanded = enhancer.enhance("Java Spring 框架", context);
        
        assertNotNull(expanded);
        assertTrue(expanded.length() >= "Java Spring 框架".length());
    }
    
    @Test
    void testEmptyQuery() {
        String expanded = enhancer.enhance("", context);
        
        assertNotNull(expanded);
        assertEquals("", expanded);
    }
    
    @Test
    void testGetName() {
        assertEquals("KeywordExpansionEnhancer", enhancer.getName());
    }
    
    @Test
    void testSupportsAllComplexity() {
        assertTrue(enhancer.supports(null));
    }
}
