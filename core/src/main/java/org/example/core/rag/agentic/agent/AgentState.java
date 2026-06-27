package org.example.core.rag.agentic.agent;

import lombok.Data;
import org.example.core.rag.agentic.quality.ContextVerdict;
import org.example.core.rag.agentic.quality.ReflectionReport;
import org.example.core.rag.agentic.quality.QualityScores;
import org.example.core.rag.agentic.tool.ToolResult;
import org.example.core.rag.agentic.trajectory.StepRecord;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 执行状态模型 — 贯穿整个 Agent 执行周期的数据容器。
 *
 * <p>分工说明：
 * <ul>
 *   <li>AgentState — 管理 RAG 问答流程的业务状态（查询、工具结果、答案草稿等）</li>
 *   <li>HarnessAgent RuntimeContext — 管理运行时上下文（userId/traceId/sessionId）</li>
 * </ul>
 */
@Data
public class AgentState {

    // ════════════════════════════════════════════════════════════
    // 输入层
    // ════════════════════════════════════════════════════════════
    /** 用户原始查询 */
    private final String originalQuery;

    /** 用户标识 */
    private final String userId;

    /** Agent 配置（当前执行使用的配置快照） */
    private final AgentConfig config;

    /** 轨迹唯一 ID */
    private final String trajectoryId;

    // ════════════════════════════════════════════════════════════
    // 执行层
    // ════════════════════════════════════════════════════════════
    /** 已执行的工具调用列表 */
    private final List<ToolCallRecord> toolCalls = new ArrayList<>();

    /** 综合后的上下文文本 */
    private String synthesizedContext;

    /** 引用来源文档列表 */
    private final List<Document> sources = new ArrayList<>();

    // ════════════════════════════════════════════════════════════
    // 质量检查层
    // ════════════════════════════════════════════════════════════
    /** 上下文完备性判定 */
    private ContextVerdict contextVerdict;

    /** 完备性重试次数 */
    private int contextRetryCount;

    // ════════════════════════════════════════════════════════════
    // 生成层
    // ════════════════════════════════════════════════════════════
    /** 答案草稿 */
    private String draftAnswer;

    /** 自反思报告 */
    private ReflectionReport reflectionReport;

    /** 修复次数 */
    private int repairCount;

    /** Agent 原始响应（HarnessAgent 返回的完整文本） */
    private String agentRawResponse;

    // ════════════════════════════════════════════════════════════
    // 最终输出层
    // ════════════════════════════════════════════════════════════
    /** 最终答案 */
    private String finalAnswer;

    /** 质量评分 */
    private QualityScores qualityScores;

    /** 质量门禁是否未通过 */
    private boolean qualityGateFailed;

    // ════════════════════════════════════════════════════════════
    // 轨迹与控制层
    // ════════════════════════════════════════════════════════════
    /** 执行步骤列表 */
    private final List<StepRecord> trajectory = new ArrayList<>();

    /** 循环轮次 */
    private int loopCount;

    /** 执行状态 */
    private AgentStatus status = AgentStatus.RUNNING;

    /** 错误信息 */
    private String error;

    /** 总耗时 (ms) */
    private long totalDurationMs;

    /** 开始时间戳 */
    private final long startTimeMs = System.currentTimeMillis();

    // ════════════════════════════════════════════════════════════
    // 构造函数
    // ════════════════════════════════════════════════════════════

    public AgentState(String originalQuery, String userId, AgentConfig config) {
        this.originalQuery = originalQuery;
        this.userId = userId;
        this.config = config;
        this.trajectoryId = generateTrajectoryId();
    }

    // ════════════════════════════════════════════════════════════
    // 业务方法
    // ════════════════════════════════════════════════════════════

    /**
     * 判断是否超时。
     */
    public boolean isTimeout() {
        return System.currentTimeMillis() - startTimeMs > config.getAgent().getMaxTimeoutMs();
    }

    /** 增加循环计数 */
    public void incrementLoopCount() {
        this.loopCount++;
    }

    /** 增加修复计数 */
    public void incrementRepairCount() {
        this.repairCount++;
    }

    /** 增加上下文重试计数 */
    public void incrementContextRetryCount() {
        this.contextRetryCount++;
    }

    /**
     * 合并补充检索结果到上下文。
     */
    public void mergeContext(String additionalContent) {
        if (additionalContent == null || additionalContent.isBlank()) {
            return;
        }
        if (this.synthesizedContext == null || this.synthesizedContext.isBlank()) {
            this.synthesizedContext = additionalContent;
        } else {
            this.synthesizedContext += "\n\n--- 补充检索 ---\n\n" + additionalContent;
        }
    }

    /**
     * 添加工具调用记录。
     */
    public void addToolCall(String toolName, Map<String, Object> params, ToolResult result) {
        toolCalls.add(new ToolCallRecord(toolName, params, result));
    }

    /**
     * 添加轨迹步骤。
     */
    public void addStepRecord(StepRecord record) {
        trajectory.add(record);
    }

    /**
     * 添加引用来源。
     */
    public void addSource(Document doc) {
        if (doc != null) {
            sources.add(doc);
        }
    }

    /**
     * 获取当前已耗时。
     */
    public long getElapsedMs() {
        return System.currentTimeMillis() - startTimeMs;
    }

    // ════════════════════════════════════════════════════════════
    // 内部类
    // ════════════════════════════════════════════════════════════

    @Data
    public static class ToolCallRecord {
        private final String toolName;
        private final java.util.Map<String, Object> params;
        private final ToolResult result;
    }

    // ════════════════════════════════════════════════════════════
    // 私有方法
    // ════════════════════════════════════════════════════════════

    private static String generateTrajectoryId() {
        String date = new java.text.SimpleDateFormat("yyyyMMdd").format(new java.util.Date());
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "traj_" + date + "_" + uuid;
    }
}
