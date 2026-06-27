package org.example.core.rag.agentic.tool;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 工具注册表 — 统一管理 AgentTool 生命周期。
 *
 * <p>职责：
 * <ol>
 *   <li>收集 Spring 容器中的 AgentTool Bean</li>
 *   <li>供给 HarnessAgent 通过 .tools() 注册</li>
 *   <li>提供工具描述格式化（用于日志和调试）</li>
 * </ol>
 *
 * <p>使用方式：
 * <pre>
 * HarnessAgent.builder()
 *     .tools(toolRegistry.getAllTools().toArray(new AgentTool[0]))
 *     .build();
 * </pre>
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();
    private final List<AgentTool> toolList;

    public ToolRegistry(List<AgentTool> toolList) {
        this.toolList = toolList;
    }

    @PostConstruct
    public void init() {
        registerAll(toolList);
        log.info("ToolRegistry 初始化完成，已注册 {} 个工具: {}",
            tools.size(), tools.keySet());
    }

    /**
     * 注册单个工具。
     */
    public void register(AgentTool tool) {
        if (tool == null) {
            return;
        }
        AgentTool existing = tools.put(tool.getName(), tool);
        if (existing != null) {
            log.warn("工具 '{}' 被重复注册，已覆盖", tool.getName());
        }
    }

    /**
     * 批量注册。
     */
    public void registerAll(List<AgentTool> toolList) {
        if (toolList == null) {
            return;
        }
        toolList.forEach(this::register);
    }

    /**
     * 按名称获取工具。
     */
    public AgentTool getTool(String name) {
        return tools.get(name);
    }

    /**
     * 获取所有可用工具（过滤不可用）。
     */
    public List<AgentTool> getAllTools() {
        return tools.values().stream()
            .filter(AgentTool::isAvailable)
            .collect(Collectors.toList());
    }

    /**
     * 获取所有已注册工具的名称列表。
     */
    public Set<String> getToolNames() {
        return tools.keySet();
    }

    /**
     * 格式化工具描述文本（用于日志和调试）。
     */
    public String getToolDescriptionsForPrompt() {
        return getAllTools().stream()
            .map(t -> {
                String schema = t.getParametersSchema().entrySet().stream()
                    .map(e -> "    " + e.getKey() + ": " + e.getValue())
                    .collect(Collectors.joining("\n"));
                return String.format("### %s\n描述: %s\n参数:\n%s",
                    t.getName(), t.getDescription(), schema);
            })
            .collect(Collectors.joining("\n\n"));
    }

    /**
     * 获取工具数量。
     */
    public int size() {
        return tools.size();
    }
}
