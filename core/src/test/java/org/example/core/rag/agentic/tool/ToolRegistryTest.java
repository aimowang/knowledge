package org.example.core.rag.agentic.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry(List.of());
        registry.init();
    }

    @Test
    void shouldRegisterAndRetrieveTool() {
        AgentTool tool = new TestTool("test_tool");
        registry.register(tool);
        assertEquals(tool, registry.getTool("test_tool"));
    }

    @Test
    void shouldReturnAllAvailableTools() {
        registry.register(new TestTool("available", true));
        registry.register(new TestTool("unavailable", false));
        assertEquals(1, registry.getAllTools().size());
        assertEquals("available", registry.getAllTools().get(0).getName());
    }

    @Test
    void shouldReturnNullForUnknownTool() {
        assertNull(registry.getTool("nonexistent"));
    }

    static class TestTool implements AgentTool {
        private final String name;
        private final boolean available;

        TestTool(String name) { this(name, true); }
        TestTool(String name, boolean available) { this.name = name; this.available = available; }

        @Override public String getName() { return name; }
        @Override public String getDescription() { return "test"; }
        @Override public Map<String, Object> getParametersSchema() { return Map.of(); }
        @Override public ToolResult execute(Map<String, Object> params) {
            return ToolResult.success("ok", 10);
        }
        @Override public boolean isAvailable() { return available; }
    }
}
