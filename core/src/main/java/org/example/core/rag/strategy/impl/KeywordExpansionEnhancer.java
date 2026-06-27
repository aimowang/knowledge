package org.example.core.rag.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.core.rag.context.RagContext;
import org.example.core.resilience.ResilienceHelper;
import org.example.core.rag.strategy.QueryEnhancementStrategy;
import org.example.model.enums.ComplexityLevelEnum;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 关键词扩展查询增强策略
 * 使用 LLM 提取核心关键词并补充同义词，提高召回率
 */
@Slf4j
@Component
public class KeywordExpansionEnhancer implements QueryEnhancementStrategy {
    
    private final ChatClient chatClient;
    private final ResilienceHelper resilienceHelper;
    
    public KeywordExpansionEnhancer(@Qualifier("fastChatClient") ChatClient chatClient, ResilienceHelper resilienceHelper) {
        this.chatClient = chatClient;
        this.resilienceHelper = resilienceHelper;
    }
    
    @Override
    public String enhance(String query, RagContext context) {
        try {
            log.debug("扩展查询关键词: {}", query);
            
            String systemPrompt = """
                你是一个关键词提取专家。从用户问题中提取核心关键词，并补充相关同义词。
                
                输出格式：原始问题 + 空格 + 关键词列表（用空格分隔）
                
                示例：
                输入：Spring Boot 的优势是什么？
                输出：Spring Boot 的优势是什么？ Spring Boot 优点 特性 好处 advantages features benefits
                
                输入：如何配置 MySQL 数据库？
                输出：如何配置 MySQL 数据库？ MySQL 配置 连接池 datasource 数据库设置
                
                只输出一行结果，不要解释。
                """;
            
            // 使用带容错的 LLM 调用
            String expandedQuery = callLlmWithResilience(systemPrompt + "\n\n" + query);
            
            if (expandedQuery != null && !expandedQuery.trim().isEmpty()) {
                // 取第一行，防止 LLM 输出多余内容
                String firstLine = expandedQuery.split("\n")[0].trim();
                if (firstLine.length() > query.length()) {
                    log.info("查询扩展: '{}' -> '{}'", query, firstLine);
                    return firstLine;
                }
            }
        } catch (Exception e) {
            log.warn("查询扩展失败，使用原查询: {}", e.getMessage());
        }
        
        return query;
    }
    
    @Override
    public boolean supports(ComplexityLevelEnum complexity) {
        // 中等和复杂问题支持关键词扩展
        return complexity == ComplexityLevelEnum.MODERATE 
            || complexity == ComplexityLevelEnum.COMPLEX;
    }
    
    @Override
    public String getName() {
        return "KeywordExpansionEnhancer";
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
