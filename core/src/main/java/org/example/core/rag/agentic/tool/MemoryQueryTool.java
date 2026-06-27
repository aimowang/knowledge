package org.example.core.rag.agentic.tool;

import org.example.core.memory.LongTermMemoryManager;
import org.example.core.memory.ShortTermMemoryManager;
import org.example.core.rag.agentic.agent.AgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 记忆查询工具 — 包装 ShortTermMemoryManager + LongTermMemoryManager。
 *
 * <p>工具定义：
 * <ul>
 *   <li>name: "memory_query"</li>
 *   <li>description: 查询用户的会话历史和长期记忆</li>
 *   <li>params: query(必填)</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "agentic-rag.tool.memory-query.enabled",
                       havingValue = "true", matchIfMissing = false)
public class MemoryQueryTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(MemoryQueryTool.class);

    private final ShortTermMemoryManager shortTermMemory;
    private final LongTermMemoryManager longTermMemory;
    private final AgentConfig config;

    public MemoryQueryTool(ShortTermMemoryManager shortTermMemory,
                           LongTermMemoryManager longTermMemory,
                           AgentConfig config) {
        this.shortTermMemory = shortTermMemory;
        this.longTermMemory = longTermMemory;
        this.config = config;
    }

    @Override
    public String getName() { return "memory_query"; }

    @Override
    public String getDescription() {
        return "查询用户的会话历史和长期记忆（偏好、事实、上下文）。" +
               "适用于个性化回答和需要了解用户背景的场景。" +
               "返回记忆内容文本。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
            "query", Map.of(
                "type", "string", "required", true,
                "description", "要查询的记忆内容"
            ),
            "userId", Map.of(
                "type", "string", "required", true,
                "description", "用户标识"
            )
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolResult execute(Map<String, Object> params) {
        long startTime = System.currentTimeMillis();
        try {
            String query = (String) params.get("query");
            String userId = (String) params.get("userId");

            if (query == null || query.isBlank()) {
                return ToolResult.failure("查询参数 'query' 不能为空");
            }
            if (userId == null || userId.isBlank()) {
                return ToolResult.failure("查询参数 'userId' 不能为空");
            }

            int topK = config.getTool().getMemoryQuery().getTopK();
            long duration = System.currentTimeMillis() - startTime;

            // 1. 查询短期记忆（最近对话历史摘要）
            String shortTermInfo = queryShortTermMemory(userId);

            // 2. 查询长期记忆（偏好、事实等）
            String longTermInfo = queryLongTermMemory(userId, query, topK);

            // 3. 合并结果
            StringBuilder result = new StringBuilder();
            if (!shortTermInfo.isBlank()) {
                result.append("【会话历史】\n").append(shortTermInfo).append("\n\n");
            }
            if (!longTermInfo.isBlank()) {
                result.append("【长期记忆】\n").append(longTermInfo);
            }
            if (result.isEmpty()) {
                result.append("未找到相关记忆。");
            }

            log.debug("MemoryQueryTool 完成: query='{}', userId='{}'", query, userId);
            return ToolResult.success(result.toString(), duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("MemoryQueryTool 执行失败: {}", e.getMessage());
            return ToolResult.failure("记忆查询失败: " + e.getMessage());
        }
    }

    private String queryShortTermMemory(String userId) {
        try {
            var history = shortTermMemory.getHistory(userId);
            if (history == null || history.isEmpty()) return "";
            return history.stream()
                .map(msg -> msg.getRole() + ": " + msg.getContent())
                .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.warn("短期记忆查询失败: {}", e.getMessage());
            return "";
        }
    }

    private String queryLongTermMemory(String userId, String query, int topK) {
        try {
            var memories = longTermMemory.getRelevantMemories(userId, query);
            if (memories == null || memories.isEmpty()) return "";
            return memories.stream()
                .map(m -> "[" + m.getType() + "] " + m.getContent()
                    + " (重要性: " + m.getImportance() + ")")
                .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.warn("长期记忆查询失败: {}", e.getMessage());
            return "";
        }
    }

    @Override
    public boolean isAvailable() {
        return config.getTool().getMemoryQuery().isEnabled();
    }
}
