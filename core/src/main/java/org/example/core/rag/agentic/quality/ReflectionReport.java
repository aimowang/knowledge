package org.example.core.rag.agentic.quality;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 自反思报告 — SelfReflection 的输出。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReflectionReport {

    /** 是否存在问题 */
    private boolean hasIssues;

    /** 无引用支持的关键主张列表 */
    private List<String> uncitedClaims;

    /** 未覆盖的子查询列表 */
    private List<String> uncoveredSubQueries;

    /** 矛盾点列表（内部矛盾或与材料的矛盾） */
    private List<String> contradictions;

    /** 是否有问题 */
    public boolean hasIssues() {
        return hasIssues;
    }
}
