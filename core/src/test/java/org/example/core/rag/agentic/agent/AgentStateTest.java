package org.example.core.rag.agentic.agent;

import org.example.core.rag.agentic.quality.ContextVerdict;
import org.example.core.rag.agentic.quality.ReflectionReport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentStateTest {

    private final AgentConfig config = new AgentConfig();

    @Test
    void shouldInitializeWithRunningStatus() {
        AgentState state = new AgentState("test query", "user1", config);
        assertEquals("test query", state.getOriginalQuery());
        assertEquals("user1", state.getUserId());
        assertEquals(AgentStatus.RUNNING, state.getStatus());
        assertNotNull(state.getTrajectoryId());
    }

    @Test
    void shouldDetectTimeout() {
        AgentConfig fastConfig = new AgentConfig();
        fastConfig.getAgent().setMaxTimeoutMs(-1);
        AgentState state = new AgentState("test", "user1", fastConfig);
        assertTrue(state.isTimeout());
    }

    @Test
    void shouldMergeContext() {
        AgentState state = new AgentState("test", "user1", config);
        state.mergeContext("first");
        state.mergeContext("second");
        assertTrue(state.getSynthesizedContext().contains("first"));
        assertTrue(state.getSynthesizedContext().contains("second"));
    }

    @Test
    void shouldTrackLoopAndRepairCounts() {
        AgentState state = new AgentState("test", "user1", config);
        state.incrementLoopCount();
        state.incrementLoopCount();
        assertEquals(2, state.getLoopCount());
        state.incrementRepairCount();
        assertEquals(1, state.getRepairCount());
    }

    @Test
    void shouldRecordTrajectorySteps() {
        AgentState state = new AgentState("test", "user1", config);
        org.example.core.rag.agentic.trajectory.StepRecord step =
                org.example.core.rag.agentic.trajectory.StepRecord.builder()
                        .stepNumber(1).type("TOOL_CALL").build();
        state.addStepRecord(step);
        assertEquals(1, state.getTrajectory().size());
    }
}
