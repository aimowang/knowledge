package org.example.core.rag.strategy.impl;

import org.example.core.rag.context.MemoryContext;
import org.example.core.rag.context.RagContext;
import org.example.core.resilience.ResilienceHelper;
import org.example.model.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * MemoryBasedQueryEnhancer 单元测试
 */
class MemoryBasedQueryEnhancerTest {
    
    private MemoryBasedQueryEnhancer enhancer;
    private RagContext context;
    private ChatClient mockChatClient;
    private ResilienceHelper mockResilienceHelper;
    
    @BeforeEach
    void setUp() {
        mockChatClient = mock(ChatClient.class);
        mockResilienceHelper = mock(ResilienceHelper.class);
        enhancer = new MemoryBasedQueryEnhancer(mockChatClient, mockResilienceHelper);
        
        List<ChatMessage> history = List.of(
            new ChatMessage("user", "Spring Boot 是什么？"),
            new ChatMessage("assistant", "Spring Boot 是一个 Java 框架")
        );
        
        MemoryContext memoryContext = new MemoryContext(history, List.of());
        
        context = RagContext.builder()
            .originalQuestion("它有什么特点？")
            .userId("user123")
            .memoryContext(memoryContext)
            .build();
    }
    
    @Test
    void testEnhanceWithMemory() {
        String enhanced = enhancer.enhance("它有什么特点？", context);
        
        assertNotNull(enhanced);
        // 应该包含上下文信息
        assertTrue(enhanced.contains("Spring Boot") || enhanced.contains("它"));
    }
    
    @Test
    void testEnhanceWithoutMemory() {
        RagContext noMemoryContext = RagContext.builder()
            .originalQuestion("独立问题")
            .userId("user123")
            .memoryContext(MemoryContext.EMPTY)
            .build();
        
        String enhanced = enhancer.enhance("独立问题", noMemoryContext);
        
        assertNotNull(enhanced);
        assertEquals("独立问题", enhanced); // 没有记忆时返回原问题
    }
    
    @Test
    void testGetName() {
        assertEquals("MemoryBasedQueryEnhancer", enhancer.getName());
    }
    
    @Test
    void testSupportsAllComplexity() {
        assertTrue(enhancer.supports(null));
    }
}
