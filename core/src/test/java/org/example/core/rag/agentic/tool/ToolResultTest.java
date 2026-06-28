package org.example.core.rag.agentic.tool;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ToolResultTest {

    @Test
    void shouldCreateSuccessResult() {
        ToolResult r = ToolResult.success("data", 100);
        assertTrue(r.isSuccess());
        assertEquals("data", r.getData());
        assertEquals(100, r.getDurationMs());
    }

    @Test
    void shouldCreateFailureResult() {
        ToolResult r = ToolResult.failure("error");
        assertFalse(r.isSuccess());
        assertNull(r.getData());
        assertNotNull(r.getErrorMessage());
    }

    @Test
    void shouldDefaultDurationToZero() {
        ToolResult r = ToolResult.success("ok");
        assertEquals(0, r.getDurationMs());
    }
}
