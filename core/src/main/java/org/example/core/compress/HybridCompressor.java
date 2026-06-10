package org.example.core.compress;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HybridCompressor implements DocumentCompressor {

    private final EmbeddingCompressor embeddingCompressor;
    private final LLMCompressor llmCompressor;

    public HybridCompressor(EmbeddingCompressor embeddingCompressor,
                            LLMCompressor llmCompressor) {
        this.embeddingCompressor = embeddingCompressor;
        this.llmCompressor = llmCompressor;
    }

    @Override
    public List<Document> compress(List<Document> documents, String query) {
        // 第一步：Embedding 粗压缩
        List<Document> coarseDocs = embeddingCompressor.compress(documents, query);
        // 第二步：LLM 精压缩
        return llmCompressor.compress(coarseDocs, query);
    }
}
