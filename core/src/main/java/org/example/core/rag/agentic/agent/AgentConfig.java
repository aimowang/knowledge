package org.example.core.rag.agentic.agent;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 配置模型 — 映射 application.yml 中 agentic-ag 配置段。
 *
 * <p>包含循环控制、工具配置、质量门禁、工作区、Hook 管道、沙箱等全部配置项。
 */
@Data
@Component
@ConfigurationProperties(prefix = "agentic-rag")
public class AgentConfig {

    // ── 全局开关 ──
    private boolean enabled = true;

    // ── Agent 引擎配置 ──
    private Agent agent = new Agent();

    // ── 工具配置 ──
    private Tool tool = new Tool();

    // ── 工作区配置 ──
    private Workspace workspace = new Workspace();

    // ── 质量保障配置 ──
    private Quality quality = new Quality();

    // ── Hook 管道配置 ──
    private Hook hook = new Hook();

    // ── 沙箱配置 ──
    private Sandbox sandbox = new Sandbox();

    // ── 路由配置 ──
    private Routing routing = new Routing();

    // ── 轨迹配置 ──
    private Trajectory trajectory = new Trajectory();

    // ── 内部配置类 ──

    @Data
    public static class Agent {
        private String name = "KnowledgeAssistant";
        private String model = "qwen-max";
        private int maxLoops = 5;
        private int maxContextRetries = 3;
        private int maxRepairRetries = 2;
        private int totalStepsLimit = 20;
        private long maxTimeoutMs = 30000;
        private long singleToolTimeoutMs = 15000;
        private boolean enableStreaming = true;
    }

    @Data
    public static class Tool {
        private VectorSearch vectorSearch = new VectorSearch();
        private Bm25Search bm25Search = new Bm25Search();
        private ExternalSearch externalSearch = new ExternalSearch();
        private SqlQuery sqlQuery = new SqlQuery();
        private MemoryQuery memoryQuery = new MemoryQuery();

        @Data
        public static class VectorSearch {
            private int topK = 5;
            private double similarityThreshold = 0.0;
        }

        @Data
        public static class Bm25Search {
            private int topK = 5;
        }

        @Data
        public static class ExternalSearch {
            private int topK = 3;
            private boolean enabled = false;
        }

        @Data
        public static class SqlQuery {
            private int maxRows = 1000;
            private long queryTimeoutMs = 5000;
            private boolean enabled = false;
        }

        @Data
        public static class MemoryQuery {
            private int topK = 5;
            private boolean enabled = false;
        }
    }

    @Data
    public static class Workspace {
        private String path = "./workspace/knowledge-agent";
        private boolean autoInit = true;
        private Memory memory = new Memory();

        @Data
        public static class Memory {
            private boolean enabled = true;
            private int consolidationInterval = 3600;
            private int maxDailyLogs = 30;
            private double importanceThreshold = 0.6;
        }
    }

    @Data
    public static class Quality {
        private ContextCheck contextCheck = new ContextCheck();
        private boolean selfReflection = true;
        private boolean correctiveRepair = true;
        private LlmJudge llmJudge = new LlmJudge();

        @Data
        public static class ContextCheck {
            private boolean enabled = true;
            private int maxRetries = 3;
            private int improvementThreshold = 2;
        }

        @Data
        public static class LlmJudge {
            private boolean enabled = false;
            private Thresholds thresholds = new Thresholds();

            @Data
            public static class Thresholds {
                private double faithfulness = 0.7;
                private double answerRelevancy = 0.6;
                private double citationGrounding = 0.8;
            }
        }
    }

    @Data
    public static class Hook {
        private Compaction compaction = new Compaction();
        private TokenCounter tokenCounter = new TokenCounter();
        private RateLimit rateLimit = new RateLimit();
        private Safety safety = new Safety();

        @Data
        public static class Compaction {
            private boolean enabled = true;
            private int maxHistoryLength = 50;
            private int maxTokens = 4096;
            private String strategy = "semantic";
            private int preserveLastN = 5;
        }

        @Data
        public static class TokenCounter {
            private boolean enabled = true;
            private int maxPromptTokens = 8192;
            private int maxResponseTokens = 2048;
        }

        @Data
        public static class RateLimit {
            private boolean enabled = false;
            private int maxCallsPerMinute = 30;
        }

        @Data
        public static class Safety {
            private boolean enabled = false;
        }
    }

    @Data
    public static class Sandbox {
        private boolean enabled = false;
        private String mode = "gVisor";
        private String defaultTools = "NONE";
        private List<String> sandboxTools = new ArrayList<>();
        private String memoryLimit = "512m";
        private double cpuLimit = 1.0;
        private boolean networkAccess = false;
    }

    @Data
    public static class Routing {
        private String strategy = "rule_llm";
        private boolean forceAgentic = false;
        private boolean forceWorkflow = false;
        private int simpleThreshold = 20;
    }

    @Data
    public static class Trajectory {
        private boolean enabled = true;
        private int retentionDays = 30;
    }
}
