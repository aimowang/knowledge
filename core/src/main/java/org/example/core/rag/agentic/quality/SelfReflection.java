package org.example.core.rag.agentic.quality;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 自反思模块 (FR-9) — 答案生成后对质量和完整性进行检查。
 *
 * <p>三个检查维度：
 * <ol>
 *   <li><b>引用缺失检查</b> (FR-9.1)：逐句检查关键主张是否有对应的 [N] 引用</li>
 *   <li><b>子查询覆盖检查</b> (FR-9.2)：对比分解出的子查询是否全部在答案中被覆盖</li>
 *   <li><b>矛盾检测</b> (FR-9.3)：检测答案内部自相矛盾的内容，或答案与检索材料的矛盾</li>
 * </ol>
 */
public class SelfReflection {

    private static final Logger log = LoggerFactory.getLogger(SelfReflection.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ChatClient chatClient;

    public SelfReflection(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 对答案草稿进行自反思。
     *
     * @param query        原始用户查询
     * @param subQueries   分解出的子查询（可能为 null，Phase 3 完整）
     * @param draftAnswer  生成的答案草稿
     * @param context      检索到的上下文
     * @return 反思报告
     */
    public ReflectionReport reflect(String query, List<String> subQueries,
                                     String draftAnswer, String context) {
        List<String> uncitedClaims = checkCitations(draftAnswer);
        List<String> uncovered = checkCoverage(subQueries, draftAnswer);
        List<String> contradictions = checkContradictions(draftAnswer, context);

        boolean hasIssues = !uncitedClaims.isEmpty()
            || !uncovered.isEmpty()
            || !contradictions.isEmpty();

        return new ReflectionReport(hasIssues, uncitedClaims, uncovered, contradictions);
    }

    /**
     * 引用缺失检查 — 答案中的关键主张是否都有 [N] 引用标记。
     */
    private List<String> checkCitations(String draftAnswer) {
        if (draftAnswer == null || draftAnswer.isBlank()) {
            return List.of();
        }

        try {
            String response = chatClient.prompt()
                .system("你是一个引用检查专家。检查以下答案中的关键主张是否有对应的引用标记 [N]。"
                    + "只返回 JSON 列表，不要附加说明。")
                .user("答案:\n" + truncate(draftAnswer, 4000)
                    + "\n\n列出所有没有引用支持的关键主张（如果全部有引用则返回空列表 []）：")
                .call()
                .content();

            return parseStringList(response);
        } catch (Exception e) {
            log.warn("引用检查异常: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 子查询覆盖检查 — 对比子查询列表与答案内容。
     */
    private List<String> checkCoverage(List<String> subQueries, String draftAnswer) {
        if (subQueries == null || subQueries.isEmpty()) {
            return List.of();
        }
        if (draftAnswer == null || draftAnswer.isBlank()) {
            return new ArrayList<>(subQueries);
        }

        try {
            String subQueryText = subQueries.stream()
                .map(sq -> "- " + sq)
                .collect(Collectors.joining("\n"));

            String response = chatClient.prompt()
                .system("你是一个答案完整性检查专家。检查答案是否覆盖了所有子问题。"
                    + "只返回 JSON 列表，不要附加说明。")
                .user("子问题列表:\n" + subQueryText
                    + "\n\n答案:\n" + truncate(draftAnswer, 4000)
                    + "\n\n列出答案中没有覆盖的子问题（如果全部覆盖则返回空列表 []）：")
                .call()
                .content();

            return parseStringList(response);
        } catch (Exception e) {
            log.warn("子查询覆盖检查异常: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 矛盾检测 — 答案内部矛盾或答案与材料的矛盾。
     */
    private List<String> checkContradictions(String draftAnswer, String context) {
        if (draftAnswer == null || draftAnswer.isBlank()) {
            return List.of();
        }

        try {
            String response = chatClient.prompt()
                .system("你是一个矛盾检测专家。检测答案是否存在内部矛盾，或与检索材料矛盾。"
                    + "只返回 JSON 列表，不要附加说明。")
                .user("检索材料:\n" + truncate(context, 3000)
                    + "\n\n答案:\n" + truncate(draftAnswer, 4000)
                    + "\n\n列出所有矛盾点（如果无矛盾则返回空列表 []）：")
                .call()
                .content();

            return parseStringList(response);
        } catch (Exception e) {
            log.warn("矛盾检测异常: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 解析 LLM 返回的 JSON 列表。
     */
    @SuppressWarnings("unchecked")
    private List<String> parseStringList(String response) {
        if (response == null || response.isBlank()) {
            return List.of();
        }

        try {
            // 提取 JSON（处理 Markdown 代码块）
            String json = extractJsonArray(response);
            if (json == null || json.equals("[]")) {
                return List.of();
            }
            return objectMapper.readValue(json, List.class);
        } catch (JsonProcessingException e) {
            log.debug("解析列表失败: {}", e.getMessage());
            return List.of();
        }
    }

    private String extractJsonArray(String text) {
        text = text.trim();
        if (text.startsWith("```")) {
            int start = text.indexOf('[');
            int end = text.lastIndexOf(']');
            if (start >= 0 && end > start) {
                text = text.substring(start, end + 1);
            }
        }
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
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
