package org.example.core.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.core.metrics.RagMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CacheService 单元测试
 */
class CacheServiceTest {

    private RedisTemplate<String, String> redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private ObjectMapper objectMapper;
    private RagMetrics ragMetrics;
    private CacheService cacheService;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        objectMapper = new ObjectMapper();
        ragMetrics = mock(RagMetrics.class);
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        cacheService = new CacheService(redisTemplate, objectMapper, ragMetrics);
    }

    @Test
    void testCacheHit() {
        // 模拟缓存命中
        when(valueOperations.get(anyString())).thenReturn("\"cached_answer\"");
        
        String result = cacheService.getQaAnswer("user123", "test_question", String.class);
        
        assertNotNull(result);
        assertEquals("cached_answer", result);
        verify(ragMetrics).recordCacheHit();
    }

    @Test
    void testCacheMiss() {
        // 模拟缓存未命中
        when(valueOperations.get(anyString())).thenReturn(null);
        
        String result = cacheService.getQaAnswer("user123", "test_question", String.class);
        
        assertNull(result);
        verify(ragMetrics).recordCacheMiss();
    }

    @Test
    void testSaveAnswer() throws Exception {
        // 测试保存答案到缓存
        String testAnswer = "test_answer";
        cacheService.cacheQaAnswer("user123", "test_question", testAnswer);
        
        verify(valueOperations).set(anyString(), anyString(), eq(3600L), eq(TimeUnit.SECONDS));
        verify(ragMetrics, never()).recordCacheHit();
        verify(ragMetrics, never()).recordCacheMiss();
    }
}
