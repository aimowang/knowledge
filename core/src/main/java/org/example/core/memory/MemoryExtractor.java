package org.example.core.memory;

import lombok.extern.slf4j.Slf4j;
import org.example.model.ChatMessage;
import org.example.model.LongTermMemory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 记忆提取器
 * 使用 LLM 从对话中提取重要的长期记忆
 */
@Slf4j
@Component
public class MemoryExtractor {
    
    private final ChatClient chatClient;
    
    public MemoryExtractor(@Qualifier("fastChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }
    
    /**
     * 从对话历史中提取长期记忆
     * @param userId 用户ID
     * @param conversationHistory 对话历史
     * @return 提取的记忆列表
     */
    public List<LongTermMemory> extractMemories(String userId, List<ChatMessage> conversationHistory) {
        if (conversationHistory == null || conversationHistory.isEmpty()) {
            return List.of();
        }
        
        // 构建对话文本
        StringBuilder dialogText = new StringBuilder();
        for (ChatMessage msg : conversationHistory) {
            String role = "user".equals(msg.getRole()) ? "用户" : "助手";
            dialogText.append(role).append(": ").append(msg.getContent()).append("\n");
        }
        
        // 使用 LLM 提取记忆
        String extractionPrompt = buildExtractionPrompt(dialogText.toString());
        
        try {
            String result = chatClient.prompt()
                    .system("""
                        你是一个记忆提取专家。从对话中提取重要的、值得长期记住的信息。
                        
                        提取规则：
                        1. 只提取事实性信息、用户偏好、重要上下文
                        2. 忽略闲聊、临时性问题
                        3. 每条记忆应该简洁、明确
                        4. 为每条记忆生成关键词（用逗号分隔）
                        5. 评分重要性（1-10分）
                        
                        输出格式（每行一条）：
                        [类型] 内容 | 关键词 | 重要性
                        
                        类型：FACT（事实）/ PREFERENCE（偏好）/ CONTEXT（上下文）
                        """)
                    .user(extractionPrompt)
                    .call()
                    .content();
            
            if (result == null || result.isEmpty()) {
                return List.of();
            }
            
            // 解析 LLM 的输出
            return parseExtractionResult(userId, result);
            
        } catch (Exception e) {
            log.error("提取记忆失败", e);
            return List.of();
        }
    }
    
    /**
     * 构建提取提示词
     */
    private String buildExtractionPrompt(String dialogText) {
        return String.format("""
            请从以下对话中提取重要的长期记忆：
            
            %s
            
            注意：
            - 只提取真正重要的信息
            - 如果没有值得记忆的内容，返回空
            - 最多提取 5 条记忆
            """, dialogText);
    }
    
    /**
     * 解析 LLM 的提取结果
     */
    private List<LongTermMemory> parseExtractionResult(String userId, String result) {
        List<LongTermMemory> memories = new java.util.ArrayList<>();
        
        // 正则表达式匹配：[类型] 内容 | 关键词 | 重要性
        Pattern pattern = Pattern.compile("\\[(\\w+)\\]\\s+(.+?)\\s*\\|\\s*(.+?)\\s*\\|\\s*(\\d+)");
        Matcher matcher = pattern.matcher(result);
        
        while (matcher.find()) {
            try {
                String typeStr = matcher.group(1);
                String content = matcher.group(2).trim();
                String keywords = matcher.group(3).trim();
                int importance = Integer.parseInt(matcher.group(4).trim());
                
                // 转换类型
                LongTermMemory.MemoryType type;
                try {
                    type = LongTermMemory.MemoryType.valueOf(typeStr);
                } catch (IllegalArgumentException e) {
                    type = LongTermMemory.MemoryType.FACT;
                }
                
                // 创建记忆对象
                LongTermMemory memory = new LongTermMemory(userId, type, content, keywords);
                memory.setImportance(Math.min(10, Math.max(1, importance)));
                
                memories.add(memory);
                log.debug("提取记忆: {} - {}", type, content);
                
            } catch (Exception e) {
                log.warn("解析记忆条目失败: {}", matcher.group(0), e);
            }
        }
        
        return memories;
    }
    
    /**
     * 判断是否需要提取记忆（基于对话轮数）
     */
    public boolean shouldExtractMemories(int messageCount) {
        // 每 10 条消息提取一次记忆
        return messageCount > 0 && messageCount % 10 == 0;
    }
}
