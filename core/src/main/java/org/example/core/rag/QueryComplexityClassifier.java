package org.example.core.rag;

import org.example.model.enums.ComplexityLevelEnum;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class QueryComplexityClassifier {
    private final ChatClient chatClient;

    public QueryComplexityClassifier(@Qualifier("fastChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public ComplexityLevelEnum classify(String question) {
        // 用 few-shot 提示要求 LLM 输出 SIMPLE / MODERATE / COMPLEX
        PromptTemplate template = new PromptTemplate("""
            你是一个查询复杂度分类器。根据以下定义，输出分类标签（仅输出标签，不要解释）。
            - SIMPLE: 常识性、不需要外部信息即可回答。
            - MODERATE: 需要从文档中查找事实信息。
            - COMPLEX: 需要多步推理、比较或综合多个信息源。
            
            示例：
            问：法国的首都是哪里？ → SIMPLE
            问：公司今年第一季度的销售额是多少？ → MODERATE
            问：对比量子计算和经典计算在密码学中的应用前景 → COMPLEX
            
            用户问题：{question}
            分类：""");
        Prompt prompt = template.create(Map.of("question", question));
        String result = chatClient.prompt(prompt).call().content().trim().toUpperCase();
        // 简单解析
        if (result.contains("SIMPLE")) return ComplexityLevelEnum.SIMPLE;
        if (result.contains("COMPLEX")) return ComplexityLevelEnum.COMPLEX;
        return ComplexityLevelEnum.MODERATE;
    }
}
