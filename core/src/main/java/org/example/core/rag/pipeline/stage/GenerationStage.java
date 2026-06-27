package org.example.core.rag.pipeline.stage;

import lombok.extern.slf4j.Slf4j;
import org.example.core.metrics.RagMetrics;
import org.example.core.rag.context.RagContext;
import org.example.core.rag.pipeline.PipelineStage;
import org.example.core.resilience.ResilienceHelper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 答案生成阶段
 * 负责基于检索到的文档生成最终答案
 */
@Slf4j
public class GenerationStage implements PipelineStage {
    
    private final ChatClient chatClient;
    private final ResilienceHelper resilienceHelper;
    private final RagMetrics ragMetrics;
    
    public GenerationStage(@Qualifier("fullChatClient") ChatClient chatClient, ResilienceHelper resilienceHelper, RagMetrics ragMetrics) {
        this.chatClient = chatClient;
        this.resilienceHelper = resilienceHelper;
        this.ragMetrics = ragMetrics;
    }
    
    @Override
    public void process(RagContext context) {
        List<Document> docs = context.getDocuments();
        
        if (docs == null || docs.isEmpty()) {
            log.debug("无文档，返回默认答案");
            context.setAnswer("该知识点暂未收录");
            context.setSources(List.of());
            return;
        }
        
        log.debug("开始生成答案 - 使用 {} 个文档", docs.size());
        
        try {
            // 1. 构建系统提示词
            String systemPrompt = buildSystemMessage(docs);
            
            // 2. 构建用户提示词
            String userPrompt = buildUserMessage(context.getOriginalQuestion(), docs);
            
            // 3. 调用 LLM 生成答案（带容错和指标采集）
            long startTime = System.currentTimeMillis();
            String answer = callLlmWithResilience(systemPrompt + "\n\n" + userPrompt);
            long durationMs = System.currentTimeMillis() - startTime;
            
            // 记录 LLM 调用指标
            if (ragMetrics != null) {
                ragMetrics.recordLlmCall();
                ragMetrics.recordLlmCallDuration(durationMs / 1000.0);
            }
            
            // 4. 提取来源
            List<String> sources = extractSources(docs);
            List<String> citedSources = extractCitedSources(answer, sources);
            
            // 5. 设置结果
            context.setAnswer(answer);
            context.setSources(citedSources.isEmpty() ? sources : citedSources);
            
            log.info("答案生成完成 - 引用 {} 个来源", context.getSources().size());
            
        } catch (Exception e) {
            log.error("答案生成失败: {}", e.getMessage(), e);
            context.setAnswer("抱歉，服务暂时不可用，请稍后重试。");
            context.setSources(List.of());
        }
    }
    
    @Override
    public String getName() {
        return "GenerationStage";
    }
    
    @Override
    public String getDescription() {
        return "答案生成：基于检索文档生成最终答案";
    }
    
    /**
     * 构建系统提示词
     */
    private String buildSystemMessage(List<Document> docs) {
        StringBuilder contextBuilder = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            String title = (String) doc.getMetadata().getOrDefault("title", "未知");
            String category = (String) doc.getMetadata().getOrDefault("category", "未知");
            String source = (String) doc.getMetadata().getOrDefault("source", "未知");
            String text = doc.getText();
            
            contextBuilder.append(String.format("[%d] 【%s】(分类:%s, 来源:%s)\n%s\n\n",
                    i + 1, title, category, source, text));
        }
        
        return String.format("""
                你是一个大模型应用开发知识库助手。根据以下资料回答问题。
                如果资料无法回答，请说"该知识点暂未收录"。
                    
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
    
    /**
     * 构建用户提示词
     */
    private String buildUserMessage(String query, List<Document> docs) {
        return String.format("用户问题：%s\n答案：", query);
    }
    
    /**
     * 提取所有来源
     */
    private List<String> extractSources(List<Document> docs) {
        return docs.stream()
                .map(doc -> {
                    Object source = doc.getMetadata().get("source");
                    return source != null ? source.toString() : "未知来源";
                })
                .distinct()
                .collect(Collectors.toList());
    }
    
    /**
     * 提取被引用的来源
     */
    private List<String> extractCitedSources(String answer, List<String> allSources) {
        if (answer == null || allSources.isEmpty()) {
            return List.of();
        }
        
        Pattern pattern = Pattern.compile("\\[(\\d+)\\]");
        Matcher matcher = pattern.matcher(answer);
        
        java.util.Set<Integer> citedIndices = new java.util.HashSet<>();
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
        
        return citedIndices.stream()
                .sorted()
                .map(allSources::get)
                .collect(Collectors.toList());
    }
    
    /**
     * 带容错的 LLM 调用
     */
    private String callLlmWithResilience(String prompt) {
        if (resilienceHelper != null) {
            return resilienceHelper.executeWithLlmResilience(
                () -> chatClient.prompt(prompt).call().content(),
                () -> {
                    log.warn("LLM 调用失败，返回降级响应");
                    return "抱歉，服务暂时不可用，请稍后重试。";
                }
            );
        } else {
            log.warn("ResilienceHelper 未配置，直接调用 LLM");
            return chatClient.prompt(prompt).call().content();
        }
    }
}
