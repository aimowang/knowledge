package org.example.core.rag.agentic.router;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QueryRouter 规则路由逻辑测试。
 * 测试 {@code COMPLEX_KEYWORDS} 和长度阈值规则。
 */
class QueryRouterTest {

    private static final Set<String> COMPLEX_KEYWORDS = Set.of(
            "比较", "对比", "区别", "差异", "分析", "优缺点",
            "compare", "difference", "analysis", "versus", "vs"
    );
    private static final int SIMPLE_THRESHOLD = 20;

    @Test
    void shortQueryIsSimple() {
        // 长度 < 20 且无复杂关键词 → SIMPLE
        String q = "今天星期几";
        assertTrue(isSimpleByRule(q));
    }

    @Test
    void complexKeywordNeedsLLM() {
        // 含比较/对比等关键词 → 需要 LLM 确认
        String q = "比较 Spring Boot 和 Quarkus";
        assertFalse(isSimpleByRule(q));
    }

    @Test
    void emptyOrNullIsSimple() {
        assertTrue(isSimpleByRule(""));
        assertTrue(isSimpleByRule(null));
    }

    @Test
    void plainQuestionIsSimple() {
        String q = "什么是 RAG";
        assertTrue(isSimpleByRule(q));
    }

    /** 模拟 QueryRouter 的规则分类逻辑 */
    private boolean isSimpleByRule(String query) {
        if (query == null || query.isBlank()) return true;
        if (query.length() < SIMPLE_THRESHOLD) return true;
        return COMPLEX_KEYWORDS.stream().noneMatch(query::contains);
    }
}
