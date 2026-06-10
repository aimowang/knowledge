package org.example.core.rag.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.core.compress.HybridCompressor;
import org.example.core.evaluation.EvaluationManager;
import org.example.core.evaluation.RagEvaluator;
import org.example.core.memory.LongTermMemoryManager;
import org.example.core.memory.MemoryExtractor;
import org.example.core.memory.ShortTermMemoryManager;
import org.example.core.rag.AbstractBasicRag;
import org.example.core.rag.QueryComplexityClassifier;
import org.example.core.retrieval.BasicContentRetriever;
import org.example.core.retrieval.ContentRetriever;
import org.example.model.ChatMessage;
import org.example.model.LongTermMemory;
import org.example.model.RagEvaluation;
import org.example.model.enums.CategoryEnum;
import org.example.model.enums.ComplexityLevelEnum;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 高级 RAG 流程实现
 * 
 * 特点：
 * - ✅ 启用短期记忆（对话历史）
 * - ✅ 启用长期记忆（用户偏好、事实）
 * - ✅ 启用质量评估（异步）
 * - ✅ 支持多查询生成
 * - ✅ 支持 CRAG 流程
 */
@Slf4j
@Component
public class AdvancedRagFlow extends AbstractBasicRag {

    private final BasicContentRetriever contentRetriever;
    private final ShortTermMemoryManager shortTermMemoryManager;
    private final LongTermMemoryManager longTermMemoryManager;
    private final MemoryExtractor memoryExtractor;
    private final RagEvaluator ragEvaluator;
    private final EvaluationManager evaluationManager;

    public AdvancedRagFlow(BasicContentRetriever contentRetriever,
                          HybridCompressor hybridCompressor,
                          QueryComplexityClassifier queryComplexityClassifier,
                          ChatClient chatClient,
                          ShortTermMemoryManager shortTermMemoryManager,
                          LongTermMemoryManager longTermMemoryManager,
                          MemoryExtractor memoryExtractor,
                          RagEvaluator ragEvaluator,
                          EvaluationManager evaluationManager) {
        super(queryComplexityClassifier, chatClient, hybridCompressor);
        this.contentRetriever = contentRetriever;
        this.shortTermMemoryManager = shortTermMemoryManager;
        this.longTermMemoryManager = longTermMemoryManager;
        this.memoryExtractor = memoryExtractor;
        this.ragEvaluator = ragEvaluator;
        this.evaluationManager = evaluationManager;
        log.info("AdvancedRagFlow 初始化完成 - 已启用记忆和评估功能");
    }

    @Override
    public List<String> support() {
        // AdvancedRagFlow 支持所有分类，作为默认的高级实现
        return List.of(CategoryEnum.ALL.getValue());
    }

    @Override
    public ContentRetriever getContextRetriever() {
        return contentRetriever;
    }

    // ==================== 个性化定制（钩子方法重写）====================

    /**
     * 启用短期记忆
     */
    @Override
    protected boolean shouldUseShortTermMemory() {
        return true;
    }

    /**
     * 启用长期记忆
     */
    @Override
    protected boolean shouldUseLongTermMemory() {
        return true;
    }

    /**
     * 从 ShortTermMemoryManager 获取短期记忆历史
     */
    @Override
    protected List<ChatMessage> getShortTermHistory(String userId) {
        List<ChatMessage> history = shortTermMemoryManager.getConversationHistory(userId);
        log.debug("为用户 {} 获取短期记忆: {} 条消息", userId, history.size());
        return history;
    }

    /**
     * 从 LongTermMemoryManager 检索长期记忆
     */
    @Override
    protected List<LongTermMemory> getLongTermMemories(String userId, String question) {
        List<LongTermMemory> memories = longTermMemoryManager.searchMemories(userId, question, 3);
        if (!memories.isEmpty()) {
            log.debug("为用户 {} 检索到长期记忆: {} 条", userId, memories.size());
        }
        return memories;
    }

    /**
     * 构建包含记忆的 Prompt
     */
    @Override
    protected String buildPromptWithMemories(String baseSystemPrompt,
                                            List<ChatMessage> shortTermHistory,
                                            List<LongTermMemory> longTermMemories) {
        StringBuilder sb = new StringBuilder(baseSystemPrompt);
        
        // 添加长期记忆
        if (longTermMemories != null && !longTermMemories.isEmpty()) {
            sb.append("\n\n**用户长期记忆：**\n");
            for (LongTermMemory memory : longTermMemories) {
                String typeLabel = switch (memory.getType()) {
                    case FACT -> "事实";
                    case PREFERENCE -> "偏好";
                    case CONTEXT -> "上下文";
                };
                sb.append(String.format("- [%s] %s\n", typeLabel, memory.getContent()));
            }
        }
        
        // 添加短期对话历史
        if (shortTermHistory != null && !shortTermHistory.isEmpty()) {
            sb.append("\n**最近对话：**\n");
            
            // 只保留最近的 5 轮对话（10条消息）
            int startIndex = Math.max(0, shortTermHistory.size() - 10);
            
            for (int i = startIndex; i < shortTermHistory.size(); i++) {
                ChatMessage msg = shortTermHistory.get(i);
                String roleLabel = "user".equals(msg.getRole()) ? "用户" : "助手";
                sb.append(String.format("%s: %s\n", roleLabel, msg.getContent()));
            }
        }
        
        if ((longTermMemories != null && !longTermMemories.isEmpty()) ||
            (shortTermHistory != null && !shortTermHistory.isEmpty())) {
            sb.append("\n请结合用户的长期记忆、最近对话和当前问题来回答。\n");
        }
        
        return sb.toString();
    }

    /**
     * 保存对话到短期记忆
     */
    @Override
    protected void saveToShortTermMemory(String userId, String question, String answer) {
        try {
            shortTermMemoryManager.addUserMessage(userId, question);
            shortTermMemoryManager.addAssistantMessage(userId, answer);
            log.debug("已保存用户 {} 的对话到短期记忆", userId);
        } catch (Exception e) {
            log.error("保存短期记忆失败 - 用户: {}", userId, e);
        }
    }

    /**
     * 提取并保存长期记忆
     */
    @Override
    protected void extractLongTermMemories(String userId, List<ChatMessage> conversationHistory) {
        try {
            if (memoryExtractor.shouldExtractMemories(conversationHistory.size())) {
                List<LongTermMemory> memories = memoryExtractor.extractMemories(userId, conversationHistory);
                if (!memories.isEmpty()) {
                    for (LongTermMemory memory : memories) {
                        longTermMemoryManager.addMemory(memory);
                    }
                    log.info("为用户 {} 提取并保存了 {} 条长期记忆", userId, memories.size());
                }
            }
        } catch (Exception e) {
            log.error("提取长期记忆失败 - 用户: {}", userId, e);
        }
    }

    /**
     * 启用 RAG 质量评估
     */
    @Override
    protected boolean shouldEnableEvaluation() {
        return true;
    }

    /**
     * 触发异步质量评估
     */
    @Override
    protected void triggerEvaluation(String userId, String question, String answer, String groundTruth) {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("开始异步评估 RAG 质量 - 用户: {}", userId);
                
                // 1. 使用 RagEvaluator 进行评估
                RagEvaluation evaluation = ragEvaluator.evaluate(
                    userId, question, answer, groundTruth
                );
                
                // 2. 保存评估结果
                evaluationManager.saveEvaluation(evaluation);
                
                log.info("RAG 质量评估完成 - 用户: {}, 综合评分: {:.2f}", 
                    userId, evaluation.getOverallScore());
                
                // 3. 如果评分过低，记录警告
                if (evaluation.getOverallScore() != null && evaluation.getOverallScore() < 0.6) {
                    log.warn("低质量 RAG 回答 - 用户: {}, 评分: {:.2f}, 问题: {}", 
                        userId, evaluation.getOverallScore(), 
                        question.substring(0, Math.min(50, question.length())));
                }
                
            } catch (Exception e) {
                log.error("RAG 质量评估失败 - 用户: {}", userId, e);
            }
        });
    }

    /**
     * 启用多查询生成（复杂问题）
     */
    @Override
    protected boolean shouldUseMultiQuery(String question, ComplexityLevelEnum complexity) {
        return complexity == ComplexityLevelEnum.COMPLEX;
    }

    /**
     * 自定义检索配置（更激进的参数）
     */
    @Override
    protected RetrievalConfig getRetrievalConfig(ComplexityLevelEnum complexity) {
        return switch (complexity) {
            case SIMPLE -> new RetrievalConfig(0, 0.0);
            case MODERATE -> new RetrievalConfig(8, 0.75);   // 更多文档
            case COMPLEX -> new RetrievalConfig(15, 0.85);   // 更多文档
        };
    }
}
