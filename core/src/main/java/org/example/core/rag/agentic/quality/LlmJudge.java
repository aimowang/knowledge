package org.example.core.rag.agentic.quality;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LLM Judge (FR-11) — 运行时质量评估器。
 *
 * <p>在答案返回给用户前进行三个维度的质量评分：
 * <ul>
 *   <li><b>Faithfulness</b>（忠实度）：答案是否严格基于检索上下文，不包含无依据内容</li>
 *   <li><b>Answer Relevancy</b>（相关性）：答案是否直接回答用户问题</li>
 *   <li><b>Citation Grounding</b>（引用完整性）：每个关键主张是否有明确的 [N] 引用</li>
 * </ul>
 *
 * <p>低于阈值时触发重生成或降级。评分记录写入 rag_evaluations 表关联 trajectory_id。
 */
public class LlmJudge {

    private static final Logger log = LoggerFactory.getLogger(LlmJudge.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String JUDGE_PROMPT = """
        作为 LLM Judge，请对以下答案进行质量评分（0.0 ~ 1.0）。

        用户问题: %s

        检索上下文:
        %s

        答案:
        %s

        评分维度：
        1. faithfulness（忠实度）：答案是否严格基于上述上下文，没有编造或幻觉？
        2. answer_relevancy（相关性）：答案是否直接回答用户问题，没有偏题？
        3. citation_grounding（引用完整性）：关键主张是否有对应的 [N] 引用标记？

        以 JSON 格式返回，不要附加说明：
        {
          "faithfulness": 0.9,
          "answer_relevancy": 0.8,
          "citation_grounding": 0.7,
          "summary": "简要说明评分理由"
        }
        """;

    private final ChatClient chatClient;

    public LlmJudge(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 对答案进行质量评分。
     */
    public QualityScores evaluate(String query, String answer, String context) {
        if (answer == null || answer.isBlank()) {
            return new QualityScores(0, 0, 0);
        }

        try {
            String prompt = String.format(JUDGE_PROMPT,
                truncate(query, 500),
                truncate(context, 3000),
                truncate(answer, 4000));

            String response = chatClient.prompt()
                .system("你是一个严格的 LLM Judge。只返回 JSON，不要附加说明。")
                .user(prompt)
                .call()
                .content();

            if (response == null) {
                log.warn("LLM Judge 返回为空");
                return defaultScores();
            }

            return parseScores(response);

        } catch (Exception e) {
            log.warn("LLM Judge 评分异常: {}", e.getMessage());
            return defaultScores();
        }
    }

    /**
     * 评估并检查是否通过质量阈值。
     */
    public boolean isPassing(String query, String answer, String context,
                              QualityThresholds thresholds) {
        QualityScores scores = evaluate(query, answer, context);
        boolean passing = scores.isPassing(thresholds);
        if (!passing) {
            log.info("LLM Judge 未通过: faithfulness={}, relevancy={}, citation={} (阈值: {}/{}/{})",
                scores.getFaithfulness(), scores.getAnswerRelevancy(),
                scores.getCitationGrounding(),
                thresholds.getFaithfulness(), thresholds.getAnswerRelevancy(),
                thresholds.getCitationGrounding());
        }
        return passing;
    }

    @SuppressWarnings("unchecked")
    private QualityScores parseScores(String response) {
        try {
            String json = extractJson(response);
            Map<String, Object> result = objectMapper.readValue(json, LinkedHashMap.class);

            double faithfulness = toDouble(result.get("faithfulness"));
            double relevancy = toDouble(result.get("answer_relevancy"));
            double citation = toDouble(result.get("citation_grounding"));

            log.debug("LLM Judge 评分: faithfulness={}, relevancy={}, citation={}",
                faithfulness, relevancy, citation);

            return new QualityScores(faithfulness, relevancy, citation);

        } catch (JsonProcessingException e) {
            log.warn("解析 LLM Judge 评分失败: {}", e.getMessage());
            return defaultScores();
        }
    }

    private double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0.5; }
        }
        return 0.5;
    }

    private QualityScores defaultScores() {
        // 默认返回中等分数，不阻断流程
        return new QualityScores(0.7, 0.7, 0.7);
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
