package org.example.core.rag.agentic.transform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Step-Back 查询 (FR-12.2) — 为过于具体的问题生成更宽泛的检索词。
 *
 * <p>当检索结果为空或得分低时触发，生成更宽泛的查询词以扩大检索范围。
 * 示例："Spring Boot 3.5 @EnableWebMvc 变更" → "Spring Boot 3.5 Web MVC 变更"
 */
public class StepBackQuery {

    private static final Logger log = LoggerFactory.getLogger(StepBackQuery.class);

    private static final String STEPBACK_PROMPT = """
        你的任务是将一个过于具体或细节导向的问题"后退一步"，生成一个更宽泛的检索查询。

        规则：
        - 移除具体的版本号、方法名、参数等细节
        - 保留核心主题和概念
        - 输出更通用、更宽泛的查询版本
        - 只返回一个退一步后的查询文本，不要附加说明

        原始查询: %s

        退一步查询:
        """;

    private final ChatClient chatClient;

    public StepBackQuery(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 生成 Step-Back 查询。
     *
     * @param originalQuery 原始查询（检索结果为空或得分低）
     * @param searchSummary 简短描述搜索结果状况，帮助 LLM 理解为何需要放宽
     * @return 更宽泛的查询，或原始查询（无法退一步时）
     */
    public String stepBack(String originalQuery, String searchSummary) {
        if (originalQuery == null || originalQuery.isBlank()) {
            return originalQuery;
        }

        try {
            String prompt = String.format(STEPBACK_PROMPT, originalQuery);
            if (searchSummary != null && !searchSummary.isBlank()) {
                prompt += "\n\n当前检索状况: " + searchSummary;
            }

            String broaderQuery = chatClient.prompt()
                .system("你是一个查询扩展专家。生成更宽泛的检索词，只返回文本，不要附加说明。")
                .user("原始查询: " + originalQuery
                    + "\n\n退一步，生成一个更宽泛的检索查询：")
                .call()
                .content();

            if (broaderQuery == null || broaderQuery.isBlank()) {
                return originalQuery;
            }

            String result = broaderQuery.trim();
            log.debug("Step-Back: '{}' → '{}'", originalQuery, result);
            return result;

        } catch (Exception e) {
            log.warn("Step-Back 查询异常: {}", e.getMessage());
            return originalQuery;
        }
    }
}
