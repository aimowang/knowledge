package org.example.core.rag.orchestrator;

import lombok.extern.slf4j.Slf4j;
import org.example.core.cache.CacheService;
import org.example.core.memory.LongTermMemoryManager;
import org.example.core.memory.ShortTermMemoryManager;
import org.example.core.rag.QueryComplexityClassifier;
import org.example.core.rag.context.MemoryContext;
import org.example.core.rag.context.RagContext;
import org.example.core.evaluation.EvaluationManager;
import org.example.model.ChatMessage;
import org.example.model.LongTermMemory;
import org.example.model.RagAnswer;
import org.example.model.enums.ComplexityLevelEnum;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 默认 RAG 编排器实现
 * 协调缓存、记忆、评估等横切关注点
 */
@Slf4j
@Component
public class DefaultRagOrchestrator implements RagOrchestrator {
    
    private final CacheService cacheService;
    private final ShortTermMemoryManager shortTermMemoryManager;
    private final LongTermMemoryManager longTermMemoryManager;
    private final QueryComplexityClassifier complexityClassifier;
    private final EvaluationManager evaluationManager;
    
    // 配置对象
    private RagOrchestratorConfig config;
    
    public DefaultRagOrchestrator(CacheService cacheService,
                                 ShortTermMemoryManager shortTermMemoryManager,
                                 LongTermMemoryManager longTermMemoryManager,
                                 QueryComplexityClassifier complexityClassifier,
                                 EvaluationManager evaluationManager) {
        this.cacheService = cacheService;
        this.shortTermMemoryManager = shortTermMemoryManager;
        this.longTermMemoryManager = longTermMemoryManager;
        this.complexityClassifier = complexityClassifier;
        this.evaluationManager = evaluationManager;
        this.config = RagOrchestratorConfig.defaultConfig();
    }
    
    @Override
    public void beforeExecute(RagContext context) {
        String userId = context.getUserId();
        String question = context.getOriginalQuestion();
        
        log.debug("开始执行前编排 - 用户: {}", userId);
        
        // 1. 检查缓存（如果启用且有 userId）
        if (userId != null && cacheService != null) {
            RagAnswer cachedAnswer = cacheService.getQaAnswer(userId, question, RagAnswer.class);
            if (cachedAnswer != null) {
                log.info("✅ 缓存命中 - 用户: {}", userId);
                // 设置缓存命中标志，让调用方可以直接返回
                context.addMetadata("cacheHit", true);
                context.setAnswer(cachedAnswer.getAnswer());
                context.setSources(cachedAnswer.getSources());
                return;
            }
        }
        
        // 2. 加载记忆上下文
        MemoryContext memoryContext = loadMemoryContext(userId, question);
        context.setMemoryContext(memoryContext);
        
        // 3. 分类复杂度
        ComplexityLevelEnum complexity = classifyComplexity(question);
        context.setComplexity(complexity);
        
        log.debug("执行前编排完成 - 复杂度: {}, 短期记忆: {}条, 长期记忆: {}条",
            complexity,
            memoryContext.getShortTermHistory().size(),
            memoryContext.getLongTermMemories().size());
    }
    
    @Override
    public void afterExecute(RagContext context, RagAnswer answer) {
        String userId = context.getUserId();
        String question = context.getOriginalQuestion();
        
        if (userId == null) {
            return;
        }
        
        log.debug("开始执行后编排 - 用户: {}", userId);
        
        // 1. 保存短期记忆
        if (config.isShortTermMemoryEnabled() && shortTermMemoryManager != null) {
            saveToShortTermMemory(userId, question, answer.getAnswer());
        }
        
        // 2. 提取长期记忆
        if (config.isLongTermMemoryEnabled() && shortTermMemoryManager != null) {
            List<ChatMessage> history = getShortTermHistory(userId);
            if (!history.isEmpty()) {
                extractLongTermMemories(userId, history);
            }
        }
        
        // 3. 触发异步评估
        if (config.isEvaluationEnabled() && evaluationManager != null) {
            triggerEvaluation(userId, question, answer.getAnswer());
        }
        
        // 4. 缓存结果（如果未命中缓存）
        Boolean cacheHit = context.getMetadata("cacheHit", Boolean.class);
        if (cacheHit == null || !cacheHit) {
            if (cacheService != null && answer.getAnswer() != null) {
                cacheService.cacheQaAnswer(userId, question, answer);
                log.debug("✅ 已缓存答案 - 用户: {}", userId);
            }
        }
        
        log.debug("执行后编排完成");
    }
    
    @Override
    public RagOrchestratorConfig getConfig() {
        return config;
    }
    
    @Override
    public void updateConfig(RagOrchestratorConfig newConfig) {
        this.config = newConfig;
        log.info("更新 RAG 编排器配置: {}", newConfig);
    }
    
    // ==================== 私有方法 ====================
    
    /**
     * 加载记忆上下文
     */
    private MemoryContext loadMemoryContext(String userId, String question) {
        if (userId == null) {
            return MemoryContext.EMPTY;
        }
        
        List<ChatMessage> shortTermHistory = config.isShortTermMemoryEnabled() && shortTermMemoryManager != null ?
            shortTermMemoryManager.getHistory(userId) : List.of();
        
        List<LongTermMemory> longTermMemories = config.isLongTermMemoryEnabled() && longTermMemoryManager != null ?
            longTermMemoryManager.getRelevantMemories(userId, question) : List.of();
        
        return new MemoryContext(shortTermHistory, longTermMemories);
    }
    
    /**
     * 分类问题复杂度
     */
    private ComplexityLevelEnum classifyComplexity(String question) {
        try {
            ComplexityLevelEnum complexity = complexityClassifier.classify(question);
            log.debug("问题复杂度分类: {}", complexity);
            return complexity;
        } catch (Exception e) {
            log.error("复杂度分类失败，降级为 MODERATE", e);
            return ComplexityLevelEnum.MODERATE;
        }
    }
    
    /**
     * 保存短期记忆
     */
    private void saveToShortTermMemory(String userId, String question, String answer) {
        CompletableFuture.runAsync(() -> {
            try {
                shortTermMemoryManager.saveMessage(userId, "user", question);
                shortTermMemoryManager.saveMessage(userId, "assistant", answer);
                log.debug("已保存短期记忆 - 用户: {}", userId);
            } catch (Exception e) {
                log.error("保存短期记忆失败", e);
            }
        });
    }
    
    /**
     * 获取短期记忆历史
     */
    private List<ChatMessage> getShortTermHistory(String userId) {
        return shortTermMemoryManager != null ? 
            shortTermMemoryManager.getHistory(userId) : List.of();
    }
    
    /**
     * 提取长期记忆
     */
    private void extractLongTermMemories(String userId, List<ChatMessage> conversationHistory) {
        CompletableFuture.runAsync(() -> {
            try {
                // TODO: 集成 MemoryExtractor
                log.debug("长期记忆提取功能待实现 - 用户: {}", userId);
            } catch (Exception e) {
                log.error("提取长期记忆失败", e);
            }
        });
    }
    
    /**
     * 触发异步评估
     */
    private void triggerEvaluation(String userId, String question, String answer) {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("开始异步评估 RAG 质量 - 用户: {}", userId);
                // TODO: 集成 RagEvaluator 和 EvaluationManager
                log.debug("质量评估功能待完全集成 - 用户: {}", userId);
            } catch (Exception e) {
                log.error("RAG 质量评估失败 - 用户: {}", userId, e);
            }
        });
    }
}
