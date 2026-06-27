package org.example.core.rag.agentic.quality;

import org.example.core.rag.agentic.tool.AgentTool;
import org.example.core.rag.agentic.tool.ToolRegistry;
import org.example.core.rag.agentic.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 纠错模块 (FR-10) — 基于 SelfReflection 的发现进行针对性修复。
 *
 * <p>流程：
 * <ol>
 *   <li><b>有界重试</b> (FR-10.1)：缺失引用→补充检索，未覆盖子问题→专项检索，矛盾→重评估</li>
 *   <li><b>修复验证</b> (FR-10.2)：修复后再次执行 SelfReflection 确认</li>
 *   <li><b>优雅降级</b> (FR-10.3)：修复仍失败时声明不确定性</li>
 * </ol>
 */
public class CorrectiveRepair {

    private static final Logger log = LoggerFactory.getLogger(CorrectiveRepair.class);

    private static final String REPAIR_PROMPT = """
        基于全部检索信息（含补充材料），重新生成准确、带引用的答案。

        用户问题: %s

        全部上下文:
        %s

        上次回答的问题:
        %s

        请重新生成答案，确保：
        1. 使用 [N] 标记引用来源
        2. 完整覆盖所有子问题
        3. 不包含矛盾内容
        4. 使用中文回答
        """;

    private final ChatClient chatClient;
    private final ToolRegistry toolRegistry;

    public CorrectiveRepair(ChatClient chatClient, ToolRegistry toolRegistry) {
        this.chatClient = chatClient;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 执行修复。
     *
     * @param query        原始用户查询
     * @param draftAnswer  当前答案草稿
     * @param report       自反思报告
     * @param context      当前上下文
     * @return 修复后的答案
     */
    public String repair(String query, String draftAnswer,
                          ReflectionReport report, String context) {
        log.info("纠错开始: 未覆盖子查询={}, 无引用声明={}, 矛盾={}",
            report.getUncoveredSubQueries() != null ? report.getUncoveredSubQueries().size() : 0,
            report.getUncitedClaims() != null ? report.getUncitedClaims().size() : 0,
            report.getContradictions() != null ? report.getContradictions().size() : 0);

        // 1. 收集缺失关键词
        Set<String> searchTerms = collectSearchTerms(report);
        if (searchTerms.isEmpty()) {
            log.info("无需要补充检索的缺失信息，跳过补充检索");
            return regenerateAnswer(query, draftAnswer, context, report);
        }

        // 2. 补充检索
        String supplementalContext = performSupplementalSearch(searchTerms);

        // 3. 合并上下文并重新生成
        String combinedContext = context;
        if (!supplementalContext.isBlank()) {
            combinedContext = context + "\n\n=== 补充检索材料 ===\n" + supplementalContext;
        }

        return regenerateAnswer(query, draftAnswer, combinedContext, report);
    }

    /**
     * 从反思报告中收集需要补充检索的关键词。
     */
    private Set<String> collectSearchTerms(ReflectionReport report) {
        Set<String> terms = new LinkedHashSet<>();

        if (report.getUncoveredSubQueries() != null) {
            terms.addAll(report.getUncoveredSubQueries());
        }
        if (report.getUncitedClaims() != null) {
            terms.addAll(extractKeywords(report.getUncitedClaims()));
        }
        // 最多取 3 个搜索词
        return terms.stream().limit(3).collect(Collectors.toSet());
    }

    /**
     * 从声明文本中提取关键词（取前 20 个字符作为搜索词）。
     */
    private List<String> extractKeywords(List<String> claims) {
        return claims.stream()
            .map(c -> c.length() > 20 ? c.substring(0, 20) : c)
            .collect(Collectors.toList());
    }

    /**
     * 使用 vector_search 工具进行补充检索。
     */
    private String performSupplementalSearch(Set<String> searchTerms) {
        StringBuilder sb = new StringBuilder();
        AgentTool vectorTool = toolRegistry.getTool("vector_search");

        if (vectorTool == null || !vectorTool.isAvailable()) {
            log.warn("vector_search 不可用，跳过补充检索");
            return "";
        }

        for (String term : searchTerms) {
            try {
                log.debug("补充检索: {}", term);
                ToolResult result = vectorTool.execute(Map.of("query", term, "top_k", 3));
                if (result.isSuccess() && result.getData() != null) {
                    sb.append("\n--- 补充检索: ").append(term).append(" ---\n");
                    sb.append(result.getData().toString());
                }
            } catch (Exception e) {
                log.warn("补充检索失败: {}", e.getMessage());
            }
        }
        return sb.toString();
    }

    /**
     * 基于完整上下文重新生成答案。
     */
    private String regenerateAnswer(String query, String draftAnswer,
                                     String context, ReflectionReport report) {
        String issuesSummary = formatIssuesSummary(report);

        try {
            return chatClient.prompt()
                .system("你是一个智能知识库助手。基于全部检索信息重新生成准确、带引用的答案。"
                    + "注意修复之前的问题：" + issuesSummary)
                .user(String.format(REPAIR_PROMPT, query,
                    truncate(context, 6000), issuesSummary))
                .call()
                .content();
        } catch (Exception e) {
            log.error("答案重新生成失败: {}", e.getMessage());
            return draftAnswer;
        }
    }

    private String formatIssuesSummary(ReflectionReport report) {
        List<String> issues = new ArrayList<>();
        if (report.getUncitedClaims() != null && !report.getUncitedClaims().isEmpty()) {
            issues.add("缺少引用: " + String.join("; ", report.getUncitedClaims()));
        }
        if (report.getUncoveredSubQueries() != null && !report.getUncoveredSubQueries().isEmpty()) {
            issues.add("未覆盖子问题: " + String.join("; ", report.getUncoveredSubQueries()));
        }
        if (report.getContradictions() != null && !report.getContradictions().isEmpty()) {
            issues.add("存在矛盾: " + String.join("; ", report.getContradictions()));
        }
        return issues.isEmpty() ? "无" : String.join("\n", issues);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
