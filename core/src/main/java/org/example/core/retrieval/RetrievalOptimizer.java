package org.example.core.retrieval;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 检索优化器
 * 提供查询扩展、重写等功能以提高检索质量
 */
@Slf4j
@Component
public class RetrievalOptimizer {
    
    private final ChatClient chatClient;
    
    public RetrievalOptimizer(@Qualifier("fastChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }
    
    /**
     * 查询扩展：生成多个相关查询以提高召回率
     */
    public List<String> expandQuery(String originalQuery) {
        List<String> queries = new ArrayList<>();
        queries.add(originalQuery);
        
        try {
            String prompt = String.format("""
                请为以下问题生成 3 个不同角度的相关查询，以提高信息检索的召回率。
                
                原始问题：%s
                
                要求：
                1. 保持原意不变
                2. 使用不同的表达方式
                3. 从不同角度提问
                4. 每行一个查询，不要编号
                
                示例：
                原始：Spring AI 是什么？
                扩展：
                Spring AI 的主要功能
                Spring AI 的应用场景
                Spring AI 的核心组件
                """, originalQuery);
            
            String result = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (result != null && !result.isEmpty()) {
                String[] lines = result.split("\n");
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.equals(originalQuery)) {
                        queries.add(trimmed);
                    }
                }
            }
            
            log.info("查询扩展完成: {} -> {} 个查询", originalQuery, queries.size());
            
        } catch (Exception e) {
            log.warn("查询扩展失败，使用原始查询", e);
        }
        
        return queries;
    }
    
    /**
     * 查询重写：优化查询表达以提高相关性
     */
    public String rewriteQuery(String originalQuery) {
        try {
            String prompt = String.format("""
                请优化以下问题的表达，使其更适合用于信息检索。
                
                原始问题：%s
                
                要求：
                1. 去除冗余词汇
                2. 突出关键信息
                3. 使用更专业的术语
                4. 只返回优化后的问题，不要有其他内容
                
                示例：
                原始：我想问一下 Spring AI 这个框架到底是用来做什么的啊？
                优化：Spring AI 框架的主要用途和功能
                """, originalQuery);
            
            String result = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (result != null && !result.isEmpty()) {
                String rewritten = result.trim();
                log.info("查询重写: {} -> {}", originalQuery, rewritten);
                return rewritten;
            }
            
        } catch (Exception e) {
            log.warn("查询重写失败，使用原始查询", e);
        }
        
        return originalQuery;
    }
    
    /**
     * 关键词提取：从查询中提取关键术语
     */
    public List<String> extractKeywords(String query) {
        try {
            String prompt = String.format("""
                请从以下问题中提取关键术语或关键词。
                
                问题：%s
                
                要求：
                1. 提取核心概念和技术术语
                2. 每行一个关键词
                3. 不要包含停用词（如：的、是、什么等）
                4. 最多提取 5 个关键词
                
                示例：
                问题：Spring AI 如何集成向量数据库？
                关键词：
                Spring AI
                向量数据库
                集成
                """, query);
            
            String result = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (result != null && !result.isEmpty()) {
                List<String> keywords = new ArrayList<>();
                String[] lines = result.split("\n");
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        keywords.add(trimmed);
                    }
                }
                log.info("关键词提取: {} -> {}", query, keywords);
                return keywords;
            }
            
        } catch (Exception e) {
            log.warn("关键词提取失败", e);
        }
        
        return List.of(query);
    }
}
