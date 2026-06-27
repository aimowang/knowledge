package org.example.core.rag.agentic.trajectory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.core.rag.agentic.agent.AgentConfig;
import org.example.core.rag.agentic.agent.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 轨迹记录器 — 记录 Agent 执行轨迹到 MySQL 的 agent_trajectories 表。
 *
 * <p>HarnessAgent 自动通过 SessionPersistenceHook 将原始对话写入
 * workspace/sessions/ JSONL 文件；本记录器补充业务级的结构化轨迹，
 * 包含质量评分、工具调用明细、执行步骤等，供 API 查询和审计使用。
 */
@Component
public class TrajectoryRecorder {

    private static final Logger log = LoggerFactory.getLogger(TrajectoryRecorder.class);

    private final TrajectoryRepository repository;
    private final ObjectMapper objectMapper;
    private final AgentConfig config;

    public TrajectoryRecorder(TrajectoryRepository repository,
                              ObjectMapper objectMapper,
                              AgentConfig config) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.config = config;
    }

    /**
     * 记录 Agent 执行轨迹到数据库。
     */
    public void record(AgentState state) {
        if (!config.getTrajectory().isEnabled()) {
            log.debug("轨迹记录已禁用，跳过");
            return;
        }

        try {
            TrajectoryEntity entity = new TrajectoryEntity();
            entity.setId(state.getTrajectoryId());
            entity.setUserId(state.getUserId());
            entity.setQuery(state.getOriginalQuery());
            entity.setTrajectoryJson(serializeTrajectory(state));
            entity.setTotalSteps(state.getTrajectory().size());
            entity.setTotalLoops(state.getLoopCount());
            entity.setTotalDurationMs(state.getTotalDurationMs());
            entity.setToolsUsed(serializeToolsUsed(state));
            entity.setQualityScores(serializeQualityScores(state));
            entity.setStatus(state.getStatus().name());
            entity.setErrorMessage(state.getError());
            entity.setArchived(false);

            repository.save(entity);

            log.info("轨迹持久化完成: id={}, query='{}', status={}, loops={}, duration={}ms",
                state.getTrajectoryId(),
                truncate(state.getOriginalQuery(), 50),
                state.getStatus(),
                state.getLoopCount(),
                state.getTotalDurationMs());

        } catch (Exception e) {
            log.error("轨迹持久化失败: {}", e.getMessage());
        }
    }

    /**
     * 序列化轨迹步骤列表为 JSON。
     */
    private String serializeTrajectory(AgentState state) {
        try {
            List<Map<String, Object>> steps = state.getTrajectory().stream()
                .map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("step", s.getStepNumber());
                    m.put("loop", s.getLoopNumber());
                    m.put("type", s.getType());
                    m.put("description", s.getDescription());
                    m.put("durationMs", s.getDurationMs());
                    if (s.getToolName() != null) m.put("toolName", s.getToolName());
                    if (s.getToolInput() != null) m.put("toolInput", s.getToolInput());
                    return m;
                })
                .collect(Collectors.toList());

            Map<String, Object> root = new LinkedHashMap<>();
            root.put("query", state.getOriginalQuery());
            root.put("steps", steps);

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (JsonProcessingException e) {
            log.warn("轨迹序列化失败: {}", e.getMessage());
            return "[]";
        }
    }

    /**
     * 序列化使用的工具列表。
     */
    private String serializeToolsUsed(AgentState state) {
        try {
            List<String> tools = state.getToolCalls().stream()
                .map(tc -> tc.getToolName())
                .distinct()
                .collect(Collectors.toList());
            return objectMapper.writeValueAsString(tools);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    /**
     * 序列化质量评分。
     */
    private String serializeQualityScores(AgentState state) {
        if (state.getQualityScores() == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(state.getQualityScores());
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * 根据 trajectoryId 反查轨迹（Phase 3 完善反序列化）。
     */
    public void replay(String trajectoryId) {
        repository.findById(trajectoryId).ifPresent(entity ->
            log.info("轨迹回放: id={}, status={}", entity.getId(), entity.getStatus()));
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
