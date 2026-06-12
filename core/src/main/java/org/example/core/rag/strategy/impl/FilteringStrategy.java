package org.example.core.rag.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.core.rag.context.RagContext;
import org.example.core.rag.strategy.DocumentProcessingStrategy;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 文档过滤策略
 * 过滤掉空文档、过短文档等低质量文档
 */
@Slf4j
@Component
public class FilteringStrategy implements DocumentProcessingStrategy {
    
    private static final int MIN_TEXT_LENGTH = 20;
    
    @Override
    public List<Document> process(List<Document> documents, RagContext context) {
        if (documents == null || documents.isEmpty()) {
            return documents;
        }
        
        List<Document> filtered = documents.stream()
                .filter(doc -> doc.getText() != null && !doc.getText().trim().isEmpty())
                .filter(doc -> doc.getText().length() >= MIN_TEXT_LENGTH)
                .collect(Collectors.toList());
        
        log.debug("文档过滤: {} -> {}", documents.size(), filtered.size());
        return filtered;
    }
    
    @Override
    public ProcessingType getType() {
        return ProcessingType.FILTERING;
    }
    
    @Override
    public String getName() {
        return "FilteringStrategy";
    }
}
