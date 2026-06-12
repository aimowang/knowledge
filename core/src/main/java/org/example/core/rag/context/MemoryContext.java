package org.example.core.rag.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.model.ChatMessage;
import org.example.model.LongTermMemory;

import java.util.List;

/**
 * 记忆上下文 - 封装短期和长期记忆
 * 从 AbstractBasicRag 提取
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryContext {
    
    /**
     * 短期记忆（对话历史）
     */
    private List<ChatMessage> shortTermHistory;
    
    /**
     * 长期记忆（用户偏好、事实）
     */
    private List<LongTermMemory> longTermMemories;
    
    /**
     * 空记忆上下文
     */
    public static final MemoryContext EMPTY = new MemoryContext(List.of(), List.of());
    
    /**
     * 是否有短期记忆
     */
    public boolean hasShortTermHistory() {
        return shortTermHistory != null && !shortTermHistory.isEmpty();
    }
    
    /**
     * 是否有长期记忆
     */
    public boolean hasLongTermMemories() {
        return longTermMemories != null && !longTermMemories.isEmpty();
    }
    
    /**
     * 获取最近的 N 条短期记忆
     */
    public List<ChatMessage> getRecentShortTermHistory(int n) {
        if (shortTermHistory == null || shortTermHistory.isEmpty()) {
            return List.of();
        }
        
        int size = shortTermHistory.size();
        int fromIndex = Math.max(0, size - n);
        return shortTermHistory.subList(fromIndex, size);
    }
}
