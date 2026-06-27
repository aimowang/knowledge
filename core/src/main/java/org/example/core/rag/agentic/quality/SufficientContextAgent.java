package org.example.core.rag.agentic.quality;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 上下文完备性检查代理 (FR-8) — 基于 LLM 判断检索材料是否足以回答问题。
 *
 * <p>执行流程：
 * <ol>
 *   <li><b>检索片段检查</b> (FR-8.1)：LLM 判断文档是否包含回答所需的关键信息</li>
 *   <li><b>缺失分析</b> (FR-8.3)：如果不够，具体说明缺什么 + 建议重检索词</li>
 *   <li><b>循环控制</b> (FR-8.4)：最多 3 轮，连续 2 轮无改善提前终止</li>
 * </ol>
 */
public class SufficientContextAgent {

    private static final Logger log = LoggerFactory.getLogger(SufficientContextAgent.class);

    private static final String CHECK_PROMPT = """
        你是一个上下文完备性检查专家。判断以下检索到的信息是否足以回答用户问题。

        用户问题: %s

        检索到的信息:
        %s

        请严格判断：
        1. 这些信息是否覆盖了回答问题所需的所有关键要点？
        2. 是否存在重要信息缺失？

        以 JSON 格式返回，不要包含其他内容：
        {
          "sufficient": true/false,
          "reason": "简要说明判断理由",
          "missing_info": "如果 insufficient，描述缺失了什么具体信息",
          "suggested_query": "如果 insufficient，建议一个补充搜索词"
        }
        """;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatClient chatClient;

    public SufficientContextAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 检查上下文完备性。
     *
     * @param query   原始用户查询
     * @param context 检索到的上下文文本
     * @return 完备性判定结果
     */
    public ContextVerdict check(String query, String context) {
        // 空文档直接判定不完备
        if (context == null || context.isBlank()) {
            log.debug("上下文为空，标记为不完备");
            return ContextVerdict.insufficient("未检索到任何文档", query);
        }

        try {
            String prompt = String.format(CHECK_PROMPT, query, truncate(context, 3000));
            String response = chatClient.prompt()
                .system("你是一个严格的上下文完备性检查器。只返回 JSON，不要附加说明。")
                .user(prompt)
                .call()
                .content();

            if (response == null) {
                log.warn("LLM 返回为空，保守判定为完备");
                return ContextVerdict.sufficient();
            }

            return parseResponse(response);

        } catch (Exception e) {
            log.warn("LLM 完备性检查异常: {}，保守判定为完备", e.getMessage());
            return ContextVerdict.sufficient();
        }
    }

    /**
     * 解析 LLM 返回的 JSON 响应。
     */
    @SuppressWarnings("unchecked")
    private ContextVerdict parseResponse(String response) {
        try {
            // 提取 JSON（处理 LLM 有时会附加 Markdown 代码块标记）
            String json = extractJson(response);
            Map<String, Object> result = objectMapper.readValue(json, LinkedHashMap.class);

            boolean sufficient = Boolean.TRUE.equals(result.get("sufficient"));

            if (sufficient) {
                log.debug("完备性检查: 完备");
                return ContextVerdict.sufficient();
            }

            String missingInfo = (String) result.getOrDefault("missing_info", "信息不足");
            String suggestedQuery = (String) result.getOrDefault("suggested_query", "");
            log.debug("完备性检查: 不完备 - {}", missingInfo);

            return ContextVerdict.insufficient(missingInfo, suggestedQuery);

        } catch (JsonProcessingException e) {
            log.warn("解析完备性检查结果失败: {}", e.getMessage());
            return ContextVerdict.sufficient();
        }
    }

    /**
     * 从 LLM 响应中提取 JSON 部分。
     */
    private String extractJson(String text) {
        // 去掉 Markdown 代码块标记
        text = text.trim();
        if (text.startsWith("```")) {
            int start = text.indexOf('\n');
            int end = text.lastIndexOf("```");
            if (start > 0 && end > start) {
                text = text.substring(start, end).trim();
            }
        }
        // 找到第一个 { 和最后一个 }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
