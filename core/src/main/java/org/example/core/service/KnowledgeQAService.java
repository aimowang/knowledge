package org.example.core.service;

import lombok.extern.slf4j.Slf4j;
import org.example.model.ChatMessage;
import org.example.model.LongTermMemory;
import org.example.model.RagAnswer;
import org.example.core.evaluation.EvaluationManager;
import org.example.core.evaluation.RagEvaluator;
import org.example.core.memory.LongTermMemoryManager;
import org.example.core.memory.MemoryExtractor;
import org.example.core.memory.ShortTermMemoryManager;
import org.example.core.rag.RagFlow;
import org.example.core.retrieval.ContentRetriever;
import org.example.model.RagAnswerWithEvaluation;
import org.example.model.enums.CategoryEnum;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
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
     * 普通问答（不带来源）
     */
    public String askInFlow(String question) {
        return askInFlowWithMemory("default_user", question);
    }
    
    /**
     * 带记忆的问答（支持多用户 + 长期记忆）
     * @param userId 用户ID
     * @param question 问题
     * @return 答案
     */
    public String askInFlowWithMemory(String userId, String question) {
        return askInFlowWithMemoryAndEvaluation(userId, question, null).getAnswer();
    }
    
    /**
     * 带记忆和评估的问答
     * @param userId 用户ID
     * @param question 问题
     * @param groundTruth 标准答案（可选，用于更准确的评估）
     * @return 包含答案和评估结果的对象
     */
    public RagAnswerWithEvaluation askInFlowWithMemoryAndEvaluation(
            String userId, String question, String groundTruth) {
        
        // 1. 保存用户消息到短期记忆
        shortTermMemoryManager.addUserMessage(userId, question);
        
        // 2. 获取短期对话历史
        List<ChatMessage> shortTermHistory = shortTermMemoryManager.getConversationHistory(userId);
        log.info("用户 {} 的短期记忆长度: {}", userId, shortTermHistory.size());
        
        // 3. 检索相关的长期记忆
        List<LongTermMemory> longTermMemories = longTermMemoryManager.searchMemories(userId, question, 3);
        if (!longTermMemories.isEmpty()) {
            log.info("为用户 {} 检索到 {} 条长期记忆", userId, longTermMemories.size());
        }
        
        // 4. 根据问题内容获取分类
        String category = classifyQuestion(question);
        log.info("问题分类: [{}]", category);
        
        // 5. 根据分类选择rag执行流程
        RagFlow rag = selectRagFlow(category);
        
        // 6. 执行 RAG 流程（传入短期和长期记忆）
        String answer = rag.executeRag(question, (sysPrompt, userPrompt) -> {
            // 构建包含短期和长期记忆的 prompt
            String enhancedSystemPrompt = buildSystemPromptWithAllMemories(
                sysPrompt, shortTermHistory, longTermMemories
            );
            
            return chatClient.prompt()
                    .system(enhancedSystemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
        });
        
        // 7. 保存助手回复到短期记忆
        shortTermMemoryManager.addAssistantMessage(userId, answer);
        
        // 8. 检查是否需要提取长期记忆
        if (memoryExtractor.shouldExtractMemories(shortTermHistory.size())) {
            extractAndSaveLongTermMemories(userId, shortTermHistory);
        }
        
        // 9. 评估 RAG 质量（异步，不阻塞响应）
        evaluateRagQualityAsync(userId, question, answer, groundTruth);
        
        return new RagAnswerWithEvaluation(answer, null);  // TODO: 返回评估结果
    }
    
    /**
     * 异步评估 RAG 质量
     */
    private void evaluateRagQualityAsync(String userId, String question, 
                                        String answer, String groundTruth) {
        // TODO: 实现异步评估，需要获取上下文信息
        // 这里简化处理，实际应该从 RAG 流程中传递上下文
    }
    
    /**
     * 提取并保存长期记忆
     */
    private void extractAndSaveLongTermMemories(String userId, List<ChatMessage> conversationHistory) {
        try {
            List<LongTermMemory> memories = memoryExtractor.extractMemories(userId, conversationHistory);
            if (!memories.isEmpty()) {
                for (LongTermMemory memory : memories) {
                    longTermMemoryManager.addMemory(memory);
                }
                log.info("为用户 {} 提取并保存了 {} 条长期记忆", userId, memories.size());
            }
        } catch (Exception e) {
            log.error("提取长期记忆失败", e);
        }
    }
    
    /**
     * 带来源的问答（推荐）
     */
    public RagAnswer askInFlowWithSources(String question) {
        // 1. 根据问题内容获取分类
        String category = classifyQuestion(question);
        log.info("问题分类: [{}]", category);
        
        // 2. 根据分类选择rag执行流程
        RagFlow rag = selectRagFlow(category);
        
        // 3. 执行带来源的 RAG 流程
        if (rag instanceof org.example.core.rag.AbstractBasicRag) {
            return ((org.example.core.rag.AbstractBasicRag) rag).executeRagWithSources(question, (sysPrompt, userPrompt) -> chatClient.prompt()
                    .system(sysPrompt)
                    .user(userPrompt)
                    .call()
                    .content());
        }
        
        // 降级：如果不支持，返回普通答案
        String answer = rag.executeRag(question, (sysPrompt, userPrompt) -> chatClient.prompt()
                .system(sysPrompt)
                .user(userPrompt)
                .call()
                .content());
        return new RagAnswer(answer, List.of());
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

    public String ask(String question) {
        // 1. 根据问题内容获取分类
        String category = classifyQuestion(question);
        System.out.println("问题分类: " + category);
        
        // 2. 根据分类选择第一个符合条件的 Retriever
        ContentRetriever retriever = selectRetriever(category);
        System.out.println("选择的 Retriever: " + retriever.getClass().getSimpleName());
        
        // 3. 检索相关文档
        List<Document> docs = retriever.retrieve(question);

        // 4. 构建上下文
        String context = docs.stream()
                .map(d -> String.format("【%s】(分类:%s)\n%s",
                        d.getMetadata().getOrDefault("title", "未知"),
                        d.getMetadata().getOrDefault("category", "未知"),
                        d.getText()))
                .collect(Collectors.joining("\n\n---\n\n"));

        // 5. 如果没有检索到相关内容
        if (context.isEmpty()) {
            return "该知识点暂未收录";
        }

        // 6. 构建完整的用户消息
        String userMessage = String.format("""
                资料：
                %s
                
                用户问题：%s
                答案：
                """, context, question);

        // 7. 调用大模型生成答案
        return chatClient.prompt()
                .system("""
                    你是一个大模型应用开发知识库助手。根据以下资料回答问题。
                    如果资料无法回答，请说“该知识点暂未收录”。
                    """)
                .user(userMessage)
                .call()
                .content();
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

    /**
     * 根据分类选择第一个符合条件的 Retriever
     */
    private ContentRetriever selectRetriever(String category) {
        // 查找支持该分类的第一个 Retriever
        for (ContentRetriever retriever : retrievers) {
            List<String> supportedCategories = retriever.support();
            if (supportedCategories.contains(category) || supportedCategories.contains("all")) {
                return retriever;
            }
        }
        // 如果没有找到匹配的，返回第一个 Retriever
        return retrievers.get(0);
    }
    
    /**
     * 构建包含对话历史的系统提示词
     */
    private String buildSystemPromptWithHistory(String baseSystemPrompt, List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return baseSystemPrompt;
        }
        
        StringBuilder sb = new StringBuilder(baseSystemPrompt);
        sb.append("\n\n**对话历史：**\n");
        
        // 只保留最近的 5 轮对话（避免 prompt 过长）
        int startIndex = Math.max(0, history.size() - 10);  // 10条消息 = 5轮对话
        
        for (int i = startIndex; i < history.size(); i++) {
            ChatMessage msg = history.get(i);
            String roleLabel = "user".equals(msg.getRole()) ? "用户" : "助手";
            sb.append(String.format("%s: %s\n", roleLabel, msg.getContent()));
        }
        
        sb.append("\n请基于以上对话历史和当前问题回答。\n");
        
        return sb.toString();
    }
    
    /**
     * 构建包含短期和长期记忆的系统提示词
     */
    private String buildSystemPromptWithAllMemories(
            String baseSystemPrompt, 
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
            
            // 只保留最近的 5 轮对话
            int startIndex = Math.max(0, shortTermHistory.size() - 10);
            
            for (int i = startIndex; i < shortTermHistory.size(); i++) {
                ChatMessage msg = shortTermHistory.get(i);
                String roleLabel = "user".equals(msg.getRole()) ? "用户" : "助手";
                sb.append(String.format("%s: %s\n", roleLabel, msg.getContent()));
            }
        }
        
        sb.append("\n请结合用户的长期记忆、最近对话和当前问题来回答。\n");
        
        return sb.toString();
    }
}
