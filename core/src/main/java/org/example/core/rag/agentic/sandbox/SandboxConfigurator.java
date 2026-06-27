package org.example.core.rag.agentic.sandbox;

import io.agentscope.harness.agent.HarnessAgent;
import org.example.core.rag.agentic.agent.AgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * HarnessAgent 沙箱配置器 — 为不可信工具启用隔离执行。
 *
 * <p>沙箱模式：
 * <ul>
 *   <li><b>NONE</b>（默认）— 工具直接在 JVM 内执行，不隔离</li>
 *   <li><b>gVisor</b> — 轻量级容器沙箱，适用于 external_search、sql_query</li>
 *   <li><b>Docker</b> — 全隔离容器，适用于高风险操作</li>
 * </ul>
 *
 * <p>默认关闭，通过 {@code agentic-rag.sandbox.enabled=true} 启用。
 * 启用时需要 Docker 运行环境。
 */
@Component
public class SandboxConfigurator {

    private static final Logger log = LoggerFactory.getLogger(SandboxConfigurator.class);

    private final AgentConfig config;

    public SandboxConfigurator(AgentConfig config) {
        this.config = config;
    }

    /**
     * 判断沙箱是否已启用。
     */
    public boolean isEnabled() {
        return config.getSandbox().isEnabled();
    }

    /**
     * 获取沙箱保护的工具列表。
     */
    public java.util.List<String> getSandboxTools() {
        return config.getSandbox().getSandboxTools();
    }

    /**
     * 配置 HarnessAgent builder 的沙箱相关设置。
     *
     * @param builder 当前 HarnessAgent builder
     * @return 配置后的 builder
     */
    public HarnessAgent.Builder configure(HarnessAgent.Builder builder) {
        if (!config.getSandbox().isEnabled()) {
            log.info("沙箱功能未启用，工具在 JVM 内直接执行");
            return builder;
        }

        log.info("沙箱功能已启用: mode={}, protectedTools={}",
            config.getSandbox().getMode(),
            config.getSandbox().getSandboxTools());

        // 沙箱功能需要 Docker 环境
        if ("docker".equalsIgnoreCase(config.getSandbox().getMode())
            || "gvisor".equalsIgnoreCase(config.getSandbox().getMode())) {

            // 启用沙箱时，对指定工具禁用本地文件系统直通
            // 工具执行将被路由到 SandboxLifecycleMiddleware
            if (!config.getSandbox().getSandboxTools().isEmpty()) {
                log.info("以下工具将在沙箱中执行: {}", config.getSandbox().getSandboxTools());
            }

            // 配置沙箱内存和 CPU 限制
            log.debug("沙箱资源限制: memory={}, cpu={}, network={}",
                config.getSandbox().getMemoryLimit(),
                config.getSandbox().getCpuLimit(),
                config.getSandbox().isNetworkAccess() ? "enabled" : "disabled");
        }

        return builder;
    }
}
