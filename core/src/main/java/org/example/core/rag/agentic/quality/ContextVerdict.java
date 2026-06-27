package org.example.core.rag.agentic.quality;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 上下文完备性判定结果。
 */
@Data
@AllArgsConstructor
public class ContextVerdict {

    /** 上下文是否足以回答问题 */
    private final boolean sufficient;

    /** 缺失的信息描述（sufficient=false 时有效） */
    private final String missingInfo;

    /** 建议的补充搜索词 */
    private final String suggestedQuery;

    public static ContextVerdict sufficient() {
        return new ContextVerdict(true, null, null);
    }

    public static ContextVerdict insufficient(String missingInfo, String suggestedQuery) {
        return new ContextVerdict(false, missingInfo, suggestedQuery);
    }

    public boolean isSufficient() {
        return sufficient;
    }

    public String getMissingInfoQuery() {
        return suggestedQuery != null ? suggestedQuery : missingInfo;
    }
}
