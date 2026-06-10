package org.example.core.rag;

import org.example.model.enums.AssessmentEnum;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class RetrievalEvaluator {
    private final ChatClient chatClient;

    public RetrievalEvaluator(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public AssessmentEnum evaluate(String question, List<Document> docs) {
        // 将文档拼接成摘要，让 LLM 判断是否足以回答问题
        String docSummary = docs.stream()
                .limit(5)
                .map(Document::getFormattedContent)
                .collect(Collectors.joining("\n---\n"));

        PromptTemplate template = new PromptTemplate("""
            你是一个检索质量评估器。判断以下文档能否回答问题。输出 CORRECT、AMBIGUOUS 或 INCORRECT。
            - CORRECT: 文档包含明确答案。
            - AMBIGUOUS: 文档部分相关但不足以回答。
            - INCORRECT: 文档不相关。
            
            问题：{question}
            文档摘要：{docs}
            评估：""");

        Prompt prompt = template.create(Map.of("question", question, "docs", docSummary));
        String result = chatClient.prompt(prompt).call().content().trim().toUpperCase();
        if (result.contains("CORRECT")) return AssessmentEnum.CORRECT;
        if (result.contains("INCORRECT")) return AssessmentEnum.INCORRECT;
        return AssessmentEnum.AMBIGUOUS;
    }
}
