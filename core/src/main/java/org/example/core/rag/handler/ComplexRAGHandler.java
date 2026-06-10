package org.example.core.rag.handler;

import org.example.core.rag.RetrievalEvaluator;
import org.example.model.enums.AssessmentEnum;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ComplexRAGHandler {
    private final RetrievalEvaluator evaluator;
    private final KnowledgeRefiner refiner;     // 精炼模块
    private final ExternalSearchService externalSearch;  // 外部搜索

    public ComplexRAGHandler(RetrievalEvaluator evaluator,
                             KnowledgeRefiner refiner, ExternalSearchService externalSearch) {
        this.evaluator = evaluator;
        this.refiner = refiner;
        this.externalSearch = externalSearch;
    }

    public List<Document> handle(String question, List<Document> docs) {
        // 先进行基础检索
        AssessmentEnum assessment = evaluator.evaluate(question, docs);

        return switch (assessment) {
            case CORRECT -> docs;                     // 直接使用
            case AMBIGUOUS -> refiner.refine(question, docs); // 知识精炼（如二次检索或提取片段）
            case INCORRECT -> externalSearch.search(question); // 切换到外部搜索
        };
    }
}
