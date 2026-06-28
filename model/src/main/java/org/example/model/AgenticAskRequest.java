package org.example.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Agentic RAG 问答请求 — 支持自定义 Agent 配置。
 */
@Data
public class AgenticAskRequest {
    /** 用户标识 */
    private String userId;

    /** 用户查询 */
    private String question;

    /** Agent 配置（可选） */
    private AgenticConfig config;

    @Data
    public static class AgenticConfig {
        private Integer maxLoops;
        private Long timeoutMs;
        private Boolean enableExternalSearch;
        private Boolean forceAgentic;
    }
}
