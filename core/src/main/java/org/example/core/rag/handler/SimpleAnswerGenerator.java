package org.example.core.rag.handler;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SimpleAnswerGenerator {
    private final ChatClient chatClient;

    public SimpleAnswerGenerator(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String handle(String question, List<Document> documents) {
        // 第一阶段：生成带自我检查的答案
        PromptTemplate genTemplate = new PromptTemplate("""
            你是一个严谨的问答助手。根据提供的资料，回答用户问题。
            在回答中，对每个关键断言，用 `[SUPPORTED]` 或 `[UNSUPPORTED]` 标记其是否被资料支持。
            
            资料：
            {context}
            
            用户问题：{question}
            答案（含标记）：
            """);
        // 3. 拼接上下文
        String context = documents.stream()
                .map(Document::getFormattedContent)
                .reduce("", (a, b) -> a + "\n\n" + b);
        Prompt prompt = genTemplate.create(Map.of("context", context, "question", question));
        String draft = chatClient.prompt(prompt).call().content();
        // 第二阶段：过滤掉 `[UNSUPPORTED]` 句子（简单实现）
        // 实际可更精细，或再调用 LLM 对不受支持的句子进行修正/删除
        String filtered = draft.replaceAll("\\[UNSUPPORTED\\].*?(?=\\[|$)", "");
        // 去除标记本身，形成干净输出
        return filtered.replace("[SUPPORTED]", "").trim();
    }
}
