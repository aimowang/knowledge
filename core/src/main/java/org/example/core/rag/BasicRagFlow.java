package org.example.core.rag;

import org.example.core.compress.HybridCompressor;
import org.example.core.retrieval.BasicContentRetriever;
import org.example.core.retrieval.ContentRetriever;
import org.example.model.enums.CategoryEnum;
import org.example.model.enums.ComplexityLevelEnum;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BasicRagFlow extends AbstractBasicRag {

    private final BasicContentRetriever contentRetriever;

    private final HybridCompressor hybridCompressor;

    private final QueryComplexityClassifier queryComplexityClassifier;

    public BasicRagFlow(BasicContentRetriever contentRetriever, HybridCompressor hybridCompressor, QueryComplexityClassifier queryComplexityClassifier) {
        this.contentRetriever = contentRetriever;
        this.hybridCompressor = hybridCompressor;
        this.queryComplexityClassifier = queryComplexityClassifier;
    }

    @Override
    protected ComplexityLevelEnum classifyComplexity(String question) {
        return queryComplexityClassifier.classify(question);
    }

    @Override
    public List<String> support() {
        return List.of(CategoryEnum.BASIC.getValue());
    }

    @Override
    public ContentRetriever getContextRetriever() {
        return contentRetriever;
    }

    @Override
    protected List<Document> compressContext(List<Document> docs, String query) {
        return hybridCompressor.compress(docs, query);
    }
}
