package org.example.core.rag.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.core.rag.context.RagContext;
import org.example.core.resilience.ResilienceHelper;
import org.example.core.rag.strategy.QueryEnhancementStrategy;
import org.example.model.ChatMessage;
import org.example.model.enums.ComplexityLevelEnum;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于记忆的查询增强策略
 * 利用短期记忆进行指代消解和上下文补充
 */
@Slf4j
@Component
public class MemoryBasedQueryEnhancer implements QueryEnhancementStrategy {
    
    private final ChatClient chatClient;
    private final ResilienceHelper resilienceHelper;
    
    public MemoryBasedQueryEnhancer(ChatClient chatClient, ResilienceHelper resilienceHelper) {
        this.chatClient = chatClient;
        this.resilienceHelper = resilienceHelper;
    }
    
    @Override
    public String enhance(String query, RagContext context) {
        // 如果没有短期记忆，直接返回原查询
        if (!context.getMemoryContext().hasShortTermHistory()) {
            log.debug("无短期记忆，跳过指代消解");
            return query;
        }
        
        try {
            log.debug("使用短期记忆增强查询: {}", query);
            
            String systemPrompt = """
                你是一个查询优化专家。根据对话历史，完善用户当前问题的指代和上下文。
                
                规则：
                1. 如果当前问题包含指代词（如"它"、"这个"、"前者"等），用对话历史中的具体内容替换
                2. 如果当前问题省略了主语或关键信息，从对话历史中补充
                3. 如果当前问题是完整的，直接返回原问题
                4. 只返回优化后的问题，不要有其他内容
                """;
            
            // 获取最近5条消息
            List<ChatMessage> recentHistory = context.getMemoryContext()
                .getRecentShortTermHistory(5);
            
            StringBuilder historyBuilder = new StringBuilder();
            for (ChatMessage msg : recentHistory) {
                historyBuilder.append(msg.getRole()).append(": ")
                    .append(msg.getContent()).append("\n");
            }
            
            String userPrompt = String.format("""
                对话历史：
                %s
                
                当前问题：%s
                
                优化后的问题：
                """, historyBuilder.toString(), query);
            
            // 使用带容错的 LLM 调用
            String enhancedQuery = callLlmWithResilience(systemPrompt + "\n\n" + userPrompt);
            
            if (enhancedQuery != null && !enhancedQuery.trim().isEmpty()) {
                String result = enhancedQuery.trim();
                log.info("查询增强: '{}' -> '{}'", query, result);
                return result;
            }
        } catch (Exception e) {
            log.warn("查询增强失败，使用原查询: {}", e.getMessage());
        }
        
        return query;
    }
    
    @Override
    public boolean supports(ComplexityLevelEnum complexity) {
        // 所有复杂度都支持基于记忆的增强
        return true;
    }
    
    @Override
    public String getName() {
        return "MemoryBasedQueryEnhancer";
    }
    
    /**
     * 带容错的 LLM 调用
     */
    private String callLlmWithResilience(String prompt) {
        if (resilienceHelper != null) {
            return resilienceHelper.executeWithLlmResilience(
                () -> chatClient.prompt(prompt).call().content(),
                () -> {
                    log.warn("LLM 调用失败，返回空字符串");
                    return "";
                }
            );
        } else {
            log.warn("ResilienceHelper 未配置，直接调用 LLM");
            return chatClient.prompt(prompt).call().content();
        }
    }
}
