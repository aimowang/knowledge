package org.example.core.rag.agentic.config;

import org.example.core.rag.agentic.agent.AgentConfig;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Agentic RAG Spring 配置类。
 *
 * <p>负责扫描 agentic 包下的组件。AgentOrchestrator 和 HarnessAgent
 * 的构建由各组件自身完成，本配置类仅提供 Spring 组件扫描支持。
 */
@Configuration
@ComponentScan(basePackages = "org.example.core.rag.agentic")
public class AgenticRagConfig {

    private final AgentConfig agentConfig;

    public AgenticRagConfig(AgentConfig agentConfig) {
        this.agentConfig = agentConfig;
    }
}
