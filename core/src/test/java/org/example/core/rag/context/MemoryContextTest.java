package org.example.core.rag.context;

import org.example.model.ChatMessage;
import org.example.model.LongTermMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MemoryContext 单元测试
 */
class MemoryContextTest {
    
    private MemoryContext context;
    private List<ChatMessage> shortTermHistory;
    private List<LongTermMemory> longTermMemories;
    
    @BeforeEach
    void setUp() {
        shortTermHistory = List.of(
            new ChatMessage("user", "你好"),
            new ChatMessage("assistant", "你好！有什么可以帮助你的？")
        );
        
        longTermMemories = List.of(
            LongTermMemory.builder()
                .userId("user123")
                .content("用户喜欢Java编程")
                .build()
        );
    }
    
    @Test
    void testConstructor() {
        context = new MemoryContext(shortTermHistory, longTermMemories);
        
        assertNotNull(context);
        assertEquals(2, context.getShortTermHistory().size());
        assertEquals(1, context.getLongTermMemories().size());
    }
    
    @Test
    void testEmptyContext() {
        context = MemoryContext.EMPTY;
        
        assertNotNull(context);
        assertTrue(context.getShortTermHistory().isEmpty());
        assertTrue(context.getLongTermMemories().isEmpty());
    }
    
    @Test
    void testGetShortTermHistory() {
        context = new MemoryContext(shortTermHistory, longTermMemories);
        
        List<ChatMessage> history = context.getShortTermHistory();
        
        assertNotNull(history);
        assertEquals(2, history.size());
        assertEquals("user", history.get(0).getRole());
        assertEquals("你好", history.get(0).getContent());
    }
    
    @Test
    void testGetLongTermMemories() {
        context = new MemoryContext(shortTermHistory, longTermMemories);
        
        List<LongTermMemory> memories = context.getLongTermMemories();
        
        assertNotNull(memories);
        assertEquals(1, memories.size());
        assertEquals("user123", memories.get(0).getUserId());
        assertEquals("用户喜欢Java编程", memories.get(0).getContent());
    }
    
    @Test
    void testNullValues() {
        context = new MemoryContext(null, null);
        
        assertNotNull(context.getShortTermHistory());
        assertNotNull(context.getLongTermMemories());
        assertTrue(context.getShortTermHistory().isEmpty());
        assertTrue(context.getLongTermMemories().isEmpty());
    }
}
