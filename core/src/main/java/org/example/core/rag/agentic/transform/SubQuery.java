package org.example.core.rag.agentic.transform;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 子查询模型 — 查询分解 (FR-12.1) 的输出单元。
 *
 * <p>包含子查询文本、依赖关系和同义变体。无依赖的子查询可并行执行。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubQuery {

    /** 子查询唯一标识 */
    private String id;

    /** 子查询文本 */
    private String query;

    /** 依赖的子查询 ID 列表（前置条件） */
    private List<String> dependsOn;

    /** 同义变体（用于 Multi-Query 扩展） */
    private List<String> variants;
}
