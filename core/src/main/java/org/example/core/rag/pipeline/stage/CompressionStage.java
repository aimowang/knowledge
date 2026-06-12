package org.example.core.rag.pipeline.stage;

import lombok.extern.slf4j.Slf4j;
import org.example.core.rag.context.RagContext;
import org.example.core.rag.pipeline.PipelineStage;
import org.example.core.rag.strategy.DocumentProcessingStrategy;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 文档压缩阶段
 * 使用 HybridCompressor 进行上下文压缩，减少 token 消耗
 */
@Slf4j
public class CompressionStage implements PipelineStage {
    
    private final DocumentProcessingStrategy compressionStrategy;
    
    public CompressionStage(DocumentProcessingStrategy compressionStrategy) {
        this.compressionStrategy = compressionStrategy;
    }
    
    @Override
    public void process(RagContext context) {
        // 从上一个阶段获取文档（可能是重排序后的，也可能是过滤后的）
        List<Document> docs = context.getRerankedDocs() != null ? 
            context.getRerankedDocs() : context.getFilteredDocs();
        
        if (docs == null || docs.isEmpty()) {
            log.debug("无文档，跳过压缩");
            context.setFinalDocs(List.of());
            return;
        }
        
        log.debug("开始压缩 - 初始文档数: {}", docs.size());
        
        List<Document> compressed = compressionStrategy.process(docs, context);
        context.setFinalDocs(compressed);
        
        log.info("压缩完成: {} -> {} 个文档", docs.size(), compressed.size());
    }
    
    @Override
    public String getName() {
        return "CompressionStage";
    }
    
    @Override
    public boolean shouldSkip(RagContext context) {
        List<Document> docs = context.getRerankedDocs() != null ? 
            context.getRerankedDocs() : context.getFilteredDocs();
        return docs == null || docs.isEmpty();
    }
}
