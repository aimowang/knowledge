package org.example.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 长期记忆条目
 */
@Data
public class LongTermMemory {
    
    /**
     * 记忆ID
     */
    private String id;
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 记忆类型：FACT（事实）/ PREFERENCE（偏好）/ CONTEXT（上下文）
     */
    private MemoryType type;
    
    /**
     * 记忆内容
     */
    private String content;
    
    /**
     * 关键词（用于检索）
     */
    private String keywords;
    
    /**
     * 重要性评分（1-10）
     */
    private Integer importance;
    
    /**
     * 访问次数
     */
    private Integer accessCount;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 最后访问时间
     */
    private LocalDateTime lastAccessedAt;
    
    public LongTermMemory() {
        this.createdAt = LocalDateTime.now();
        this.lastAccessedAt = LocalDateTime.now();
        this.accessCount = 0;
        this.importance = 5;
    }
    
    public LongTermMemory(String userId, MemoryType type, String content, String keywords) {
        this.userId = userId;
        this.type = type;
        this.content = content;
        this.keywords = keywords;
        this.createdAt = LocalDateTime.now();
        this.lastAccessedAt = LocalDateTime.now();
        this.accessCount = 0;
        this.importance = 5;
    }
    
    /**
     * 增加访问次数
     */
    public void incrementAccess() {
        this.accessCount++;
        this.lastAccessedAt = LocalDateTime.now();
    }
    
    /**
     * 记忆类型枚举
     */
    public enum MemoryType {
        FACT,           // 事实性知识（如：用户使用的是 Java 17）
        PREFERENCE,     // 用户偏好（如：喜欢简洁的回答）
        CONTEXT         // 上下文信息（如：正在进行的项目）
    }
}
