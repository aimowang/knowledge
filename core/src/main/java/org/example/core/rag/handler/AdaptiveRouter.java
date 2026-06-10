package org.example.core.rag.handler;

import org.example.core.rag.QueryComplexityClassifier;
import org.example.model.enums.ComplexityLevelEnum;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AdaptiveRouter {
    private final QueryComplexityClassifier classifier;
    // 注入不同的回答处理器（SimpleGenerator, ModerateHandler, ComplexHandler）
    private final SimpleAnswerGenerator simpleGen;
    private final ModerateRAGHandler moderateHandler;
    private final ComplexRAGHandler complexHandler;

    public AdaptiveRouter(QueryComplexityClassifier classifier,
                          SimpleAnswerGenerator simpleGen,
                          ModerateRAGHandler moderateHandler,
                          ComplexRAGHandler complexHandler) {
        this.classifier = classifier;
        this.simpleGen = simpleGen;
        this.moderateHandler = moderateHandler;
        this.complexHandler = complexHandler;
    }

    public String routeAndAnswer(String question, List<Document> docs) {
        ComplexityLevelEnum complexity = classifier.classify(question);
        return switch (complexity) {
            case SIMPLE -> simpleGen.handle(question,docs);
            case MODERATE, COMPLEX -> moderateHandler.handle(question, docs);
        };
    }
}
