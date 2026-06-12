package org.example.core.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.core.metrics.RagMetrics;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Redis 缓存服务
 * 用于缓存高频 Query 答案和短期会话
 */
@Slf4j
@Service
public class CacheService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final RagMetrics ragMetrics;

    // Key 前缀
    private static final String QA_CACHE_PREFIX = "qa:answer:";
    private static final String SESSION_CACHE_PREFIX = "session:messages:";
    
    // 默认过期时间
    private static final long QA_CACHE_TTL = 3600; // 1小时
    private static final long SESSION_CACHE_TTL = 1800; // 30分钟

    public CacheService(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper, RagMetrics ragMetrics) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ragMetrics = ragMetrics;
    }

    /**
     * 缓存问答结果
     * @param userId 用户ID
     * @param question 问题（作为缓存键的一部分）
     * @param answer 答案对象
     */
    public void cacheQaAnswer(String userId, String question, Object answer) {
        try {
            String key = QA_CACHE_PREFIX + userId + ":" + hashQuestion(question);
            String value = objectMapper.writeValueAsString(answer);
            redisTemplate.opsForValue().set(key, value, QA_CACHE_TTL, TimeUnit.SECONDS);
            log.debug("缓存问答结果: key={}", key);
        } catch (JsonProcessingException e) {
            log.error("序列化问答结果失败", e);
        }
    }

    /**
     * 获取缓存的问答结果
     * @param userId 用户ID
     * @param question 问题
     * @param clazz 返回类型
     * @return 缓存的答案，如果不存在则返回 null
     */
    public <T> T getQaAnswer(String userId, String question, Class<T> clazz) {
        try {
            String key = QA_CACHE_PREFIX + userId + ":" + hashQuestion(question);
            String value = redisTemplate.opsForValue().get(key);
            
            if (value == null) {
                ragMetrics.recordCacheMiss();
                return null;
            }
            
            ragMetrics.recordCacheHit();
            T result = objectMapper.readValue(value, clazz);
            log.debug("命中问答缓存: key={}", key);
            return result;
        } catch (JsonProcessingException e) {
            log.error("反序列化问答结果失败", e);
            return null;
        }
    }

    /**
     * 缓存用户会话消息
     * @param userId 用户ID
     * @param messages 消息列表（JSON字符串）
     */
    public void cacheSessionMessages(String userId, String messages) {
        String key = SESSION_CACHE_PREFIX + userId;
        redisTemplate.opsForValue().set(key, messages, SESSION_CACHE_TTL, TimeUnit.SECONDS);
        log.debug("缓存会话消息: userId={}, messageCount={}", userId, messages.length());
    }

    /**
     * 获取缓存的会话消息
     * @param userId 用户ID
     * @return 消息列表 JSON 字符串
     */
    public String getSessionMessages(String userId) {
        String key = SESSION_CACHE_PREFIX + userId;
        String messages = redisTemplate.opsForValue().get(key);
        
        if (messages != null) {
            log.debug("命中会话缓存: userId={}", userId);
        }
        
        return messages;
    }

    /**
     * 删除缓存的问答结果
     * @param userId 用户ID
     * @param question 问题
     */
    public void evictQaAnswer(String userId, String question) {
        String key = QA_CACHE_PREFIX + userId + ":" + hashQuestion(question);
        Boolean deleted = redisTemplate.delete(key);
        log.debug("删除问答缓存: key={}, deleted={}", key, deleted);
    }

    /**
     * 清除用户所有缓存
     * @param userId 用户ID
     */
    public void clearUserCache(String userId) {
        // 删除问答缓存
        String qaPattern = QA_CACHE_PREFIX + userId + ":*";
        redisTemplate.keys(qaPattern).forEach(redisTemplate::delete);
        
        // 删除会话缓存
        String sessionKey = SESSION_CACHE_PREFIX + userId;
        redisTemplate.delete(sessionKey);
        
        log.info("已清除用户 {} 的所有缓存", userId);
    }

    /**
     * 对问题进行简单哈希，避免 Redis Key 过长
     */
    private String hashQuestion(String question) {
        // 取前50个字符 + hashCode
        String prefix = question.length() > 50 ? question.substring(0, 50) : question;
        int hash = Math.abs(question.hashCode());
        return prefix.replaceAll("[^a-zA-Z0-9]", "_") + "_" + hash;
    }
}
