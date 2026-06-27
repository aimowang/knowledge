package org.example.core.rag.agentic.router;

import org.example.core.rag.agentic.AgenticRagFlow;
import org.example.core.rag.agentic.agent.AgentConfig;
import org.example.core.rag.impl.AdvancedRagFlow;
import org.example.model.AskRequest;
import org.example.model.RagAnswer;
import org.example.model.enums.ComplexityLevelEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 查询路由 — 决定查询走 Workflow RAG 还是 Agentic RAG。
 *
 * <p>路由策略：
 * <ol>
 *   <li>强制开关优先：forceAgentic / forceWorkflow</li>
 *   <li>规则快速分类：短查询（&lt;20字符）无复杂关键词 → SIMPLE</li>
 *   <li>LLM 分类兜底：规则无法判断时调用 LLM 分类</li>
 *   <li>路由决策：SIMPLE → Workflow / MODERATE+COMPLEX → Agentic</li>
 * </ol>
 */
@Component
public class QueryRouter {

    private static final Logger log = LoggerFactory.getLogger(QueryRouter.class);

    private static final Set<String> COMPLEX_KEYWORDS = Set.of(
        "比较", "对比", "区别", "差异", "分析", "总结",
        "为什么", "如何", "如果", "优缺点", "推荐",
        "compare", "difference", "analysis", "versus", "vs"
    );

    private final AdvancedRagFlow advancedRagFlow;
    private final AgenticRagFlow agenticRagFlow;
    private final AgentConfig agentConfig;
    private final ChatClient chatClient;

    public QueryRouter(AdvancedRagFlow advancedRagFlow,
                       AgenticRagFlow agenticRagFlow,
                       AgentConfig agentConfig,
                       ChatClient chatClient) {
        this.advancedRagFlow = advancedRagFlow;
        this.agenticRagFlow = agenticRagFlow;
        this.agentConfig = agentConfig;
        this.chatClient = chatClient;
    }

    /**
     * 路由入口。
     */
    public RagAnswer route(AskRequest request) {
        String question = request.getQuestion();
        String userId = request.getUserId();
        String source = request.getSource();

        // 1. 检查是否启用了 Agentic RAG
        if (!agentConfig.isEnabled()) {
            log.debug("Agentic RAG 已禁用，走 Workflow 模式");
            return advancedRagFlow.executeRag(question, userId, source);
        }

        // 2. 强制开关优先
        if (agentConfig.getRouting().isForceWorkflow()) {
            return advancedRagFlow.executeRag(question, userId, source);
        }
        if (agentConfig.getRouting().isForceAgentic()) {
            return agenticRagFlow.executeRag(question, userId, source);
        }

        // 3. 规则快速分类
        Boolean isComplex = isComplexByRule(question);

        // 4. LLM 分类兜底（规则无法明确判断时）
        if (isComplex == null) {
            isComplex = isComplexByLLM(question);
        }

        // 5. 路由决策
        if (Boolean.FALSE.equals(isComplex)) {
            log.debug("路由: SIMPLE → Workflow RAG");
            return advancedRagFlow.executeRag(question, userId, source);
        }
        log.debug("路由: COMPLEX → Agentic RAG");
        return agenticRagFlow.executeRag(question, userId, source);
    }

    /**
     * 规则快速分类。
     * @return true=复杂, false=简单, null=无法判断(需LLM兜底)
     */
    private Boolean isComplexByRule(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        if (question.length() < agentConfig.getRouting().getSimpleThreshold()) {
            return false;
        }
        boolean hasComplexKeyword = COMPLEX_KEYWORDS.stream()
            .anyMatch(kw -> question.contains(kw));
        if (hasComplexKeyword) {
            return null; // 有复杂关键词但不确定，交给 LLM
        }
        return false;
    }

    /**
     * LLM 分类。
     * @return true=复杂(MODERATE/COMPLEX), false=简单(SIMPLE)
     */
    private boolean isComplexByLLM(String question) {
        try {
            String response = chatClient.prompt()
                .system("请判断以下问题的复杂度级别：\n"
                    + "SIMPLE - 简单事实性问题，单步检索即可回答\n"
                    + "MODERATE - 需要多源信息综合\n"
                    + "COMPLEX - 需要多步推理、比较分析或专业领域知识\n"
                    + "只返回一个词：SIMPLE、MODERATE 或 COMPLEX")
                .user(question)
                .call()
                .content();

            if (response == null) {
                return false;
            }
            String upper = response.trim().toUpperCase();
            if (upper.contains("COMPLEX") || upper.contains("MODERATE")) {
                return true; // 复杂 → Agentic
            }
            return false; // 简单 → Workflow
        } catch (Exception e) {
            log.warn("LLM 分类失败，默认 SIMPLE: {}", e.getMessage());
            return false;
        }
    }
}
