package org.example.core.rag.orchestrator;

import lombok.Builder;
import lombok.Data;

/**
 * RAG 编排器配置
 * 统一管理功能开关
 */
@Data
@Builder
public class RagOrchestratorConfig {
    
    /**
     * 是否启用短期记忆
     */
    @Builder.Default
    private boolean shortTermMemoryEnabled = false;
    
    /**
     * 是否启用长期记忆
     */
    @Builder.Default
    private boolean longTermMemoryEnabled = false;
    
    /**
     * 是否启用质量评估
     */
    @Builder.Default
    private boolean evaluationEnabled = false;
    
    /**
     * 创建默认配置（全部禁用）
     */
    public static RagOrchestratorConfig defaultConfig() {
        return RagOrchestratorConfig.builder().build();
    }
    
    /**
     * 创建全启用配置
     */
    public static RagOrchestratorConfig allEnabled() {
        return RagOrchestratorConfig.builder()
            .shortTermMemoryEnabled(true)
            .longTermMemoryEnabled(true)
            .evaluationEnabled(true)
            .build();
    }
}
