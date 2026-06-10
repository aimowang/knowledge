package org.example.core.service;

import lombok.extern.slf4j.Slf4j;
import org.example.core.evaluation.EvaluationManager;
import org.example.core.evaluation.RagEvaluator;
import org.example.core.memory.LongTermMemoryManager;
import org.example.core.memory.MemoryExtractor;
import org.example.core.memory.ShortTermMemoryManager;
import org.example.core.rag.RagFlow;
import org.example.core.retrieval.ContentRetriever;
import org.example.model.RagAnswer;
import org.example.model.enums.CategoryEnum;
import org.springframework.ai.chat.client.ChatClient;
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

    public KnowledgeQAService(ChatClient chatClient, List<ContentRetriever> retrievers, 
                             List<RagFlow> ragFlows, ShortTermMemoryManager shortTermMemoryManager,
                             LongTermMemoryManager longTermMemoryManager, MemoryExtractor memoryExtractor,
                             RagEvaluator ragEvaluator, EvaluationManager evaluationManager) {
        this.chatClient = chatClient;
        this.retrievers = retrievers;
        this.ragFlows = ragFlows;
        this.shortTermMemoryManager = shortTermMemoryManager;
        this.longTermMemoryManager = longTermMemoryManager;
        this.memoryExtractor = memoryExtractor;
        this.ragEvaluator = ragEvaluator;
        this.evaluationManager = evaluationManager;
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
        for (RagFlow ragFlow : ragFlows) {
            if (ragFlow.support().contains(CategoryEnum.ALL.getValue())) {
                return ragFlow;
            }
        }
        return ragFlows.get(0);
    }


    /**
     * 根据问题内容获取分类
     * 使用 LLM 智能判断问题所属的分类
     */
    private String classifyQuestion(String question) {
        // 获取所有支持的分类
        List<String> allCategories = Arrays.stream(CategoryEnum.values()).map(CategoryEnum::getValue).collect(Collectors.toList());
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
                return category;
            }

            return "all";  // 默认返回 "all"
        } catch (Exception e) {
            System.err.println("分类失败，使用默认分类: " + e.getMessage());
            return "all";
        }
    }

}
