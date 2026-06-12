package org.example.core.rag;

import lombok.extern.slf4j.Slf4j;
import org.example.core.cache.CacheService;
import org.example.core.compress.HybridCompressor;
import org.example.core.rag.handler.ComplexRAGHandler;
import org.example.core.resilience.ResilienceHelper;
import org.example.core.rerank.ReRanker;
import org.example.core.retrieval.ContentRetriever;
import org.example.model.ChatMessage;
import org.example.model.LongTermMemory;
import org.example.model.RagAnswer;
import org.example.model.RetrievalConfig;
import org.example.model.enums.ComplexityLevelEnum;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * RAG 流程抽象基类 - 模板方法模式
 * 
 * @deprecated 已废弃，请使用新的 {@link org.example.core.rag.AbstractRagFlow} 架构
 * 
 * 迁移指南：
 * - 基本功能：继承 AbstractRagFlow 并配置管道和编排器
 * - 参考文档：MIGRATION_GUIDE.md
 * 
 * 设计原则：
 * 1. 统一的 RAG 流程实现，通过参数区分标准/增强模式
 * 2. 提供可定制的钩子方法（protected 方法）
 * 3. 支持记忆管理和质量评估的集成
 * 4. 消除代码重复，提高可维护性
 */
@Slf4j
@Deprecated
public abstract class AbstractBasicRag implements RagFlow {

    // ==================== 依赖注入 ====================
    
    private final QueryComplexityClassifier complexityClassifier;
    private final ChatClient chatClient;
    protected HybridCompressor hybridCompressor;
    protected ComplexRAGHandler complexRAGHandler;
    
    // P0 修复：注入缓存服务和容错辅助类
    @Autowired(required = false)
    protected CacheService cacheService;
    
    @Autowired(required = false)
    protected ResilienceHelper resilienceHelper;
    
    // P1 优化：注入专用线程池
    @Autowired
    @Qualifier("ragRetrievalExecutor")
    protected ThreadPoolTaskExecutor ragRetrievalExecutor;
    
    @Autowired
    @Qualifier("llmCallExecutor")
    protected ThreadPoolTaskExecutor llmCallExecutor;

    public AbstractBasicRag(QueryComplexityClassifier complexityClassifier, ChatClient chatClient) {
        this.complexityClassifier = complexityClassifier;
        this.chatClient = chatClient;
    }

    public AbstractBasicRag(QueryComplexityClassifier complexityClassifier, ChatClient chatClient,
                           HybridCompressor hybridCompressor) {
        this.complexityClassifier = complexityClassifier;
        this.chatClient = chatClient;
        this.hybridCompressor = hybridCompressor;
    }

    public AbstractBasicRag(QueryComplexityClassifier complexityClassifier, ChatClient chatClient,
                           ComplexRAGHandler complexRAGHandler) {
        this.complexityClassifier = complexityClassifier;
        this.chatClient = chatClient;
        this.complexRAGHandler = complexRAGHandler;
    }

    // ==================== RagFlow 接口实现 ====================

    @Override
    public RagAnswer executeRag(String question, String userId, String source) {
        log.debug("开始执行 RAG 流程 - 用户: {}, 来源: {}, 问题: {}", userId, source,
            question.substring(0, Math.min(50, question.length())));
        
        //1. 检查缓存（如果启用）
        if (userId != null && cacheService != null) {
            RagAnswer cachedAnswer = cacheService.getQaAnswer(userId, question, RagAnswer.class);
            if (cachedAnswer != null) {
                log.info("✅ 缓存命中 - 用户: {}, 问题: {}", userId, question.substring(0, Math.min(50, question.length())));
                return cachedAnswer;
            }
        }
        
        // 2. 获取记忆上下文（如果启用）
        MemoryContext memoryContext = loadMemoryContext(userId, question);
        
        // 3. 复杂度分类（自适应路由）
        ComplexityLevelEnum complexity = classifyComplexity(question);
        
        // 4. 根据复杂度选择处理策略（注入记忆和来源过滤）
        RagAnswer result = switch (complexity) {
            case SIMPLE -> handleSimpleQuestion(question, memoryContext);
            case MODERATE -> handleModerateQuestion(question, memoryContext, source);
            case COMPLEX -> handleComplexQuestion(question, memoryContext, source);
        };
        
        // 5. 保存对话到记忆（如果启用）
        if (userId != null && shouldUseShortTermMemory()) {
            saveToShortTermMemory(userId, question, result.getAnswer());
            
            // 提取长期记忆
            List<ChatMessage> history = getShortTermHistory(userId);
            if (!history.isEmpty()) {
                extractLongTermMemories(userId, history);
            }
        }
        
        // 6. 触发质量评估（如果启用）
        if (userId != null && shouldEnableEvaluation()) {
            triggerEvaluation(userId, question, result.getAnswer(), null);
        }
        
        // 7. 保存答案到缓存（如果启用）
        if (userId != null && cacheService != null && result.getAnswer() != null) {
            cacheService.cacheQaAnswer(userId, question, result);
            log.debug("✅ 已缓存答案 - 用户: {}", userId);
        }
        
        log.debug("RAG 流程完成 - 来源数: {}", result.getSources().size());
        return result;
    }

    /**
     * 加载记忆上下文（内部方法）
     */
    private MemoryContext loadMemoryContext(String userId, String question) {
        if (userId == null) {
            return MemoryContext.EMPTY;
        }
        
        List<ChatMessage> shortTermHistory = shouldUseShortTermMemory() ? 
            getShortTermHistory(userId) : List.of();
        List<LongTermMemory> longTermMemories = shouldUseLongTermMemory() ? 
            getLongTermMemories(userId, question) : List.of();
        
        if (!shortTermHistory.isEmpty()) {
            log.debug("加载短期记忆: {} 条消息", shortTermHistory.size());
        }
        if (!longTermMemories.isEmpty()) {
            log.debug("加载长期记忆: {} 条", longTermMemories.size());
        }
        
        return new MemoryContext(shortTermHistory, longTermMemories);
    }

    private String buildSystemMessage(List<Document> docs) {
        StringBuilder contextBuilder = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            String title = (String) doc.getMetadata().getOrDefault("title", "未知");
            String category = (String) doc.getMetadata().getOrDefault("category", "未知");
            String source = (String) doc.getMetadata().getOrDefault("source", "未知");
            String text = doc.getText();
    
            // 丰富元数据信息
            contextBuilder.append(String.format("[%d] 【%s】(分类:%s, 来源:%s)\n%s\n\n",
                    i + 1, title, category, source, text));
        }
    
        return String.format("""
                你是一个大模型应用开发知识库助手。根据以下资料回答问题。
                如果资料无法回答，请说“该知识点暂未收录”。
                    
                **重要要求：**
                1. 请在回答中使用 [1]、[2] 等标记来引用资料来源
                2. 每个观点都应该标注来源
                3. 只使用提供的资料，不要编造信息
                4. 如果资料中的信息不足以完整回答问题，请明确说明
                5. 保持答案简洁、准确，直接回答问题
                6. 优先引用高相关度的文档（排在前面的）
                    
                资料：
                %s
                """, contextBuilder.toString());
    }

    private String buildUserMessage(String query, List<Document> docs) {
        return String.format("用户问题：%s\n答案：", query);
    }

    /**
     * 记忆上下文记录类
     */
    protected record MemoryContext(List<ChatMessage> shortTermHistory, 
                                  List<LongTermMemory> longTermMemories) {
        static final MemoryContext EMPTY = new MemoryContext(List.of(), List.of());
    }

    // ==================== LLM 调用容错封装 ====================
    
    /**
     * 带容错的 LLM 调用（熔断、重试、超时）
     * @param prompt 提示词
     * @return LLM 响应内容
     */
    protected String callLlmWithResilience(String prompt) {
        if (resilienceHelper != null) {
            // 使用 Resilience4j 容错
            return resilienceHelper.executeWithLlmResilience(
                () -> chatClient.prompt(prompt).call().content(),
                () -> {
                    log.warn("⚠️ LLM 调用失败，返回降级响应");
                    return "抱歉，服务暂时不可用，请稍后重试。";
                }
            );
        } else {
            // 降级：直接调用（无容错）
            log.warn("⚠️ ResilienceHelper 未配置，直接调用 LLM");
            return chatClient.prompt(prompt).call().content();
        }
    }

    // ==================== RagFlow 接口实现 ====================

    public String overrideQuery(String query) {
        return query;
    }

    public List<String> multiQuery(String query) {
        return generateMultiQueries(query);
    }

    public ReRanker getReRanker() {
        return null;
    }

    // ==================== 钩子方法（子类可重写）====================

    protected ComplexityLevelEnum classifyComplexity(String question) {
        try {
            ComplexityLevelEnum complexity = complexityClassifier.classify(question);
            log.info("问题复杂度分类: {} - 问题: {}", complexity, question.substring(0, Math.min(50, question.length())));
            return complexity;
        } catch (Exception e) {
            log.error("复杂度分类失败，降级为 MODERATE", e);
            return ComplexityLevelEnum.MODERATE;
        }
    }

    protected boolean shouldUseMultiQuery(String question, ComplexityLevelEnum complexity) {
        return complexity == ComplexityLevelEnum.COMPLEX;
    }

    protected boolean shouldUseCRAG(ComplexityLevelEnum complexity) {
        return complexity == ComplexityLevelEnum.COMPLEX && complexRAGHandler != null;
    }

    protected RetrievalConfig getRetrievalConfig(ComplexityLevelEnum complexity) {
        return switch (complexity) {
            case SIMPLE -> new RetrievalConfig(0, 0.0);
            case MODERATE -> new RetrievalConfig(5, 0.7);
            case COMPLEX -> new RetrievalConfig(10, 0.8);
        };
    }

    protected List<String> generateMultiQueries(String originalQuery) {
        if (chatClient == null) {
            log.warn("ChatClient 未配置，无法生成多查询");
            return List.of();
        }
        
        try {
            log.info("生成多查询以提升召回率: {}", originalQuery);
            
            String systemPrompt = """
                你是一个查询扩展专家。基于用户的原始问题，生成 3-5 个不同角度的变体问题。
                这些变体应该：
                1. 保持原问题的核心意图
                2. 使用不同的措辞和表达方式
                3. 从不同角度或粒度提问
                4. 有助于检索更全面的信息
                
                只返回问题列表，每行一个，不要编号或其他内容。
                """;
            
            String userPrompt = "原始问题：" + originalQuery;
            
            // P0 修复：使用带容错的 LLM 调用
            String response = callLlmWithResilience(systemPrompt + "\n\n" + userPrompt);
            
            if (response == null || response.trim().isEmpty()) {
                return List.of();
            }
            
            List<String> queries = Arrays.stream(response.split("\n"))
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && line.length() > 5)
                    .limit(5)
                    .collect(Collectors.toList());
            
            log.info("生成了 {} 个变体查询", queries.size());
            return queries;
            
        } catch (Exception e) {
            log.error("多查询生成失败，返回空列表", e);
            return List.of();
        }
    }

    protected List<Document> deduplicateDocuments(List<Document> docs) {
        // 优化：使用内容哈希 + 相似度检测进行去重
        Map<String, Document> uniqueDocs = new LinkedHashMap<>();
        
        for (Document doc : docs) {
            if (doc.getText() == null || doc.getText().trim().isEmpty()) {
                continue;
            }
            
            // 1. 精确去重：使用内容哈希
            String contentHash = generateContentHash(doc.getText());
            
            // 2. 模糊去重：检查是否与已有文档高度相似
            boolean isDuplicate = false;
            for (String existingHash : uniqueDocs.keySet()) {
                if (isSimilarContent(contentHash, existingHash, doc.getText(), uniqueDocs.get(existingHash).getText())) {
                    isDuplicate = true;
                    log.debug("检测到相似文档，跳过: {}", doc.getText().substring(0, Math.min(30, doc.getText().length())));
                    break;
                }
            }
            
            if (!isDuplicate) {
                uniqueDocs.put(contentHash, doc);
            }
        }
        
        log.debug("文档去重: {} -> {}", docs.size(), uniqueDocs.size());
        return new ArrayList<>(uniqueDocs.values());
    }
    
    /**
     * 生成内容哈希（用于精确去重）
     */
    private String generateContentHash(String content) {
        // 简化：使用前100个字符的 hash
        String normalized = content.trim().toLowerCase();
        String sample = normalized.length() > 100 ? normalized.substring(0, 100) : normalized;
        return String.valueOf(sample.hashCode());
    }
    
    /**
     * 检测内容是否相似（用于模糊去重）
     * 使用简单的 Jaccard 相似度
     */
    private boolean isSimilarContent(String hash1, String hash2, String text1, String text2) {
        // 如果哈希相同，肯定是重复
        if (hash1.equals(hash2)) {
            return true;
        }
        
        // 计算 Jaccard 相似度
        Set<String> words1 = tokenize(text1);
        Set<String> words2 = tokenize(text2);
        
        if (words1.isEmpty() || words2.isEmpty()) {
            return false;
        }
        
        // 交集 / 并集
        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);
        
        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);
        
        double similarity = (double) intersection.size() / union.size();
        
        // 相似度阈值：80%
        return similarity > 0.8;
    }
    
    /**
     * 文本分词（简单实现）
     */
    private Set<String> tokenize(String text) {
        // 按空格和标点分割，转为小写
        String[] tokens = text.toLowerCase().split("[\\s\\p{Punct}]+");
        Set<String> result = new HashSet<>();
        for (String token : tokens) {
            if (token.length() > 2) {  // 忽略短词
                result.add(token);
            }
        }
        return result;
    }

    protected List<Document> applyCRAG(String query, List<Document> docs) {
        if (complexRAGHandler != null) {
            log.info("启用 CRAG 流程进行检索质量评估和修正");
            return complexRAGHandler.handle(query, docs);
        }
        return docs;
    }

    // ==================== 记忆管理钩子 ====================

    protected boolean shouldUseShortTermMemory() { return false; }
    protected boolean shouldUseLongTermMemory() { return false; }
    protected List<ChatMessage> getShortTermHistory(String userId) { return List.of(); }
    protected List<LongTermMemory> getLongTermMemories(String userId, String question) { return List.of(); }
    
    protected String buildPromptWithMemories(String baseSystemPrompt, 
                                            List<ChatMessage> shortTermHistory,
                                            List<LongTermMemory> longTermMemories) {
        return baseSystemPrompt;
    }
    
    protected void saveToShortTermMemory(String userId, String question, String answer) {}
    protected void extractLongTermMemories(String userId, List<ChatMessage> conversationHistory) {}

    /**
     * 钩子方法：是否启用 Query 增强（指代消解）
     */
    protected boolean shouldEnhanceQueryWithMemory() {
        return true;  // 默认启用，提升多轮对话体验
    }

    /**
     * 钩子方法：利用短期记忆增强 Query（带缓存）
     */
    protected String enhanceQueryWithMemory(String query, MemoryContext memoryContext) {
        if (!shouldEnhanceQueryWithMemory() || memoryContext.shortTermHistory().isEmpty()) {
            return query;
        }
        
        try {
            log.debug("使用短期记忆增强 Query: {}", query);
            
            String systemPrompt = """
                你是一个查询优化专家。根据对话历史，完善用户当前问题的指代和上下文。
                
                规则：
                1. 如果当前问题包含指代词（如“它”、“这个”、“前者”等），用对话历史中的具体内容替换
                2. 如果当前问题省略了主语或关键信息，从对话历史中补充
                3. 如果当前问题是完整的，直接返回原问题
                4. 只返回优化后的问题，不要有其他内容
                """;
            
            StringBuilder historyBuilder = new StringBuilder();
            List<ChatMessage> recentHistory = memoryContext.shortTermHistory().size() > 5 ?
                memoryContext.shortTermHistory().subList(
                    memoryContext.shortTermHistory().size() - 5,
                    memoryContext.shortTermHistory().size()
                ) : memoryContext.shortTermHistory();
            
            for (ChatMessage msg : recentHistory) {
                historyBuilder.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }
            
            String userPrompt = String.format("""
                对话历史：
                %s
                
                当前问题：%s
                
                优化后的问题：
                """, historyBuilder.toString(), query);
            
            // P0 修复：使用带容错的 LLM 调用
            String enhancedQuery = callLlmWithResilience(systemPrompt + "\n\n" + userPrompt);
            
            if (enhancedQuery != null && !enhancedQuery.trim().isEmpty()) {
                String result = enhancedQuery.trim();
                log.info("Query 增强: '{}' -> '{}'", query, result);
                
                return result;
            }
        } catch (Exception e) {
            log.warn("Query 增强失败，使用原问题: {}", e.getMessage());
        }
        
        return query;
    }
    
    /**
     * 生成 Query 缓存 key
     */
    private String generateQueryCacheKey(String query, MemoryContext memoryContext) {
        // 简化：只使用 query + 最近3条消息的 hash
        StringBuilder keyBuilder = new StringBuilder(query);
        List<ChatMessage> recentHistory = memoryContext.shortTermHistory().size() > 3 ?
            memoryContext.shortTermHistory().subList(
                memoryContext.shortTermHistory().size() - 3,
                memoryContext.shortTermHistory().size()
            ) : memoryContext.shortTermHistory();
        
        for (ChatMessage msg : recentHistory) {
            keyBuilder.append("|").append(msg.getRole()).append(":").append(msg.getContent());
        }
        
        return String.valueOf(keyBuilder.toString().hashCode());
    }

    /**
     * 钩子方法：是否启用关键词扩展
     */
    protected boolean shouldExpandQueryWithKeywords() {
        return true;  // 默认启用，提高召回率
    }

    /**
     * 使用 LLM 提取关键词并扩展查询
     * 例如："Spring Boot 优势" → "Spring Boot 优点 特性 好处 advantages features"
     */
    protected String expandQueryWithKeywords(String query) {
        if (!shouldExpandQueryWithKeywords() || chatClient == null) {
            return query;
        }
        
        try {
            log.debug("扩展查询关键词: {}", query);
            
            String systemPrompt = """
                你是一个关键词提取专家。从用户问题中提取核心关键词，并补充相关同义词。
                
                输出格式：原始问题 + 空格 + 关键词列表（用空格分隔）
                
                示例：
                输入：Spring Boot 的优势是什么？
                输出：Spring Boot 的优势是什么？ Spring Boot 优点 特性 好处 advantages features benefits
                
                输入：如何配置 MySQL 数据库？
                输出：如何配置 MySQL 数据库？ MySQL 配置 连接池 datasource 数据库设置
                
                只输出一行结果，不要解释。
                """;
            
            // P0 修复：使用带容错的 LLM 调用
            String expandedQuery = callLlmWithResilience(systemPrompt + "\n\n" + query);
            
            if (expandedQuery != null && !expandedQuery.trim().isEmpty()) {
                // 取第一行，防止 LLM 输出多余内容
                String firstLine = expandedQuery.split("\n")[0].trim();
                if (firstLine.length() > query.length()) {
                    log.info("查询扩展: '{}' -> '{}'", query, firstLine);
                    return firstLine;
                }
            }
        } catch (Exception e) {
            log.warn("查询扩展失败，使用原问题: {}", e.getMessage());
        }
        
        return query;
    }

    /**
     * 钩子方法：是否启用个性化检索
     */
    protected boolean shouldPersonalizeRetrieval() {
        return false;
    }

    /**
     * 钩子方法：根据长期记忆调整文档排序
     */
    protected List<Document> applyUserPreferenceToDocs(List<Document> docs, 
                                                       List<LongTermMemory> longTermMemories) {
        return docs;
    }

    // ==================== 质量评估钩子 ====================

    protected boolean shouldEnableEvaluation() { return false; }
    protected void triggerEvaluation(String userId, String question, String answer, String groundTruth) {}

    // ==================== 统一的核心流程 ====================

    /**
     * 处理简单问题
     */
    protected RagAnswer handleSimpleQuestion(String question, MemoryContext memoryContext) {
        String baseSystemPrompt = "你是一个简洁的助手。请直接回答问题，保持简短。";
        String enhancedSystemPrompt = buildPromptWithMemories(
            baseSystemPrompt, 
            memoryContext.shortTermHistory(), 
            memoryContext.longTermMemories()
        );
        
        String userPrompt = "问题：" + question;
        // P0 修复：使用带容错的 LLM 调用
        String answer = callLlmWithResilience(enhancedSystemPrompt + "\n\n" + userPrompt);
        return new RagAnswer(answer, List.of());
    }

    /**
     * 处理中等复杂度问题（带来源过滤）
     */
    protected RagAnswer handleModerateQuestion(String question, MemoryContext memoryContext, String source) {
        log.debug("处理中等复杂度问题: {}, 来源: {}", question.substring(0, Math.min(50, question.length())), source);
        return executeByComplexity(question, memoryContext, ComplexityLevelEnum.MODERATE, false, source);
    }

    /**
     * 处理复杂问题（带来源过滤）
     */
    protected RagAnswer handleComplexQuestion(String question, MemoryContext memoryContext, String source) {
        log.debug("处理复杂问题: {}, 来源: {}", question.substring(0, Math.min(50, question.length())), source);
        return executeByComplexity(question, memoryContext, ComplexityLevelEnum.COMPLEX, true, source);
    }

    /**
     * 根据复杂度执行 RAG 流程（通用方法，带来源过滤）
     * @param question 问题
     * @param memoryContext 记忆上下文
     * @param complexity 复杂度级别
     * @param useEnhancedFlow 是否使用增强流程
     * @param source 文档来源过滤（可选）
     */
    private RagAnswer executeByComplexity(String question, MemoryContext memoryContext, 
                                         ComplexityLevelEnum complexity, boolean useEnhancedFlow,
                                         String source) {
        RetrievalConfig config = getRetrievalConfig(complexity);
        return executeRagFlow(question, memoryContext, config.topK(), config.lambda(), useEnhancedFlow, source);
    }

    /**
     * 统一的 RAG 流程实现（核心方法，带来源过滤）
     * @param question 问题
     * @param memoryContext 记忆上下文
     * @param topK 检索文档数量
     * @param lambda 重排序参数
     * @param useEnhancedFlow 是否使用增强流程（多查询+CRAG）
     * @param source 文档来源过滤（可选）
     */
    protected RagAnswer executeRagFlow(String question,
                                      MemoryContext memoryContext,
                                      int topK, double lambda, boolean useEnhancedFlow,
                                      String source) {
        log.debug("执行 RAG 流程 - 增强: {}, topK: {}, lambda: {}, 来源: {}", useEnhancedFlow, topK, lambda, source);
        
        // 1. Query 预处理和增强（利用记忆）
        String query = preprocessQuery(question);
        query = overrideQuery(query);
        query = enhanceQueryWithMemory(query, memoryContext);  // ← 指代消解
        query = expandQueryWithKeywords(query);  // ← 关键词扩展

        // 2. 获取检索器并检索（考虑用户偏好和来源过滤）
        ContentRetriever contextRetriever = getContextRetriever();
        if (contextRetriever == null) {
            return new RagAnswer("未配置检索器，无法回答问题", List.of());
        }

        List<Document> allDocs;
        
        // 3. 根据是否增强流程和是否有来源过滤选择检索策略
        if (useEnhancedFlow && shouldUseMultiQuery(question, ComplexityLevelEnum.COMPLEX)) {
            log.info("启用多查询检索");
            // 优化：使用增强后的 query 而非原始 question
            allDocs = retrieveWithMultiQuery(query, source);
        } else {
            // 使用带来源过滤的检索
            allDocs = contextRetriever.retrieve(query, source);
        }

        // 4. 空结果检查
        if (allDocs == null || allDocs.isEmpty()) {
            return new RagAnswer("该知识点暂未收录", List.of());
        }

        // 5. 应用 CRAG（如果启用且是增强流程）
        if (useEnhancedFlow && shouldUseCRAG(ComplexityLevelEnum.COMPLEX)) {
            allDocs = applyCRAG(question, allDocs);
            
            if (allDocs == null || allDocs.isEmpty()) {
                return new RagAnswer("经过检索质量评估，未找到相关知识", List.of());
            }
        }

        // 6. 文档后处理流水线（包含个性化重排序）
        allDocs = postProcessDocuments(allDocs, query, topK, lambda, memoryContext);

        // 7. 再次检查空结果
        if (allDocs.isEmpty()) {
            return new RagAnswer("该知识点暂未收录", List.of());
        }

        // 8. 生成答案（注入记忆）
        return generateAnswerWithContext(question, query, allDocs, memoryContext);
    }

    /**
     * 多查询检索实现（带来源过滤、并行执行、超时控制）
     */
    protected List<Document> retrieveWithMultiQuery(String enhancedQuery, String source) {
        List<String> queries = generateMultiQueries(enhancedQuery);
        
        ContentRetriever retriever = getContextRetriever();
        if (retriever == null) {
            return List.of();
        }
        
        Set<String> allQueries = new LinkedHashSet<>();
        allQueries.add(enhancedQuery);  // 使用增强后的 query
        allQueries.addAll(queries);
        
        log.info("多查询检索: {} 个查询", allQueries.size());
        
        // 优化：并行执行多个查询 + 超时控制
        List<Document> allDocs = Collections.synchronizedList(new ArrayList<>());
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (String query : allQueries) {
            List<Document> finalAllDocs = allDocs;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    // P1 优化：使用注入的 ragRetrievalExecutor 线程池
                    // 优化：检索超时控制（3秒）
                    CompletableFuture<List<Document>> retrievalFuture = CompletableFuture.supplyAsync(() -> {
                        return retriever.retrieve(query, source);
                    }, ragRetrievalExecutor.getThreadPoolExecutor());
                    
                    List<Document> docs = retrievalFuture.get(3, TimeUnit.SECONDS);
                    
                    if (docs != null && !docs.isEmpty()) {
                        finalAllDocs.addAll(docs);
                        log.debug("查询 '{}' 检索到 {} 个文档",
                            query.substring(0, Math.min(30, query.length())), docs.size());
                    }
                } catch (TimeoutException e) {
                    log.warn("查询 '{}' 检索超时", query);
                } catch (Exception e) {
                    log.warn("查询 '{}' 检索失败: {}", query, e.getMessage());
                }
            }, ragRetrievalExecutor.getThreadPoolExecutor());
            
            futures.add(future);
        }
        
        // 等待所有查询完成（最多10秒）
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("部分查询未完成，使用已获取的结果", e);
        }
        
        allDocs = deduplicateDocuments(allDocs);
        log.info("多查询检索完成，共获得 {} 个不重复文档", allDocs.size());
        
        return allDocs;
    }

    protected abstract ContentRetriever getContextRetriever();

    /**
     * 文档后处理流水线（支持个性化）
     */
    protected List<Document> postProcessDocuments(List<Document> docs, String query, int topK, double lambda) {
        return postProcessDocuments(docs, query, topK, lambda, MemoryContext.EMPTY);
    }

    /**
     * 文档后处理流水线（支持个性化）
     */
    protected List<Document> postProcessDocuments(List<Document> docs, String query, int topK, double lambda, 
                                                  MemoryContext memoryContext) {
        // 1. 文档过滤
        docs = filterDocuments(docs, query);
        
        // 2. 重排序（考虑用户偏好）
        ReRanker reRanker = getReRanker();
        if (reRanker != null && !docs.isEmpty()) {
            docs = reRanker.rerank(docs, query, topK, String.valueOf(lambda));
        }
        
        // 3. 个性化调整（如果启用）
        if (shouldPersonalizeRetrieval() && !memoryContext.longTermMemories().isEmpty()) {
            docs = applyUserPreferenceToDocs(docs, memoryContext.longTermMemories());
        }
        
        // 4. 上下文压缩
        docs = compressContext(docs, query);
        
        return docs;
    }

    /**
     * 生成答案（支持记忆注入）
     */
    protected RagAnswer generateAnswerWithContext(String originalQuestion, String retrievalQuery, 
                                                  List<Document> docs,
                                                  MemoryContext memoryContext) {
        List<String> sources = extractSources(docs);

        String baseSystemPrompt = buildSystemMessage(docs);
        
        String enhancedSystemPrompt = buildPromptWithMemories(
            baseSystemPrompt, 
            memoryContext.shortTermHistory(), 
            memoryContext.longTermMemories()
        );
        
        String userPrompt = buildUserMessage(originalQuestion, docs);

        // 使用带容错的 LLM 调用
        String answer = callLlmWithResilience(enhancedSystemPrompt + "\n\n" + userPrompt);
        
        // 后处理：清理和验证
        answer = postprocessAnswer(answer, originalQuestion, docs);
        
        // 提取引用的来源
        List<String> citedSources = extractCitedSources(answer, sources);
        
        // 新增：验证引用完整性
        if (!citedSources.isEmpty()) {
            log.info("答案包含 {} 个引用来源", citedSources.size());
        } else {
            log.warn("答案未包含任何引用来源");
        }

        return new RagAnswer(answer, citedSources);
    }

    // ==================== 工具方法 ====================

    protected String preprocessQuery(String query) {
        return query == null ? "" : query.trim();
    }

    protected List<Document> filterDocuments(List<Document> docs, String query) {
        return docs.stream()
                .filter(doc -> doc.getText() != null && doc.getText().length() > 20)
                .collect(Collectors.toList());
    }

    protected List<Document> compressContext(List<Document> docs, String query) {
        if (hybridCompressor != null && !docs.isEmpty()) {
            try {
                log.info("使用 HybridCompressor 进行上下文压缩");
                List<Document> compressed = hybridCompressor.compress(docs, query);
                log.info("压缩后文档数: {} -> {}", docs.size(), compressed.size());
                return compressed;
            } catch (Exception e) {
                log.error("HybridCompressor 压缩失败，降级为简单截断", e);
            }
        }
        
        if (docs.size() > 5) {
            log.debug("使用简单截断策略，保留前 5 个文档");
            return docs.subList(0, 5);
        }
        return docs;
    }

    protected List<String> extractSources(List<Document> docs) {
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }

        return docs.stream()
                .map(doc -> {
                    Object source = doc.getMetadata().get("source");
                    return source != null ? source.toString() : "未知来源";
                })
                .distinct()
                .collect(Collectors.toList());
    }

    protected List<String> extractCitedSources(String answer, List<String> allSources) {
        if (answer == null || allSources.isEmpty()) {
            return List.of();
        }
        
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\[(\\d+)\\]");
        java.util.regex.Matcher matcher = pattern.matcher(answer);
        
        Set<Integer> citedIndices = new HashSet<>();
        while (matcher.find()) {
            try {
                int index = Integer.parseInt(matcher.group(1)) - 1;
                if (index >= 0 && index < allSources.size()) {
                    citedIndices.add(index);
                }
            } catch (NumberFormatException e) {
                // 忽略无效的编号
            }
        }
        
        if (citedIndices.isEmpty()) {
            log.warn("LLM 未标注引用来源，返回所有检索到的文档");
            return allSources;
        }
        
        log.info("从答案中提取到 {} 个引用来源", citedIndices.size());
        return citedIndices.stream()
                .sorted()
                .map(allSources::get)
                .collect(Collectors.toList());
    }

    protected String postprocessAnswer(String answer, String query, List<Document> docs) {
        return answer;
    }

    // ==================== 数据记录类 ====================

}
