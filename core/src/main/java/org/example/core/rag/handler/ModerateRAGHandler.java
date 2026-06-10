package org.example.core.rag.handler;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ModerateRAGHandler {
    private final ComplexRAGHandler complexRAGHandler;
    private final SimpleAnswerGenerator simpleAnswerGenerator;

    public ModerateRAGHandler(ComplexRAGHandler complexRAGHandler, SimpleAnswerGenerator simpleAnswerGenerator) {
        this.complexRAGHandler = complexRAGHandler;
        this.simpleAnswerGenerator = simpleAnswerGenerator;
    }

    public String handle(String question, List<Document> documents) {
        // 1. CRAG 增强检索（含评估纠正）
        List<Document> finalDocs = complexRAGHandler.handle(question, documents);
        // 2. 可选：混合压缩（已在之前实现）
        // List<Document> compressed = hybridCompressor.compress(finalDocs, question);
        // 3. Self‑RAG 生成
        return simpleAnswerGenerator.handle(question, finalDocs);
    }
}
