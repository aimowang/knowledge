package org.example.core.rag.agentic.transform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 查询分解器 (FR-12.1) — 将复合问题拆解为原子子问题。
 *
 * <p>使用 LLM 识别复合问题的原子子问题及其依赖关系，生成子查询 DAG。
 * 配合 FR-12.3 Multi-Query 为每个子查询生成同义变体。
 */
public class QueryDecomposer {

    private static final Logger log = LoggerFactory.getLogger(QueryDecomposer.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String DECOMPOSE_PROMPT = """
        将以下复杂问题拆解为原子子问题。

        要求：
        1. 每个子问题必须是可独立检索的原子问题
        2. 如果子问题 B 依赖于子问题 A 的结果，在 dependsOn 中标注
        3. 为每个子问题生成 2 个同义变体（不同措辞）
        4. 如果是简单事实性问题，只返回一个子问题

        以 JSON 格式返回：
        {
          "sub_queries": [
            {
              "id": "sq1",
              "query": "原子子问题文本",
              "depends_on": [],
              "variants": ["同义变体1", "同义变体2"]
            }
          ]
        }
        """;

    private final ChatClient chatClient;

    public QueryDecomposer(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 分解查询。
     *
     * @param query 用户原始查询
     * @return 子查询列表
     */
    public List<SubQuery> decompose(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        try {
            String response = chatClient.prompt()
                .system(DECOMPOSE_PROMPT)
                .user(query)
                .call()
                .content();

            if (response == null) return List.of(createSingleSubQuery(query));

            return parseResult(response);
        } catch (Exception e) {
            log.warn("查询分解异常: {}", e.getMessage());
            return List.of(createSingleSubQuery(query));
        }
    }

    @SuppressWarnings("unchecked")
    private List<SubQuery> parseResult(String response) {
        try {
            String json = extractJson(response);
            Map<String, Object> root = objectMapper.readValue(json, LinkedHashMap.class);
            List<Map<String, Object>> subQueriesRaw =
                (List<Map<String, Object>>) root.get("sub_queries");

            if (subQueriesRaw == null || subQueriesRaw.isEmpty()) {
                return List.of(createSingleSubQuery(
                    (String) root.getOrDefault("query", "")));
            }

            return subQueriesRaw.stream()
                .map(this::toSubQuery)
                .toList();
        } catch (JsonProcessingException e) {
            log.warn("解析分解结果失败: {}", e.getMessage());
            return List.of(createSingleSubQuery(response));
        }
    }

    @SuppressWarnings("unchecked")
    private SubQuery toSubQuery(Map<String, Object> m) {
        return SubQuery.builder()
            .id((String) m.getOrDefault("id", UUID.randomUUID().toString().substring(0, 8)))
            .query((String) m.get("query"))
            .dependsOn((List<String>) m.getOrDefault("depends_on", List.of()))
            .variants((List<String>) m.getOrDefault("variants", List.of()))
            .build();
    }

    private SubQuery createSingleSubQuery(String query) {
        return SubQuery.builder()
            .id("sq1")
            .query(query)
            .dependsOn(List.of())
            .variants(List.of(query))
            .build();
    }

    private String extractJson(String text) {
        text = text.trim();
        if (text.startsWith("```")) {
            int start = text.indexOf('\n');
            int end = text.lastIndexOf("```");
            if (start > 0 && end > start) {
                text = text.substring(start, end).trim();
            }
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) return text.substring(start, end + 1);
        return text;
    }
}
