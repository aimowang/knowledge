package org.example.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 长期记忆条目
 */
@Data
@Builder
@AllArgsConstructor
public class LongTermMemory {

    private String id;
    private String userId;
    private MemoryType type;
    private String content;

    /** 内容摘要（用于列表展示和渐进式加载，长度300字符） */
    private String summary;

    private String keywords;
    private Integer importance;
    private Integer accessCount;
    private LocalDateTime createdAt;
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
        this.summary = generateSummary(content);
        this.createdAt = LocalDateTime.now();
        this.lastAccessedAt = LocalDateTime.now();
        this.accessCount = 0;
        this.importance = 5;
    }

    public void incrementAccess() {
        this.accessCount++;
        this.lastAccessedAt = LocalDateTime.now();
    }

    /**
     * 从内容生成摘要（取前100字符）。
     */
    public static String generateSummary(String content) {
        if (content == null || content.isEmpty()) return "";
        return content.length() <= 100 ? content : content.substring(0, 97) + "...";
    }

    public enum MemoryType {
        FACT,           // 事实性知识
        PREFERENCE,     // 用户偏好
        CONTEXT         // 上下文信息
    }
}
