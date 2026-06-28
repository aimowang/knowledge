package org.example.core.service;

import lombok.extern.slf4j.Slf4j;
import org.example.core.evaluation.EvaluationManager;
import org.example.core.evaluation.RagEvaluator;
import org.example.core.memory.LongTermMemoryManager;
import org.example.core.memory.MemoryExtractor;
import org.example.core.memory.ShortTermMemoryManager;
import org.example.core.rag.RagFlow;
import org.example.core.rag.agentic.agent.AgentConfig;
import org.example.core.rag.agentic.agent.AgentOrchestrator;
import org.example.core.rag.agentic.agent.StreamEvent;
import org.example.core.rag.agentic.router.QueryRouter;
import org.example.core.rag.impl.AdvancedRagFlow;
import org.example.core.retrieval.ContentRetriever;
import org.example.model.AgenticAskRequest;
import org.example.model.AgenticAskResponse;
import org.example.model.AskRequest;
import org.example.model.RagAnswer;
import org.example.model.enums.CategoryEnum;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KnowledgeQAService {
    private final ChatClient chatClient;
    private final List<ContentRetriever> retrievers;
    private final List<RagFlow> ragFlows;
    private final ShortTermMemoryManager shortTermMemoryManager;
    private final LongTermMemoryManager longTermMemoryManager;
    private final MemoryExtractor memoryExtractor;
    private final RagEvaluator ragEvaluator;
    private final EvaluationManager evaluationManager;
    private final QueryRouter queryRouter;
    private final AgentConfig agentConfig;
    private final AgentOrchestrator agentOrchestrator;

    public KnowledgeQAService(@Qualifier("fastChatClient") ChatClient chatClient, List<ContentRetriever> retrievers,
                             List<RagFlow> ragFlows, ShortTermMemoryManager shortTermMemoryManager,
                             LongTermMemoryManager longTermMemoryManager, MemoryExtractor memoryExtractor,
                             RagEvaluator ragEvaluator, EvaluationManager evaluationManager,
                             QueryRouter queryRouter, AgentConfig agentConfig,
                             AgentOrchestrator agentOrchestrator) {
        this.chatClient = chatClient;
        this.retrievers = retrievers;
        this.ragFlows = ragFlows;
        this.shortTermMemoryManager = shortTermMemoryManager;
        this.longTermMemoryManager = longTermMemoryManager;
        this.memoryExtractor = memoryExtractor;
        this.ragEvaluator = ragEvaluator;
        this.evaluationManager = evaluationManager;
        this.queryRouter = queryRouter;
        this.agentConfig = agentConfig;
        this.agentOrchestrator = agentOrchestrator;
    }

    /**
     * 带来源的问答（支持文档来源过滤）
     * @param userId 用户ID
     * @param question 问题
     * @param source 文档来源过滤（可选）
     */
    public RagAnswer askInFlowWithSources(String userId, String question, String source) {
        // 1. 根据问题内容获取分类
        String category = classifyQuestion(question);
        log.info("问题分类: [{}], 来源过滤: {}", category, source);

        // 2. 根据分类选择rag执行流程
        RagFlow rag = selectRagFlow(category);

        // 3. 执行 RAG 流程（支持来源过滤）
        return rag.executeRag(question, userId, source);
    }

    private RagFlow selectRagFlow(String category) {
        for (RagFlow ragFlow : ragFlows) {
            if (ragFlow.support().contains(category)) {
                return ragFlow;
            }
        }
        // fallback: 返回默认的 AdvancedRagFlow
        for (RagFlow ragFlow : ragFlows) {
            if (ragFlow instanceof AdvancedRagFlow) {
                return ragFlow;
            }
        }
        return ragFlows.get(0);
    }


    // ════════════════════════════════════════════════════════════
    // Agentic RAG
    // ════════════════════════════════════════════════════════════

    /**
     * Agentic RAG 问答 — 通过 QueryRouter 路由到 Workflow 或 Agentic 模式。
     */
    public AgenticAskResponse askWithAgentic(AgenticAskRequest request) {
        // 基础校验
        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }

        // 将 AgenticAskRequest 转为 AskRequest（兼容现有路由逻辑）
        AskRequest askRequest = new AskRequest();
        askRequest.setUserId(request.getUserId());
        askRequest.setQuestion(request.getQuestion());

        // 安全应用请求级配置覆盖（保存并恢复，避免线程安全问题）
        Boolean savedForceAgentic = null;
        Boolean savedExternalSearch = null;
        try {
            if (request.getConfig() != null) {
                if (request.getConfig().getForceAgentic() != null) {
                    savedForceAgentic = agentConfig.getRouting().isForceAgentic();
                    agentConfig.getRouting().setForceAgentic(
                        request.getConfig().getForceAgentic());
                }
                if (request.getConfig().getEnableExternalSearch() != null) {
                    savedExternalSearch = agentConfig.getTool().getExternalSearch().isEnabled();
                    agentConfig.getTool().getExternalSearch().setEnabled(
                        request.getConfig().getEnableExternalSearch());
                }
            }

            // 路由执行
            RagAnswer answer = queryRouter.route(askRequest);

            // 构建 Agentic 响应
            AgenticAskResponse response = AgenticAskResponse.builder()
                .answer(answer.getAnswer())
                .sources(answer.getSources())
                .agenticMode(true)
                .totalDurationMs(0)
                .build();

            // 从 answer metadata 中提取 Agent 执行信息
            if (answer.getMetadata() != null) {
                java.util.Map<String, String> meta = answer.getMetadata();
                if (meta.containsKey("trajectoryId"))
                    response.setTrajectoryId(meta.get("trajectoryId"));
                if (meta.containsKey("loopCount"))
                    response.setLoopCount(Integer.parseInt(meta.get("loopCount")));
                if (meta.containsKey("totalDurationMs"))
                    response.setTotalDurationMs(Long.parseLong(meta.get("totalDurationMs")));
                if (meta.containsKey("faithfulness") || meta.containsKey("answerRelevancy")) {
                    java.util.Map<String, Double> scores = new java.util.HashMap<>();
                    if (meta.containsKey("faithfulness"))
                        scores.put("faithfulness", Double.parseDouble(meta.get("faithfulness")));
                    if (meta.containsKey("answerRelevancy"))
                        scores.put("answerRelevancy", Double.parseDouble(meta.get("answerRelevancy")));
                    response.setQualityScores(scores);
                }
            }

            return response;
        } finally {
            // 恢复原始配置（确保即使异常也能恢复）
            if (savedForceAgentic != null) {
                agentConfig.getRouting().setForceAgentic(savedForceAgentic);
            }
            if (savedExternalSearch != null) {
                agentConfig.getTool().getExternalSearch().setEnabled(savedExternalSearch);
            }
        }
    }

    // ════════════════════════════════════════════════════════════
    // 流式 Agentic RAG
    // ════════════════════════════════════════════════════════════

    /**
     * 流式 Agentic RAG 问答 — 通过 Consumer 回调推流 StreamEvent。
     *
     * @param request  问答请求
     * @param onEvent  事件回调（在 ragRetrievalExecutor 线程中执行）
     */
    public void askWithAgentStream(AgenticAskRequest request,
                                    java.util.function.Consumer<StreamEvent> onEvent) {
        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            onEvent.accept(StreamEvent.error("question must not be blank"));
            return;
        }

        String userId = request.getUserId() != null ? request.getUserId() : "anonymous";
        String question = request.getQuestion();

        // 检查是否启用 Agentic 模式
        if (agentConfig.getRouting().isForceWorkflow() || !agentConfig.isEnabled()) {
            onEvent.accept(StreamEvent.thinking("Agentic 模式未启用，使用标准模式"));
            onEvent.accept(StreamEvent.done("请使用 /api/qa/ask 接口"));
            return;
        }

        // 委托给 AgentOrchestrator 的流式方法
        agentOrchestrator.executeStream(question, userId, onEvent);
    }

    /**
     * 根据问题内容获取分类
     * 使用 LLM 智能判断问题所属的分类
     */
    private String classifyQuestion(String question) {
        // 获取所有支持的分类
        List<String> allCategories = Arrays.stream(CategoryEnum.values())
                .map(CategoryEnum::getValue)
                .collect(Collectors.toList());
        
        // 构建分类提示词
        String categoriesStr = String.join(",", allCategories);
        String systemPrompt = String.format("""
            你是一个分类助手。请根据用户问题，从以下分类中选择最合适的一个：
            可用分类：%s
            
            只返回分类名称，不要有其他内容。
            如果问题不属于任何分类，返回 "all"。
            """, categoriesStr);

        try {
            String category = Objects.requireNonNull(chatClient.prompt()
                            .system(systemPrompt)
                            .user(question)
                            .call()
                            .content())
                    .trim();

            // 验证返回的分类是否有效
            if (allCategories.contains(category)) {
                log.debug("问题分类结果: {}", category);
                return category;
            }

            log.warn("LLM 返回无效分类: {}, 使用默认分类", category);
            return "all";  // 默认返回 "all"
        } catch (Exception e) {
            log.error("分类失败，使用默认分类", e);
            return "all";
        }
    }

}
