package org.example.core.rag.agentic.tool;

import org.example.core.rag.agentic.agent.AgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Text-to-SQL 查询工具 — 将自然语言转为 SQL 并执行。
 *
 * <p>工具定义：
 * <ul>
 *   <li>name: "sql_query"</li>
 *   <li>description: 通过自然语言查询关系数据库中的结构化数据</li>
 *   <li>params: query(必填), table_hint(选填)</li>
 * </ul>
 *
 * <p>安全约束：
 * <ul>
 *   <li>只允许 SELECT 查询（关键词校验拦截）</li>
 *   <li>查询超时 5s</li>
 *   <li>行数限制 1000 行</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "agentic-rag.tool.sql-query.enabled",
                       havingValue = "true", matchIfMissing = false)
public class SqlQueryTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(SqlQueryTool.class);

    private static final List<String> FORBIDDEN_KEYWORDS = List.of(
        "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "CREATE",
        "TRUNCATE", "GRANT", "REVOKE", "EXEC", "EXECUTE"
    );

    private final JdbcTemplate jdbcTemplate;
    private final ChatClient chatClient;
    private final AgentConfig config;

    public SqlQueryTool(DataSource dataSource, ChatClient chatClient, AgentConfig config) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.jdbcTemplate.setQueryTimeout(
            (int) (config.getTool().getSqlQuery().getQueryTimeoutMs() / 1000));
        this.chatClient = chatClient;
        this.config = config;
    }

    @Override
    public String getName() { return "sql_query"; }

    @Override
    public String getDescription() {
        return "通过自然语言查询关系数据库中的结构化数据。" +
               "适用于统计数据、聚合查询、精确数值查找、历史记录查询。" +
               "返回结构化的文本结果。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
            "query", Map.of(
                "type", "string", "required", true,
                "description", "自然语言描述的查询需求"
            ),
            "table_hint", Map.of(
                "type", "string", "required", false,
                "description", "指定优先查询的表名"
            )
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolResult execute(Map<String, Object> params) {
        long startTime = System.currentTimeMillis();
        try {
            String naturalQuery = (String) params.get("query");
            if (naturalQuery == null || naturalQuery.isBlank()) {
                return ToolResult.failure("查询参数 'query' 不能为空");
            }

            // 1. Text-to-SQL 转换
            String sql = generateSql(naturalQuery, (String) params.get("table_hint"));
            if (sql == null || sql.isBlank()) {
                return ToolResult.failure("无法将查询转为 SQL");
            }

            // 2. SQL 安全校验
            String validationError = validateSql(sql);
            if (validationError != null) {
                log.warn("SQL 安全校验未通过: {}", validationError);
                return ToolResult.failure("查询被安全策略拒绝: " + validationError);
            }

            // 3. 执行查询
            log.debug("SqlQueryTool 执行: {}", sql);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

            // 4. 限制结果行数
            int maxRows = config.getTool().getSqlQuery().getMaxRows();
            List<Map<String, Object>> limited = rows.size() > maxRows
                ? rows.subList(0, maxRows) : rows;

            long duration = System.currentTimeMillis() - startTime;
            String result = formatResults(limited, rows.size());
            log.debug("SqlQueryTool 完成: {} 行 (共 {} 行), 耗时 {}ms",
                limited.size(), rows.size(), duration);

            return ToolResult.success(result, duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("SqlQueryTool 执行失败: {}", e.getMessage());
            return ToolResult.failure("SQL 查询失败: " + e.getMessage());
        }
    }

    @Override
    public boolean isAvailable() {
        return config.getTool().getSqlQuery().isEnabled();
    }

    private String generateSql(String naturalQuery, String tableHint) {
        String prompt = "将以下自然语言查询转为 MySQL SQL 语句。"
            + (tableHint != null ? "优先查询表: " + tableHint + "。" : "")
            + "只返回 SQL 语句，不要附加说明。";

        String sql = chatClient.prompt()
            .system(prompt)
            .user(naturalQuery)
            .call()
            .content();

        if (sql == null) return null;

        // 清理 Markdown 代码块标记
        sql = sql.trim();
        if (sql.startsWith("```")) {
            int start = sql.indexOf('\n');
            int end = sql.lastIndexOf("```");
            if (start > 0 && end > start) {
                sql = sql.substring(start, end).trim();
            }
        }
        // 去掉末尾分号
        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1);
        }
        return sql;
    }

    private String validateSql(String sql) {
        if (sql == null || sql.isBlank()) {
            return "SQL 语句为空";
        }
        String upper = sql.trim().toUpperCase();
        // 必须以 SELECT 开头
        if (!upper.startsWith("SELECT")) {
            return "只允许 SELECT 查询";
        }
        // 检查禁止关键词
        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (upper.contains(" " + keyword + " ")
                || upper.startsWith(keyword + " ")
                || upper.contains(" " + keyword)
                || upper.equals(keyword)) {
                return "包含禁止操作: " + keyword;
            }
        }
        return null;
    }

    private String formatResults(List<Map<String, Object>> rows, int totalRows) {
        if (rows.isEmpty()) {
            return "查询结果为空。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("查询结果 (").append(rows.size());
        if (totalRows > rows.size()) {
            sb.append("/共").append(totalRows);
        }
        sb.append(" 行):\n\n");

        // 表头（取第一行的 key）
        Map<String, Object> first = rows.get(0);
        String header = String.join(" | ", first.keySet());
        sb.append(header).append("\n");
        sb.append("-".repeat(header.length())).append("\n");

        // 数据行
        for (Map<String, Object> row : rows) {
            String line = row.values().stream()
                .map(v -> v != null ? v.toString() : "")
                .collect(Collectors.joining(" | "));
            sb.append(line).append("\n");
        }
        return sb.toString();
    }
}
