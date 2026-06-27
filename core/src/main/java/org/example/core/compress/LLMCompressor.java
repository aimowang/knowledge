package org.example.core.compress;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class LLMCompressor implements DocumentCompressor {
    private final ChatClient chatClient;
    private final int maxInputTokens;  // 单次压缩允许的最大输入 Token 数

    public LLMCompressor(@Qualifier("fastChatClient") ChatClient chatClient,
                         @Value("${compressor.llm.max-input-tokens:2000}") int maxInputTokens) {
        this.chatClient = chatClient;
        this.maxInputTokens = maxInputTokens;
    }

    @Override
    public List<Document> compress(List<Document> documents, String query) {
        List<Document> compressedDocs = new ArrayList<>();
        // 为提高效率，可以将多个短文档合并成一个批次调用 LLM，但要控制总 Token
        // 这里简单每个文档调用一次，适合文档数量较少的情况
        for (Document doc : documents) {
            String content = doc.getText();
            // 跳过空文档或过短文档
            if (content.trim().isEmpty()) continue;

            PromptTemplate template = new PromptTemplate("""
                你是一个文本压缩助手。根据用户问题，从以下文档中提取所有相关信息。
                如果文档不包含任何相关信息，请输出“无相关信息”。
                不要编造信息，仅使用文档中的原句或进行严格总结。

                用户问题：{query}
                文档：{content}
                提取结果：
                """);
            Prompt prompt = template.create(Map.of("query", query, "content", content));
            ChatResponse response = chatClient.prompt(prompt).call().chatResponse();
            String result = response.getResult().getOutput().getText();

            if (!result.contains("无相关信息") && !result.trim().isEmpty()) {
                Document compressed = new Document(result, doc.getMetadata());
                compressedDocs.add(compressed);
            }
        }
        return compressedDocs;
    }
}
