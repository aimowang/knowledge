package org.example.core.rag.agentic.trajectory;

import org.example.core.rag.agentic.agent.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 轨迹记录器 — 记录 Agent 执行轨迹到数据库。
 *
 * <p>Phase 1 实现内存记录，Phase 3 完善数据库持久化。
 */
@Component
public class TrajectoryRecorder {

    private static final Logger log = LoggerFactory.getLogger(TrajectoryRecorder.class);

    /**
     * 记录 Agent 执行轨迹。
     */
    public void record(AgentState state) {
        log.info("轨迹记录: id={}, query='{}', status={}, loops={}, duration={}ms",
            state.getTrajectoryId(),
            truncate(state.getOriginalQuery(), 50),
            state.getStatus(),
            state.getLoopCount(),
            state.getTotalDurationMs());
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
