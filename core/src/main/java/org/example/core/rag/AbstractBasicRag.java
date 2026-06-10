package org.example.core.rag;

import lombok.extern.slf4j.Slf4j;
import org.example.core.rerank.ReRanker;
import org.example.core.retrieval.ContentRetriever;
import org.example.model.RagAnswer;
import org.example.model.enums.ComplexityLevelEnum;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractBasicRag implements RagFlow {

    /**
     * 执行完整的 RAG 流程
     */
    public String executeRag(String question, BiFunction<String, String, String> chatModel) {
        RagAnswer result = executeRagWithSources(question, chatModel);
        return result.getAnswer();
    }

    /**
     * 执行 RAG 流程并返回带来源的答案
     */
    public RagAnswer executeRagWithSources(String question, BiFunction<String, String, String> chatModel) {
        // 0. 问题复杂度分类（自适应路由）
        ComplexityLevelEnum complexity = classifyComplexity(question);

        // 根据复杂度选择不同的处理策略
        return switch (complexity) {
            case SIMPLE -> handleSimpleQuestionWithSources(question, chatModel);
            case MODERATE -> handleModerateQuestionWithSources(question, chatModel);
            case COMPLEX -> handleComplexQuestionWithSources(question, chatModel);
        };
    }

    protected ComplexityLevelEnum classifyComplexity(String question) {
        return ComplexityLevelEnum.SIMPLE;
    }

    /**
     * 处理简单问题（直接回答，无需检索）
     */
    protected String handleSimpleQuestion(String question, BiFunction<String, String, String> chatModel) {
        String systemPrompt = "你是一个简洁的助手。请直接回答问题，保持简短。";
        String userPrompt = "问题：" + question;
        return chatModel.apply(systemPrompt, userPrompt);
    }

    protected RagAnswer handleSimpleQuestionWithSources(String question, BiFunction<String, String, String> chatModel) {
        String answer = handleSimpleQuestion(question, chatModel);
        return new RagAnswer(answer, List.of());  // 简单问题没有来源
    }

    /**
     * 处理中等复杂度问题（标准 RAG 流程）
     */
    protected String handleModerateQuestion(String question, BiFunction<String, String, String> chatModel) {
        return executeStandardRag(question, chatModel, 5, 0.7);
    }

    protected RagAnswer handleModerateQuestionWithSources(String question, BiFunction<String, String, String> chatModel) {
        return executeStandardRagWithSources(question, chatModel, 5, 0.7);
    }

    /**
     * 处理复杂问题（增强 RAG 流程：多查询、更多文档、更严格的重排序）
     */
    protected String handleComplexQuestion(String question, BiFunction<String, String, String> chatModel) {
        return executeEnhancedRag(question, chatModel, 10, 0.8);
    }

    protected RagAnswer handleComplexQuestionWithSources(String question, BiFunction<String, String, String> chatModel) {
        return executeEnhancedRagWithSources(question, chatModel, 10, 0.8);
    }

    /**
     * 标准 RAG 流程
     */
    private String executeStandardRag(String question, BiFunction<String, String, String> chatModel, int topK, double lambda) {
        RagAnswer result = executeStandardRagWithSources(question, chatModel, topK, lambda);
        return result.getAnswer();
    }

    /**
     * 标准 RAG 流程（带来源）
     */
    private RagAnswer executeStandardRagWithSources(String question, BiFunction<String, String, String> chatModel, int topK, double lambda) {
        // 1. Query 预处理
        String query = preprocessQuery(question);

        // 2. Query 重写（可选）
        query = overrideQuery(query);

        // 3. 多 Query 生成（可选，用于提升召回率）
//        List<String> multiQuery = multiQuery(query);
//        StringBuilder qBuilder = new StringBuilder(query);
//        for (String q : multiQuery) {
//            qBuilder.append("\n").append(q);
//        }
//        String queryInfo = qBuilder.toString();

        // 4. 获取检索器
        ContentRetriever contextRetriever = getContextRetriever();
        if (contextRetriever == null) {
            return new RagAnswer("未配置检索器，无法回答问题", List.of());
        }

        // 5. 文档召回
        List<Document> docs = contextRetriever.retrieve(query);

        // 6. 空结果检查
        if (docs == null || docs.isEmpty()) {
            return new RagAnswer("该知识点暂未收录", List.of());
        }

        // 7. 文档过滤（可选）
        docs = filterDocuments(docs, query);

        // 8. 重排序（可选）
        ReRanker reRanker = getReRanker();
        if (reRanker != null && !docs.isEmpty()) {
            docs = reRanker.rerank(docs, query, topK, String.valueOf(lambda));
        }

        // 9. 再次检查空结果
        if (docs.isEmpty()) {
            return new RagAnswer("该知识点暂未收录", List.of());
        }

        // 提取文档来源（只保留 source 信息）
        List<String> sources = extractSources(docs);

        // 10. 上下文压缩（可选，避免超出 token 限制）
        docs = compressContext(docs, query);

        // 11. 构建系统提示词
        String systemPrompt = buildSystemMessage(docs);

        // 12. 构建用户提示词
        String userPrompt = buildUserMessage(query, docs);

        // 13. 调用大模型
        String answer = chatModel.apply(systemPrompt, userPrompt);
        
        // 14. 后处理（可选）
        answer = postprocessAnswer(answer, query, docs);
        
        // 15. 提取实际被引用的来源（从答案中解析 [1]、[2] 等标记）
        List<String> citedSources = extractCitedSources(answer, sources);

        return new RagAnswer(answer, citedSources);
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
                .distinct()  // 去重
                .collect(Collectors.toList());
    }


    /**
     * 增强 RAG 流程（用于复杂问题）
     */
    private String executeEnhancedRag(String question, BiFunction<String, String, String> chatModel, int topK, double lambda) {
        RagAnswer result = executeEnhancedRagWithSources(question, chatModel, topK, lambda);
        return result.getAnswer();
    }

    private RagAnswer executeEnhancedRagWithSources(String question, BiFunction<String, String, String> chatModel, int topK, double lambda) {
        // 对于复杂问题，启用多查询生成
        List<String> multiQuery = multiQuery(question);

        // 合并所有查询的结果
        StringBuilder queryBuilder = new StringBuilder(question);
        for (String q : multiQuery) {
            queryBuilder.append("\n").append(q);
        }
        // 使用更严格的参数执行标准 RAG
        return executeStandardRagWithSources(queryBuilder.toString(), chatModel, topK, lambda);
    }

    /**
     * Query 预处理：清洗、标准化
     */
    protected String preprocessQuery(String query) {
        if (query == null) {
            return "";
        }
        // 去除首尾空格
        query = query.trim();
        // 可以在这里添加更多预处理逻辑
        return query;
    }

    /**
     * 文档过滤：根据相关性分数或其他条件过滤低质量文档
     */
    protected List<Document> filterDocuments(List<Document> docs, String query) {
        // 默认过滤策略：移除过短的文档
        return docs.stream()
                .filter(doc -> doc.getText() != null && doc.getText().length() > 20)
                .collect(Collectors.toList());
    }

    /**
     * 上下文压缩：避免超出 LLM 的 token 限制，提高精确率
     */
    protected List<Document> compressContext(List<Document> docs, String query) {
        // 默认策略：保留最相关的文档
        if (docs.size() > 5) {
            // 只保留前 5 个最相关的文档
            return docs.subList(0, 5);
        }
        return docs;
    }

    /**
     * 答案后处理：格式化、验证等
     */
    protected String postprocessAnswer(String answer, String query, List<Document> docs) {
        // 默认不处理，子类可以重写
        return answer;
    }
    
    /**
     * 从答案中提取实际引用的来源编号
     * @param answer LLM 生成的答案
     * @param allSources 所有可能的来源列表
     * @return 实际被引用的来源列表
     */
    protected List<String> extractCitedSources(String answer, List<String> allSources) {
        if (answer == null || allSources.isEmpty()) {
            return List.of();
        }
        
        // 查找答案中的 [1]、[2] 等引用标记
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\[(\\d+)\\]");
        java.util.regex.Matcher matcher = pattern.matcher(answer);
        
        java.util.Set<Integer> citedIndices = new java.util.HashSet<>();
        while (matcher.find()) {
            try {
                int index = Integer.parseInt(matcher.group(1)) - 1;  // 转换为 0-based
                if (index >= 0 && index < allSources.size()) {
                    citedIndices.add(index);
                }
            } catch (NumberFormatException e) {
                // 忽略无效的编号
            }
        }
        
        // 如果没有找到引用标记，返回所有来源（兼容旧逻辑）
        if (citedIndices.isEmpty()) {
            log.warn("LLM 未标注引用来源，返回所有检索到的文档");
            return allSources;
        }
        
        // 返回实际被引用的来源
        log.info("从答案中提取到 {} 个引用来源", citedIndices.size());
        return citedIndices.stream()
                .sorted()
                .map(allSources::get)
                .collect(Collectors.toList());
    }

    @Override
    public String overrideQuery(String query) {
        return query;
    }

    @Override
    public List<String> multiQuery(String query) {
        return List.of();
    }

    @Override
    public ReRanker getReRanker() {
        return null;
    }

    @Override
    public String buildSystemMessage(List<Document> docs) {
        // 为每个文档添加编号，方便 LLM 引用
        StringBuilder contextBuilder = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            String title = (String) doc.getMetadata().getOrDefault("title", "未知");
            String category = (String) doc.getMetadata().getOrDefault("category", "未知");
            String text = doc.getText();
            
            contextBuilder.append(String.format("[%d] 【%s】(分类:%s)\n%s\n\n", 
                    i + 1, title, category, text));
        }
        
        String context = contextBuilder.toString();
        
        return String.format("""
                你是一个大模型应用开发知识库助手。根据以下资料回答问题。
                如果资料无法回答，请说“该知识点暂未收录”。
                
                **重要要求：**
                1. 请在回答中使用 [1]、[2] 等标记来引用资料来源
                2. 每个观点都应该标注来源
                3. 只使用提供的资料，不要编造信息
                4. 如果资料中的信息不足以完整回答问题，请明确说明
                5. 保持答案简洁、准确，直接回答问题
                
                资料：
                %s
                """, context);
    }

    @Override
    public String buildUserMessage(String query, List<Document> docs) {
        return String.format("用户问题：%s\n" +
                "                答案：", query);
    }
}
