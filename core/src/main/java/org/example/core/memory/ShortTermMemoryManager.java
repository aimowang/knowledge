package org.example.core.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.example.model.ChatMessage;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 短期记忆管理器（Redis + 内存二级缓存）
 * 支持多用户隔离和分布式部署
 */
@Slf4j
@Component
public class ShortTermMemoryManager {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    
    /**
     * 用户ID -> 对话历史（内存缓存，加速读取）
     */
    private final Map<String, UserSession> userSessions = new ConcurrentHashMap<>();
    
    /**
     * 默认最大消息数（每个会话）
     */
    private static final int DEFAULT_MAX_MESSAGES = 20;
    
    /**
     * 会话过期时间（分钟）
     */
    private static final int SESSION_EXPIRE_MINUTES = 30;
    
    public ShortTermMemoryManager(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    /**
     * 获取或创建用户会话
     */
    public UserSession getOrCreateSession(String userId) {
        // 1. 先从内存缓存获取
        UserSession session = userSessions.get(userId);
        if (session != null && !session.isExpired(SESSION_EXPIRE_MINUTES)) {
            session.refreshLastAccessTime();
            return session;
        }
        
        // 2. 从 Redis 加载
        try {
            String redisKey = "session:" + userId;
            String json = redisTemplate.opsForValue().get(redisKey);
            if (json != null) {
                List<ChatMessage> messages = objectMapper.readValue(json, 
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ChatMessage.class));
                session = new UserSession(userId, DEFAULT_MAX_MESSAGES, messages);
                userSessions.put(userId, session);
                log.info("从 Redis 恢复用户会话: {}", userId);
                return session;
            }
        } catch (JsonProcessingException e) {
            log.error("从 Redis 加载会话失败: {}", userId, e);
        }
        
        // 3. 创建新会话
        session = new UserSession(userId, DEFAULT_MAX_MESSAGES);
        userSessions.put(userId, session);
        log.info("创建新用户会话: {}", userId);
        return session;
    }
    
    /**
     * 添加用户消息
     */
    public void addUserMessage(String userId, String message) {
        UserSession session = getOrCreateSession(userId);
        session.addMessage(new ChatMessage("user", message));
        saveSessionToRedis(userId, session);
        log.debug("用户 {} 添加消息，当前消息数: {}", userId, session.getMessages().size());
    }
    
    /**
     * 添加助手回复
     */
    public void addAssistantMessage(String userId, String message) {
        UserSession session = getOrCreateSession(userId);
        session.addMessage(new ChatMessage("assistant", message));
        saveSessionToRedis(userId, session);
        log.debug("助手为 {} 添加回复", userId);
    }
    
    /**
     * 获取用户的对话历史（用于构建上下文）
     */
    public List<ChatMessage> getConversationHistory(String userId) {
        UserSession session = userSessions.get(userId);
        if (session == null) {
            return List.of();
        }
        
        // 清理过期会话
        if (session.isExpired(SESSION_EXPIRE_MINUTES)) {
            log.info("用户 {} 会话已过期，清理中...", userId);
            userSessions.remove(userId);
            return List.of();
        }
        
        // 刷新最后访问时间
        session.refreshLastAccessTime();
        
        return session.getMessages();
    }
    
    /**
     * 获取短期记忆历史（别名方法，与 getConversationHistory 相同）
     */
    public List<ChatMessage> getHistory(String userId) {
        return getConversationHistory(userId);
    }
    
    /**
     * 保存消息（通用方法）
     */
    public void saveMessage(String userId, String role, String message) {
        if ("user".equals(role)) {
            addUserMessage(userId, message);
        } else if ("assistant".equals(role)) {
            addAssistantMessage(userId, message);
        } else {
            log.warn("未知的角色: {}", role);
        }
    }
    
    /**
     * 清空用户会话
     */
    public void clearSession(String userId) {
        userSessions.remove(userId);
        redisTemplate.delete("session:" + userId);
        log.info("已清空用户 {} 的会话", userId);
    }
    
    /**
     * 保存会话到 Redis
     */
    private void saveSessionToRedis(String userId, UserSession session) {
        try {
            String redisKey = "session:" + userId;
            String json = objectMapper.writeValueAsString(session.getMessages());
            redisTemplate.opsForValue().set(redisKey, json, SESSION_EXPIRE_MINUTES, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            log.error("保存会话到 Redis 失败: {}", userId, e);
        }
    }
    
    /**
     * 获取活跃会话数
     */
    public int getActiveSessionCount() {
        return userSessions.size();
    }
    
    /**
     * 用户会话内部类
     */
    @Slf4j
    public static class UserSession {
        private final String userId;
        private final List<ChatMessage> messages;
        private final int maxMessages;
        private LocalDateTime lastAccessTime;
        
        public UserSession(String userId, int maxMessages) {
            this.userId = userId;
            this.maxMessages = maxMessages;
            this.messages = new java.util.ArrayList<>();
            this.lastAccessTime = LocalDateTime.now();
        }
        
        /**
         * 从 Redis 加载的构造函数
         */
        public UserSession(String userId, int maxMessages, List<ChatMessage> loadedMessages) {
            this.userId = userId;
            this.maxMessages = maxMessages;
            this.messages = new java.util.ArrayList<>(loadedMessages);
            this.lastAccessTime = LocalDateTime.now();
        }
        
        /**
         * 添加消息（自动维护最大数量）
         */
        public synchronized void addMessage(ChatMessage message) {
            messages.add(message);
            
            // 如果超过最大数量，删除最早的消息
            while (messages.size() > maxMessages) {
                messages.remove(0);
                log.debug("会话 {} 超出最大消息数，删除最早消息", userId);
            }
            
            refreshLastAccessTime();
        }
        
        /**
         * 检查会话是否过期
         */
        public boolean isExpired(int expireMinutes) {
            return lastAccessTime.plusMinutes(expireMinutes).isBefore(LocalDateTime.now());
        }
        
        /**
         * 刷新最后访问时间
         */
        public void refreshLastAccessTime() {
            this.lastAccessTime = LocalDateTime.now();
        }
        
        public String getUserId() {
            return userId;
        }
        
        public List<ChatMessage> getMessages() {
            return List.copyOf(messages);  // 返回不可变副本
        }
        
        public int getMaxMessages() {
            return maxMessages;
        }
    }
}
