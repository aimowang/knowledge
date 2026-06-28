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

            // 构建 DockerFilesystemSpec 并注入 builder
            String memLimit = config.getSandbox().getMemoryLimit();
            long memBytes = parseMemoryToBytes(memLimit);
            double cpuLimit = config.getSandbox().getCpuLimit();

            io.agentscope.harness.agent.sandbox.impl.docker.DockerFilesystemSpec fsSpec =
                new io.agentscope.harness.agent.sandbox.impl.docker.DockerFilesystemSpec();
            fsSpec.memorySizeBytes(memBytes);
            fsSpec.cpuCount((long) Math.ceil(cpuLimit));
            if (config.getSandbox().isNetworkAccess()) {
                fsSpec.network("host");
            } else {
                fsSpec.network("none");
            }
            builder.filesystem(fsSpec);
            log.info("DockerFilesystemSpec 已配置: memory={}, cpu={}", memLimit, cpuLimit);
        }

        return builder;
    }

    /**
     * 将内存大小字符串（如 "512m", "2g"）解析为字节数。
     */
    private long parseMemoryToBytes(String memory) {
        if (memory == null || memory.isBlank()) return 512L * 1024 * 1024;
        memory = memory.trim().toLowerCase();
        try {
            if (memory.endsWith("g") || memory.endsWith("gb")) {
                return Long.parseLong(memory.replaceAll("[^0-9]", "")) * 1024L * 1024 * 1024;
            } else if (memory.endsWith("m") || memory.endsWith("mb")) {
                return Long.parseLong(memory.replaceAll("[^0-9]", "")) * 1024L * 1024;
            } else if (memory.endsWith("k") || memory.endsWith("kb")) {
                return Long.parseLong(memory.replaceAll("[^0-9]", "")) * 1024L;
            }
            return Long.parseLong(memory);
        } catch (NumberFormatException e) {
            log.warn("无法解析内存大小: {}, 使用默认 512m", memory);
            return 512L * 1024 * 1024;
        }
    }
}
