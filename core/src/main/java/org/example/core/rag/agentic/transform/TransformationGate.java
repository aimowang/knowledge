package org.example.core.rag.agentic.transform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * 转换门控 (FR-12.4) — 判断是否需要深度查询转换。
 *
 * <p>对简单事实性问题跳过查询分解和 Step-Back，避免过度处理。
 * 节约 Token 和延迟。
 */
public class TransformationGate {

    private static final Logger log = LoggerFactory.getLogger(TransformationGate.class);

    private static final Set<String> COMPLEX_QUESTION_WORDS = Set.of(
        "什么", "如何", "为什么", "哪个", "哪些", "怎样",
        "what", "how", "why", "which", "compare", "difference",
        "比较", "对比", "区别", "分析", "总结", "优缺点"
    );

    private final int simpleThreshold;

    public TransformationGate(int simpleThreshold) {
        this.simpleThreshold = simpleThreshold;
    }

    public TransformationGate() {
        this(20);
    }

    /**
     * 判断是否需要进行深度查询转换。
     *
     * @param query 用户原始查询
     * @return true = 需要转换（复杂查询），false = 直接检索（简单查询）
     */
    public boolean shouldTransform(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }

        // 1. 长度检查：短查询直接跳过
        if (query.length() < simpleThreshold) {
            log.debug("门控跳过: 查询过短 ({} < {})", query.length(), simpleThreshold);
            return false;
        }

        // 2. 关键词检查：含复杂疑问词则通过
        String lower = query.toLowerCase();
        boolean hasComplexWord = COMPLEX_QUESTION_WORDS.stream()
            .anyMatch(lower::contains);

        if (!hasComplexWord) {
            log.debug("门控跳过: 无复杂关键词");
            return false;
        }

        log.debug("门控通过: 需要查询转换");
        return true;
    }
}
