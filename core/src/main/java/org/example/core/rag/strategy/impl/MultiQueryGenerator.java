package org.example.core.rag.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.core.rag.context.RagContext;
import org.example.core.resilience.ResilienceHelper;
import org.example.model.enums.ComplexityLevelEnum;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 多查询生成器
 * 基于原始问题生成多个角度的变体问题，提升召回率
 */
@Slf4j
@Component
public class MultiQueryGenerator {
    
    private final ChatClient chatClient;
    private final ResilienceHelper resilienceHelper;
    
    public MultiQueryGenerator(@Qualifier("fastChatClient") ChatClient chatClient, ResilienceHelper resilienceHelper) {
        this.chatClient = chatClient;
        this.resilienceHelper = resilienceHelper;
    }
    
    /**
     * 生成多查询
     * @param originalQuery 原始查询
     * @param context RAG 执行上下文
     * @return 多查询列表
     */
    public List<String> generate(String originalQuery, RagContext context) {
        // 只对复杂问题生成多查询
        if (context.getComplexity() != ComplexityLevelEnum.COMPLEX) {
            log.debug("非复杂问题，跳过多查询生成");
            return List.of();
        }
        
        try {
            log.info("生成多查询以提升召回率: {}", originalQuery);
            
            String systemPrompt = """
                你是一个查询扩展专家。基于用户的原始问题，生成 3-5 个不同角度的变体问题。
                这些变体应该：
                1. 保持原问题的核心意图
                2. 使用不同的措辞和表达方式
                3. 从不同角度或粒度提问
                4. 有助于检索更全面的信息
                
                只返回问题列表，每行一个，不要编号或其他内容。
                """;
            
            String userPrompt = "原始问题：" + originalQuery;
            
            // 使用带容错的 LLM 调用
            String response = callLlmWithResilience(systemPrompt + "\n\n" + userPrompt);
            
            if (response == null || response.trim().isEmpty()) {
                return List.of();
            }
            
            List<String> queries = Arrays.stream(response.split("\n"))
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && line.length() > 5)
                    .limit(5)
                    .collect(Collectors.toList());
            
            log.info("生成了 {} 个变体查询", queries.size());
            return queries;
            
        } catch (Exception e) {
            log.error("多查询生成失败，返回空列表", e);
            return List.of();
        }
    }
    
    /**
     * 是否应该使用多查询
     * @param complexity 复杂度级别
     * @return true 表示应该使用
     */
    public boolean shouldUseMultiQuery(ComplexityLevelEnum complexity) {
        return complexity == ComplexityLevelEnum.COMPLEX;
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
