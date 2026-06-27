# Agentic RAG 开发规格说明书

**文档版本**: v1.0  
**编写日期**: 2026-06-14  
**项目名称**: RAG 智能知识库问答系统  
**框架选型**: AgentScope-Java 2.0.0-RC1（HarnessAgent Agent 引擎 + 工作区管理）+ Spring AI Alibaba 1.1.2.3（编排与基础设施）  
**文档类型**: 开发规格说明书

---

## 修订记录

| 版本 | 日期 | 修订内容 | 修订人 |
|------|------|---------|--------|
| v1.0 | 2026-06-14 | 初稿：基于 AgentScope-Java 的 Agentic RAG 开发规格 | - |

---

## 目录

1. [总体架构](#1-总体架构)
2. [模块结构与代码组织](#2-模块结构与代码组织)
3. [核心组件设计](#3-核心组件设计)
4. [工具框架设计](#4-工具框架设计)
5. [质量保障管道设计](#5-质量保障管道设计)
6. [状态与轨迹设计](#6-状态与轨迹设计)
7. [路由策略设计](#7-路由策略设计)
8. [接口设计](#8-接口设计)
9. [数据模型设计](#9-数据模型设计)
10. [配置设计](#10-配置设计)
11. [AgentScope-Java 集成要点](#11-agentscope-java-集成要点)

---

## 1. 总体架构

### 1.1 架构分层

```
┌──────────────────────────────────────────────────────────────────────┐
│                        API Layer (api 模块)                           │
│  KnowledgeQAController                                                │
│  POST /api/qa/ask          (Workflow RAG，保持不变)                   │
│  POST /api/qa/ask/agent    (新增：Agentic RAG)                       │
│  GET  /api/qa/trajectory/{id} (新增：轨迹查询)                       │
└───────────────────────────┬──────────────────────────────────────────┘
                            │
┌───────────────────────────▼──────────────────────────────────────────┐
│                    Routing Layer (KnowledgeQAService)                  │
│  规则分类 + LLM 分类 → SIMPLE → Workflow / COMPLEX → Agentic         │
└──────┬──────────────────────────────────────────┬─────────────────────┘
       │                                          │
       ▼                                          ▼
┌──────────────┐                    ┌──────────────────────────────────────┐
│ Workflow RAG │                    │     Agentic RAG 层 (新增)             │
│ (保留，降级)  │                    │                                      │
│              │                    │  ┌──────────────────────────────┐   │
│ Advanced     │                    │  │ AgentOrchestrator            │   │
│ BasicRagFlow │                    │  │  ├─ HarnessAgent (工作区+ReAct)│   │
└──────────────┘                    │  │  │   workspace/AGENTS.md 人格     │   │
                                    │  │  ├─ SubAgent 动态编排        │   │
                                    │  │  └─ 循环控制/超时/降级      │   │
                                    │  └──────────────────────────────┘   │
                                    │  ┌──────────────────────────────┐   │
                                    │  │ Tool 层                      │   │
                                    │  │  ├─ AgentTool 接口           │   │
                                    │  │  ├─ ToolRegistry             │   │
                                    │  │  ├─ VectorSearchTool  (P0)   │   │
                                    │  │  ├─ BM25SearchTool    (P0)   │   │
                                    │  │  ├─ ExternalSearchTool (P1)  │   │
                                    │  │  ├─ SqlQueryTool      (P1)   │   │
                                    │  │  └─ MemoryQueryTool   (P1)   │   │
                                    │  └──────────────────────────────┘   │
                                    │  ┌──────────────────────────────┐   │
                                    │  │ Quality Pipeline 层 (自研)    │   │
                                    │  │  ├─ SufficientContextAgent   │   │
                                    │  │  ├─ SelfReflection           │   │
                                    │  │  ├─ CorrectiveRepair         │   │
                                    │  │  └─ LLM Judge                │   │
                                    │  └──────────────────────────────┘   │
                                    │  ┌──────────────────────────────┐   │
                                    │  │ 状态与轨迹层                   │   │
                                    │  │  ├─ AgentState               │   │
                                    │  │  └─ TrajectoryRecorder       │   │
                                    │  └──────────────────────────────┘   │
                                    └──────────────────────────────────────┘
                                                    │
┌───────────────────────────────────────────────────▼───────────────────────┐
│                      基础设施层 (现有，复用)                                │
│  Milvus / MySQL / Redis / DashScope API / Tavily/Bing                     │
│  Resilience4j / RagMetrics / CacheService                                  │
│  ShortTermMemoryManager / LongTermMemoryManager                            │
└───────────────────────────────────────────────────────────────────────────┘
```

### 1.2 框架职责边界

| 框架 | 职责 | 关键组件 |
|------|------|---------|
| **AgentScope-Java (HarnessAgent)** | Agent 工程化封装：工作区管理、Hook 驱动生命周期、RuntimeContext 多租户、文件系统抽象（本地/沙箱）、SubAgent 动态编排、安全沙箱、自修正 ReAct 推理、双层记忆 | `HarnessAgent`, `RuntimeContext`, `AbstractFilesystem`, `SubAgent`, `sandbox`, `workspace` |
| **Spring AI Alibaba** | @AgentSkill 工具定义、DashScope 集成、Spring 基础设施 | `@AgentSkill`, `ChatClient`, Actuator, Security |
| **自研 (Quality Pipeline)** | 上下文完备性检查、自反思、纠错、LLM Judge | `SufficientContextAgent`, `SelfReflection`, `CorrectiveRepair`, `LLMJudge` |

### 1.3 关键架构决策

| 决策 ID | 决策项 | 结论 | 理由 |
|---------|--------|------|------|
| ADR-001 | Agent 推理引擎 | **AgentScope-Java HarnessAgent**（包装 ReAct 推理 + 工作区 + Hook 管道） | 内置 ReAct 循环 + 工作区驱动配置（AGENTS.md 作为 System Prompt） + Hook 管道注入能力 + RuntimeContext 多租户 |
| ADR-002 | 工具定义方式 | **HarnessAgent .tool() 注册 + workspace/skills/ 自动发现** | HarnessAgent 通过 builder 的 `.tool()` 方法注册工具，同时支持 skills/ 目录自动发现 |
| ADR-003 | 工作区策略 | **AgentScope workspace（工作区目录）** | AGENTS.md 作为 System Prompt 自动注入，MEMORY.md 自动维护，sessions/ 自动持久化会话 |
| ADR-004 | Workflow 保留 | **并行共存 → 渐进替代** | 风险可控，可灰度 |
| ADR-005 | 路由策略 | **规则 + LLM 分类** | 简单查询零额外成本 |
| ADR-006 | 循环上限 | 主循环 max 5，重检索 max 3，Repair max 2 | 防止无限循环 |
| ADR-007 | 状态管理 | **RuntimeContext + AgentState + 工作区 session 持久化** | HarnessAgent 自动处理会话持久化，AgentState 管理业务状态 |
| ADR-008 | 质量保障 | **四阶段自研管道（自定义 Hook 注入 + 外部编排）** | 分层保障，各有侧重 |
| ADR-009 | RuntimeContext 集成 | **每次 call() 传入 RuntimeContext** | 自然传递 userId/sessionId/traceId，天然多租户隔离 |

---

## 2. 模块结构与代码组织

### 2.1 新增包结构

```
core/src/main/java/org/example/core/rag/agentic/
├── AgenticRagFlow.java                        # AbstractRagFlow 子类，Agentic RAG 入口
├── agent/
│   ├── AgentOrchestrator.java                 # Agent 主循环引擎（包装 HarnessAgent）
│   ├── AgentState.java                        # Agent 状态模型（输入/中间/输出）
│   ├── AgentConfig.java                       # Agent 配置（循环上限/超时/阈值）
│   └── AgentDecision.java                     # Agent 决策结果模型
├── tool/
│   ├── AgentTool.java                         # 工具接口（适配 AgentScope Toolkit）
│   ├── ToolRegistry.java                      # 工具注册表（管理工具生命周期）
│   ├── ToolResult.java                        # 工具执行结果封装
│   ├── VectorSearchTool.java                  # 包装 HybirdContentRetriever
│   ├── Bm25SearchTool.java                    # 包装 Bm25Indexer
│   ├── ExternalSearchTool.java                # 包装 ExternalSearchService (P1)
│   ├── MemoryQueryTool.java                   # 包装 ShortTermMemoryManager + LongTermMemoryManager (P1)
│   └── SqlQueryTool.java                      # Text-to-SQL (P1)
├── quality/
│   ├── SufficientContextAgent.java            # 上下文完备性检查
│   ├── SelfReflection.java                    # 自反思（引用/覆盖/矛盾）
│   ├── CorrectiveRepair.java                  # 纠错（补充检索/修复/降级）
│   ├── LlmJudge.java                          # 运行时质量评估 LLM Judge (P1)
│   └── QualityVerdict.java                    # 质量判定结果模型
├── trajectory/
│   ├── TrajectoryRecorder.java                # 轨迹记录器
│   ├── StepRecord.java                        # 单步记录模型
│   └── TrajectoryRepository.java              # 轨迹持久化 Repository
├── router/
│   └── QueryRouter.java                       # 查询路由（规则 + LLM 分类）
└── config/
    └── AgenticRagConfig.java                  # Agentic RAG 配置类（总配置入口）
```

### 2.2 需修改的现有文件

| 文件 | 修改内容 | 变更类型 |
|------|---------|---------|
| `KnowledgeQAController.java` | 新增 `/api/qa/ask/agent` 和 `/api/qa/trajectory/{id}` 端点 | 新增方法 |
| `KnowledgeQAService.java` | 新增 Agentic 路由逻辑（分类 → 选择 Workflow / Agentic） | 新增方法 |
| `RagMetrics.java` | 新增 7 个 Agent Prometheus 指标 | 新增指标 |
| `pom.xml` (core) | 新增 AgentScope-Java 依赖 | 新增依赖 |
| `application-dev.yml` | 新增 Agentic RAG 配置段 | 新增配置 |

### 2.3 无需修改的现有文件

| 文件 | 原因 |
|------|------|
| `AdvancedRagFlow.java` | Workflow 模式保留，作为降级路径 |
| `BasicRagFlow.java` | 同上 |
| `DefaultRagPipeline.java` | Workflow 模式保留，不对 Agent 模式产生影响 |
| `HybirdContentRetriever.java` | 被 VectorSearchTool 包装调用，无需修改 |
| `Bm25Indexer.java` | 被 BM25SearchTool 包装调用，无需修改 |
| `ExternalSearchService.java` | 被 ExternalSearchTool 包装调用，无需修改 |
| `ShortTermMemoryManager.java` | 被 MemoryQueryTool 包装调用，无需修改 |
| `LongTermMemoryManager.java` | 被 MemoryQueryTool 包装调用，无需修改 |
| `ResilienceHelper.java` | 工具调用复用现有容错配置 |
| `CacheService.java` | 复用缓存基础设施，新增 Agent 决策缓存方法 |
| `RetrievalEvaluator.java` | 可复用到 SufficientContextAgent |
| `RagEvaluator.java` | 可复用到 LLM Judge |

---

## 3. 核心组件设计

### 3.1 AgenticRagFlow

```java
/**
 * Agentic RAG 入口 — AbstractRagFlow 的子类。
 * 
 * Agentic 模式不组装 Pipeline 阶段，而是通过 AgentOrchestrator
 * 执行 Agent 主循环（规划→执行→检查→反思→生成）。
 */
@Component
@ConditionalOnProperty(name = "agentic-rag.enabled", havingValue = "true", matchIfMissing = false)
public class AgenticRagFlow extends AbstractRagFlow {

    private final AgentOrchestrator orchestrator;

    public AgenticRagFlow(AgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    protected void configurePipeline(RagPipeline pipeline) {
        // Agentic 模式不使用 Pipeline 阶段
        // pipeline 保持空实现
    }

    @Override
    protected void configureOrchestrator(RagOrchestrator orchestrator) {
        // Agentic 模式使用自己的状态管理，不使用 Orchestrator 的传统 pre/post 钩子
    }

    @Override
    public RagAnswer executeRag(String question, String userId, String source) {
        AgentState state = orchestrator.execute(question, userId);
        return convertToRagAnswer(state);
    }

    private RagAnswer convertToRagAnswer(AgentState state) {
        RagAnswer answer = new RagAnswer();
        answer.setAnswer(state.getFinalAnswer());
        answer.setSources(state.getSources().stream()
            .map(s -> new Section(s.getContent(), s.getMetadata()))
            .collect(Collectors.toList()));
        // 添加 agentic 元数据
        answer.getMetadata().put("trajectoryId", state.getTrajectoryId());
        answer.getMetadata().put("loopCount", String.valueOf(state.getLoopCount()));
        answer.getMetadata().put("agenticMode", "true");
        return answer;
    }
}
```

### 3.2 AgentOrchestrator (核心引擎)

```java
/**
 * Agent 主循环引擎。
 * 
 * 核心职责：
 * 1. 基于 AgentScope-Java HarnessAgent 执行自主推理循环
 * 2. 通过 RuntimeContext 传递多租户上下文（userId/sessionId/traceId）
 * 3. 借助 workspace 工作区管理 Agent 人格（AGENTS.md）、记忆（MEMORY.md）和会话持久化
 * 4. 协调 Quality Pipeline 的各阶段（作为自定义 Hook 注入 + 外部编排）
 * 5. 控制循环边界（最大次数/超时）
 *
 * HarnessAgent 相较于裸 ReActAgent 的优势：
 * - 工作区驱动：AGENTS.md 自动注入 System Prompt
 * - 自动记忆：MEMORY.md 双层记忆（每日流水账 + 精炼长期记忆）
 * - 会话持久化：workspace/agents/<id>/sessions/ 自动记录
 * - RuntimeContext：天然的 userId/sessionId/traceId 多租户隔离
 * - Hook 管道：可注入自定义质量检查 Hook
 * - 文件系统抽象：本地/沙箱/远程无缝切换
 *
 * 执行流程：
 *   HarnessAgent 自主推理（ReAct 循环 + 工作区 + 自动记忆）
 *   → SufficientContextAgent 检查完备性
 *   → 生成答案草稿
 *   → SelfReflection 自反思
 *   → CorrectiveRepair 纠错
 *   → LLM Judge 质量评分
 *   → 返回最终答案
 */
@Component
public class AgentOrchestrator {

    private final ToolRegistry toolRegistry;
    private final AgentConfig config;
    private final TrajectoryRecorder recorder;
    private final QualityPipeline qualityPipeline;
    private final ChatClient chatClient;          // Spring AI Alibaba ChatClient

    // HarnessAgent 实例（包装 ReAct 循环 + 工作区 + Hook 管道）
    private HarnessAgent harnessAgent;

    public AgentOrchestrator(ToolRegistry toolRegistry,
                             AgentConfig config,
                             TrajectoryRecorder recorder,
                             QualityPipeline qualityPipeline,
                             ChatClient chatClient) {
        this.toolRegistry = toolRegistry;
        this.config = config;
        this.recorder = recorder;
        this.qualityPipeline = qualityPipeline;
        this.chatClient = chatClient;
        this.harnessAgent = buildHarnessAgent();
    }

    /**
     * 构建 HarnessAgent（工作区驱动配置 + 工具注册 + 自定义 Hook）。
     *
     * HarnessAgent 内部自动管理：
     * - AGENTS.md → 作为 System Prompt 注入 Agent 人格
     * - MEMORY.md → 自动维护长期记忆（每日流水账 + 精炼）
     * - workspace/agents/<id>/sessions/ → 会话历史自动持久化
     * - skills/ → 自动发现技能并注册为工具
     * - tools.json → MCP 服务器配置
     */
    private HarnessAgent buildHarnessAgent() {
        return HarnessAgent.builder()
            .name("KnowledgeAssistant")
            .model(DashScopeChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("qwen-max")
                .build())
            .workspace("./workspace/knowledge-agent")
            // 注册 Agent 工具（AgentTool 实现类）
            .tool(new VectorSearchTool())
            .tool(new BM25SearchTool())
            .tool(new ExternalSearchTool())
            // 自定义生命周期 Hook（质量门禁、监控等）
            .hook(new QualityCheckHook())       // 质量检查 Hook
            .hook(new MetricsCollectHook())     // 指标采集 Hook
            .build();
    }

    /**
     * 执行 Agent 主循环。
     *
     * 每次调用通过 RuntimeContext 传递多租户上下文：
     * - userId: 用户标识（用于记忆隔离和权限校验）
     * - traceId: 追踪标识（用于日志关联和轨迹回放）
     * - sessionId: 会话标识（用于工作区目录隔离）
     */
    public AgentState execute(String query, String userId) {
        AgentState state = new AgentState(query, userId, config);
        long startTime = System.currentTimeMillis();

        try {
            // ─────────────────────────────────────────────
            // 阶段 1: HarnessAgent 自主推理
            // HarnessAgent 内部自动完成：
            //   1. 加载 workspace/AGENTS.md 作为 System Prompt
            //   2. 加载 MEMORY.md 长期记忆
            //   3. ReAct 推理循环（分析→规划→工具调用→观察→循环）
            //   4. 调用已注册工具（vector_search, bm25_search, ...）
            //   5. 结果自动写入 workspace/sessions/ 持久化
            //   6. 每日流水账写入 workspace/memory/YYYY-MM-DD.md
            // ─────────────────────────────────────────────
            RuntimeContext ctx = RuntimeContext.builder()
                .userId(userId)
                .traceId(state.getTrajectoryId())
                .build();

            Msg response = harnessAgent.call(
                Msg.builder()
                    .textContent(query)     // HarnessAgent 自动注入 AGENTS.md 作为系统提示
                    .build(),
                ctx                          // 传入 RuntimeContext
            ).block();  // 同步等待（底层为 Mono 响应式链）

            // 从 HarnessAgent 的 RuntimeContext 中提取执行结果
            state.setAgentRawResponse(response.getTextContent());
            state.setSynthesizedContext(extractContextFromResponse(response));

            // ─────────────────────────────────────────────
            // 阶段 2: SufficientContextAgent 完备性检查
            // ─────────────────────────────────────────────
            if (config.isSufficientContextCheckEnabled()) {
                SufficientContextAgent contextAgent = new SufficientContextAgent(chatClient);
                ContextVerdict verdict = contextAgent.check(query, state.getSynthesizedContext());

                int retryCount = 0;
                while (!verdict.isSufficient() && retryCount < config.getMaxContextRetries()) {
                    // 缺失信息 → 生成重检索指令 → 重新进入 HarnessAgent 推理
                    String supplementalQuery = verdict.getMissingInfoQuery();
                    Msg supplementalResponse = harnessAgent.call(
                        Msg.builder()
                            .textContent("补充检索：" + supplementalQuery)
                            .build(),
                        ctx
                    ).block();
                    retryCount++;
                    state.incrementLoopCount();
                    state.mergeContext(supplementalResponse.getTextContent());
                    verdict = contextAgent.check(query, state.getSynthesizedContext());
                }
                state.setContextVerdict(verdict);
            }

            // ─────────────────────────────────────────────
            // 阶段 3: 生成答案草稿
            // ─────────────────────────────────────────────
            String draft = generateDraft(query, state.getSynthesizedContext());
            state.setDraftAnswer(draft);

            // ─────────────────────────────────────────────
            // 阶段 4: Self-Reflection + Corrective Repair
            // ─────────────────────────────────────────────
            if (config.isSelfReflectionEnabled()) {
                SelfReflection reflection = new SelfReflection(chatClient);
                ReflectionReport report = reflection.reflect(
                    query, state.getSubQueries(), draft, state.getSynthesizedContext());
                state.setReflectionReport(report);

                if (report.hasIssues() && config.isCorrectiveRepairEnabled()) {
                    CorrectiveRepair repair = new CorrectiveRepair(chatClient, toolRegistry);
                    int repairCount = 0;
                    while (report.hasIssues() && repairCount < config.getMaxRepairRetries()) {
                        draft = repair.repair(query, draft, report, state.getSynthesizedContext());
                        report = reflection.reflect(
                            query, state.getSubQueries(), draft, state.getSynthesizedContext());
                        repairCount++;
                        state.incrementRepairCount();
                    }
                    state.setDraftAnswer(draft);
                    state.setReflectionReport(report);
                }
            }

            // ─────────────────────────────────────────────
            // 阶段 5: LLM Judge 质量评估 (P1)
            // ─────────────────────────────────────────────
            if (config.isLlmJudgeEnabled()) {
                LlmJudge judge = new LlmJudge(chatClient);
                QualityScores scores = judge.evaluate(query, draft, state.getSynthesizedContext());
                state.setQualityScores(scores);

                if (!scores.isPassing(config.getQualityThresholds())) {
                    // 质量不达标：尝试重生成（最多 2 次）或标记降级
                    state.setQualityGateFailed(true);
                }
            }

            // ── 最终：输出 ──
            state.setFinalAnswer(state.getDraftAnswer());
            state.setStatus(AgentStatus.COMPLETED);

        } catch (TimeoutException e) {
            state.setStatus(AgentStatus.TIMEOUT);
            state.setFinalAnswer(buildFallbackAnswer(state, "处理超时，请简化问题后重试"));
        } catch (Exception e) {
            state.setStatus(AgentStatus.FAILED);
            state.setError(e.getMessage());
            state.setFinalAnswer(buildFallbackAnswer(state, "处理过程中出现错误"));
        } finally {
            state.setTotalDurationMs(System.currentTimeMillis() - startTime);
            recorder.record(state);
        }

        return state;
    }

    private String generateDraft(String query, String context) {
        return chatClient.prompt()
            .system("基于检索到的上下文信息生成准确、带引用的答案。使用 [N] 标注引用来源。")
            .user("问题: " + query + "\n\n上下文:\n" + context)
            .call()
            .getContent();
    }

    private String buildFallbackAnswer(AgentState state, String reason) {
        return String.format(
            "抱歉，%s。\n\n以下是我已找到的相关信息：\n%s",
            reason,
            state.getSynthesizedContext() != null ? state.getSynthesizedContext() : "（暂无相关信息）"
        );
    }

    /**
     * 从 HarnessAgent 响应中提取综合上下文。
     * HarnessAgent 的返回中已包含工具调用结果和推理过程。
     */
    private String extractContextFromResponse(Msg response) {
        // HarnessAgent 的响应包含所有工具调用结果和推理步骤
        // 从中提取文档内容和检索结果
        return response.getTextContent();
    }

    /**
     * 自定义 HarnessAgent 生命周期 Hook — 质量检查。
     * 在每个推理循环的关键节点注入质量检查逻辑。
     */
    public static class QualityCheckHook implements AgentHook {
        @Override
        public void onBeforeCall(RuntimeContext ctx, Msg msg) {
            // 调用前：可注入额外的上下文信息
        }

        @Override
        public void onAfterCall(RuntimeContext ctx, Msg response) {
            // 调用后：可检查响应质量
        }

        @Override
        public int getPriority() {
            return 100;  // 在 WorkspaceContextHook(900) 之后执行
        }
    }

    /**
     * 自定义 Hook — 指标采集。
     */
    public static class MetricsCollectHook implements AgentHook {
        @Override
        public void onBeforeCall(RuntimeContext ctx, Msg msg) {
            // 记录调用开始时间
        }

        @Override
        public void onAfterCall(RuntimeContext ctx, Msg response) {
            // 记录调用耗时、Token 消耗等指标
        }

        @Override
        public int getPriority() {
            return 200;
        }
    }
}
```
                        state.incrementRepairCount();
                    }
                    state.setDraftAnswer(draft);
                    state.setReflectionReport(report);
                }
            }

            // === 阶段 5: LLM Judge 质量评估 (P1) ===
            if (config.isLlmJudgeEnabled()) {
                LlmJudge judge = new LlmJudge(chatClient);
                QualityScores scores = judge.evaluate(query, draft, state.getSynthesizedContext());
                state.setQualityScores(scores);

                if (!scores.isPassing(config.getQualityThresholds())) {
                    // 质量不达标，尝试重生成（最多 2 次）
                    // 或标记降级
                    state.setQualityGateFailed(true);
                }
            }

            // === 最终：输出 ===
            state.setFinalAnswer(state.getDraftAnswer());
            state.setStatus(AgentStatus.COMPLETED);

        } catch (TimeoutException e) {
            state.setStatus(AgentStatus.TIMEOUT);
            state.setFinalAnswer(buildFallbackAnswer(state, "处理超时，请简化问题后重试"));
        } catch (Exception e) {
            state.setStatus(AgentStatus.FAILED);
            state.setError(e.getMessage());
            state.setFinalAnswer(buildFallbackAnswer(state, "处理过程中出现错误"));
        } finally {
            state.setTotalDurationMs(System.currentTimeMillis() - startTime);
            recorder.record(state);
        }

        return state;
    }

    private String buildAgentPrompt(String query, String userId) {
        return "你是一个智能知识库助手。请分析用户问题，自主决定调用哪些工具检索信息。" +
               "当信息充分时生成带引用的答案。\n\n用户问题: " + query;
    }

    private String generateDraft(String query, String context) {
        return chatClient.prompt()
            .system("基于检索到的上下文信息生成准确、带引用的答案。使用 [N] 标注引用来源。")
            .user("问题: " + query + "\n\n上下文:\n" + context)
            .call()
            .getContent();
    }

    private String buildFallbackAnswer(AgentState state, String reason) {
        return String.format(
            "抱歉，%s。\n\n以下是我已找到的相关信息：\n%s",
            reason,
            state.getSynthesizedContext() != null ? state.getSynthesizedContext() : "（暂无相关信息）"
        );
    }

    private void recordAgentSteps(AgentState state) {
        // 从 HarnessAgent 的 RuntimeContext 中提取步骤记录
        // HarnessAgent 通过 workspace/sessions/ 自动持久化会话
    }
}
```

### 3.3 AgentState 模型

```java
/**
 * Agent 状态模型 — 贯穿整个 Agent 执行周期的数据容器。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentState {
    // ── 输入层 ──
    private String originalQuery;
    private String userId;
    private AgentConfig config;

    // ── 规划层 (FR-1.1) ──
    private List<SubQuery> subQueries;              // 分解后的子查询
    private List<ToolAssignment> toolAssignments;   // 子查询→工具分配

    // ── 执行层 (FR-1.2, FR-1.3) ──
    private List<ToolCallRecord> toolCalls;          // 已执行的工具调用
    private String synthesizedContext;               // 综合后的上下文

    // ── 质量检查层 (FR-8) ──
    private ContextVerdict contextVerdict;           // 完备性判定
    private int contextRetryCount;                   // 完备性重试次数

    // ── 生成层 (FR-9, FR-10) ──
    private String draftAnswer;                      // 答案草稿
    private ReflectionReport reflectionReport;       // 自反思报告
    private int repairCount;                         // 修复次数

    // ── 最终输出层 ──
    private String finalAnswer;                      // 最终答案
    private List<Document> sources;                  // 引用来源
    private QualityScores qualityScores;             // 质量评分
    private boolean qualityGateFailed;               // 质量门禁是否未通过

    // ── 轨迹与控制层 (NFR-1) ──
    private String trajectoryId;                     // 轨迹唯一 ID
    private List<StepRecord> trajectory;             // 执行步骤列表
    private int loopCount;                           // 循环轮次
    private AgentStatus status;                      // COMPLETED/FAILED/TIMEOUT
    private String error;                            // 错误信息
    private long totalDurationMs;                    // 总耗时

    // ── 运行控制 ──
    private long startTime = System.currentTimeMillis();

    public boolean isTimeout() {
        return System.currentTimeMillis() - startTime > config.getMaxTimeoutMs();
    }

    public void incrementLoopCount() { this.loopCount++; }
    public void incrementRepairCount() { this.repairCount++; }

    public void mergeContext(String additionalContent) {
        if (this.synthesizedContext == null) {
            this.synthesizedContext = additionalContent;
        } else {
            this.synthesizedContext += "\n\n--- 补充检索 ---\n\n" + additionalContent;
        }
    }
}
```

### 3.4 AgentConfig 配置模型

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentConfig {
    // ── 循环控制 ──
    private int maxLoops = 5;                   // 主循环最大次数
    private int maxContextRetries = 3;           // Context Agent 最大重检次数
    private int maxRepairRetries = 2;            // 修复最大重试次数
    private int totalStepsLimit = 20;            // 总步骤数硬性上限 (NFR-7.1)
    private long maxTimeoutMs = 30000;            // 总耗时上限 (NFR-4.4)
    private long singleToolTimeoutMs = 15000;    // 单工具调用超时 (NFR-4.5)

    // ── 功能开关 ──
    private boolean queryDecompositionEnabled = true;  // 查询分解
    private boolean stepBackEnabled = true;             // Step-Back 查询
    private boolean sufficientContextCheckEnabled = true;
    private boolean selfReflectionEnabled = true;
    private boolean correctiveRepairEnabled = true;
    private boolean llmJudgeEnabled = false;            // P1 默认关闭

    // ── 路由 ──
    private boolean forceAgentic = false;        // 强制走 Agent 模式
    private boolean forceWorkflow = false;       // 强制走 Workflow 模式

    // ── 质量阈值 (FR-11.2) ──
    private QualityThresholds qualityThresholds = new QualityThresholds();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QualityThresholds {
        private double faithfulness = 0.7;
        private double answerRelevancy = 0.6;
        private double citationGrounding = 0.8;
    }
}
```

---

## 4. 工具框架设计

### 4.1 AgentTool 接口

工具层采用**适配器模式**：AgentScope 的 HarnessAgent 通过 `.tool()` 注册和调用工具，我们通过 `AgentTool` 接口将现有 Spring Bean 包装为 AgentScope 兼容的工具。

```java
/**
 * Agent 工具接口 — 适配 AgentScope-Java 的 Toolkit 规范。
 *
 * 每个 AgentTool 实现类包装一个现有检索组件，通过 ToolRegistry 注册
 * 到 HarnessAgent 的工具列表中（通过 .tool() 注册）。
 *
 * 工具定义包含：
 * - name:        工具名称（LLM 选择工具的依据）
 * - description: 工具描述（LLM 理解工具用途的依据）
 * - parameters:  参数 Schema（LLM 生成工具调用参数的依据）
 * - execute():   工具执行逻辑
 * - isAvailable(): 工具可用性检查
 */
public interface AgentTool {

    /**
     * 工具名称，用于 LLM 选择工具。
     * 格式：snake_case，如 "vector_search", "bm25_search"
     */
    String getName();

    /**
     * 工具描述，LLM 据此理解工具用途。
     * 应包含：适用场景、使用限制、返回格式
     */
    String getDescription();

    /**
     * 工具参数 Schema，AgentScope 据此生成 LLM 的 Function Calling 参数。
     * 格式为 JSON Schema（如 Jackson Schema 或 Map 描述）
     */
    Map<String, Object> getParametersSchema();

    /**
     * 执行工具。
     * @param params 参数键值对（由 LLM 根据 parametersSchema 生成）
     * @return 执行结果
     */
    ToolResult execute(Map<String, Object> params);

    /**
     * 工具是否可用。
     * 例如：数据库断开时 SQL 工具返回 false
     */
    default boolean isAvailable() { return true; }
}
```

### 4.2 ToolRegistry 注册表

```java
/**
 * 工具注册表 — Agent 工具的唯一管理入口。
 *
 * 职责：
 * 1. 管理 AgentTool 的生命周期（注册/获取/列表）
 * 2. 生成 AgentScope 兼容的工具描述
 * 3. 提供 LLM 友好的工具提示词
 * 4. 工具可用性过滤
 */
@Component
public class ToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    /**
     * 注册工具（通常在 PostConstruct 中调用）
     */
    public void register(AgentTool tool) {
        tools.put(tool.getName(), tool);
    }

    /**
     * 批量注册（Spring 自动注入后调用）
     */
    public void registerAll(List<AgentTool> toolList) {
        toolList.forEach(this::register);
    }

    /**
     * 根据名称获取工具
     */
    public AgentTool getTool(String name) {
        return tools.get(name);
    }

    /**
     * 获取所有可用工具
     */
    public List<AgentTool> getAllTools() {
        return tools.values().stream()
            .filter(AgentTool::isAvailable)
            .collect(Collectors.toList());
    }

    /**
     * 生成 AgentScope Toolkit 兼容的工具定义列表
     */
    public List<Map<String, Object>> getToolDefinitionsForAgentScope() {
        return getAllTools().stream()
            .map(this::toToolDefinition)
            .collect(Collectors.toList());
    }

    private Map<String, Object> toToolDefinition(AgentTool tool) {
        Map<String, Object> def = new HashMap<>();
        def.put("name", tool.getName());
        def.put("description", tool.getDescription());
        def.put("parameters", tool.getParametersSchema());
        return def;
    }

    /**
     * 格式化工具描述文本（用于 System Prompt）
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
}
```

### 4.3 工具实现规范

#### 4.3.1 VectorSearchTool (P0)

```java
/**
 * 向量检索工具 — 包装 HybirdContentRetriever。
 *
 * 工具定义：
 *   name: "vector_search"
 *   description: "基于向量相似度和 BM25 关键词混合检索知识库文档。
 *                 适用于语义搜索、开放域问答、中文/英文文档检索"
 *   parameters:
 *     query: string (required) — 检索查询文本
 *     top_k: integer (optional, default=5) — 返回文档数量
 *     source: string (optional) — 按来源过滤（文件名）
 *
 * 实现：调用 HybirdContentRetriever.retrieve(query, topK, threshold, source)
 */
@Component
public class VectorSearchTool implements AgentTool {

    private final HybirdContentRetriever retriever;

    @Override
    public String getName() { return "vector_search"; }

    @Override
    public String getDescription() {
        return "基于向量相似度和 BM25 关键词混合检索知识库文档。" +
               "适用于语义搜索、开放域问答、中文/英文文档检索。" +
               "返回文档列表，每篇包含文档内容和来源信息。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
            "query", Map.of("type", "string", "description", "检索查询文本", "required", true),
            "top_k", Map.of("type", "integer", "description", "返回文档数量", "default", 5),
            "source", Map.of("type", "string", "description", "按来源过滤（文件名）")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String query = (String) params.get("query");
        int topK = params.containsKey("top_k") ? ((Number) params.get("top_k")).intValue() : 5;
        String source = (String) params.get("source");

        try {
            List<Document> docs = retriever.retrieve(query, topK, 0.0, source);
            return ToolResult.success(docs);
        } catch (Exception e) {
            return ToolResult.failure("向量检索失败: " + e.getMessage());
        }
    }
}
```

#### 4.3.2 BM25SearchTool (P0)

```java
/**
 * BM25 关键词检索工具 — 包装 Bm25Indexer。
 *
 * 工具定义：
 *   name: "bm25_search"
 *   description: "基于 BM25 算法的关键词精确搜索。
 *                 适用于专业术语、代码片段、配置项、型号等精确匹配场景"
 *   parameters:
 *     query: string (required) — 关键词查询
 *     top_k: integer (optional, default=5) — 返回文档数量
 */
@Component
public class Bm25SearchTool implements AgentTool { ... }
```

#### 4.3.3 ExternalSearchTool (P1)

```java
/**
 * 外部搜索工具 — 包装 ExternalSearchService。
 *
 * 工具定义：
 *   name: "external_search"
 *   description: "搜索互联网获取实时信息。
 *                 适用于时效性强的查询、内部知识库未覆盖的内容"
 *   parameters:
 *     query: string (required) — 搜索查询
 *     top_k: integer (optional, default=3) — 返回结果数量
 */
@Component
public class ExternalSearchTool implements AgentTool { ... }
```

#### 4.3.4 MemoryQueryTool (P1)

```java
/**
 * 记忆查询工具 — 包装 ShortTermMemoryManager + LongTermMemoryManager。
 *
 * 工具定义：
 *   name: "memory_query"
 *   description: "查询用户的会话历史和长期记忆（偏好、事实、上下文）。
 *                 适用于个性化回答和需要了解用户背景"
 *   parameters:
 *     query: string (required) — 要查询的记忆内容
 */
@Component
public class MemoryQueryTool implements AgentTool { ... }
```

#### 4.3.5 SqlQueryTool (P1)

```java
/**
 * Text-to-SQL 工具 — 将自然语言转为 SQL 查询并执行。
 *
 * 工具定义：
 *   name: "sql_query"
 *   description: "通过自然语言查询关系数据库中的结构化数据。
 *                 适用于统计数据、聚合查询、精确数值查找"
 *   parameters:
 *     query: string (required) — 自然语言描述的查询需求
 *     table_hint: string (optional) — 指定优先查询的表名
 *
 * 安全约束：
 * - 只允许 SELECT 查询（自动校验）
 * - 查询超时 5s
 * - 行数限制 1000 行
 */
@Component
public class SqlQueryTool implements AgentTool { ... }
```

### 4.4 ToolResult 封装

```java
@Data
@AllArgsConstructor
public class ToolResult {
    private final boolean success;
    private final Object data;
    private final String errorMessage;
    private final long durationMs;

    public static ToolResult success(Object data) {
        return new ToolResult(true, data, null, 0);
    }

    public static ToolResult failure(String errorMessage) {
        return new ToolResult(false, null, errorMessage, 0);
    }

    public boolean isSuccess() { return success; }

    @SuppressWarnings("unchecked")
    public <T> T getData() { return (T) data; }
}
```

---

## 5. 质量保障管道设计

### 5.1 SufficientContextAgent (FR-8)

```java
/**
 * 上下文完备性检查代理 (FR-8)。
 *
 * 在生成最终答案前，检查检索到的信息是否足以回答问题。
 * Google Research 2026 年提出的模式，研究表明可提升准确率 +34%。
 *
 * 流程：
 * 1. 检索片段检查 — LLM 判断文档是否包含回答所需的关键信息
 * 2. 缺失分析 — 如果不够，具体说明缺什么 + 建议的重检索词
 * 3. 循环控制 — 最多 3 轮，连续 2 轮无改善提前终止
 */
public class SufficientContextAgent {

    private final ChatClient chatClient;

    /**
     * 检查上下文完备性。
     *
     * @param query   原始用户查询
     * @param context 检索到的上下文（文档列表/综合文本）
     * @return 完备性判定结果
     */
    public ContextVerdict check(String query, String context) {
        if (context == null || context.isBlank()) {
            return ContextVerdict.insufficient("未检索到任何文档", query);
        }

        String prompt = String.format(
            "判断以下检索到的信息是否足以回答用户问题。\n\n" +
            "用户问题: %s\n\n" +
            "检索到的信息:\n%s\n\n" +
            "请判断: 这些信息是否充分覆盖了回答问题所需的所有关键点？\n" +
            "- 如果充分，回复: 完备\n" +
            "- 如果不充分，说明缺失了什么，并建议补充搜索词",
            query, context
        );

        String response = chatClient.prompt().user(prompt).call().getContent();
        return parseVerdict(response);
    }

    private ContextVerdict parseVerdict(String response) {
        if (response.contains("完备") || response.contains("充分")) {
            return ContextVerdict.sufficient();
        }
        // 提取缺失信息和搜索建议
        String missingInfo = extractMissingInfo(response);
        String suggestedQuery = extractSuggestedQuery(response);
        return ContextVerdict.insufficient(missingInfo, suggestedQuery);
    }
}

@Data
@AllArgsConstructor
public class ContextVerdict {
    private final boolean sufficient;
    private final String missingInfo;
    private final String suggestedQuery;

    public static ContextVerdict sufficient() {
        return new ContextVerdict(true, null, null);
    }

    public static ContextVerdict insufficient(String missingInfo, String suggestedQuery) {
        return new ContextVerdict(false, missingInfo, suggestedQuery);
    }
}
```

### 5.2 SelfReflection (FR-9)

```java
/**
 * 自反思模块 (FR-9)。
 *
 * 答案生成后，进行三个方面检查：
 * 1. 引用缺失检查 — 逐句检查关键主张是否有对应的 [N] 引用
 * 2. 子查询覆盖检查 — 对比分解出的子查询是否全部被覆盖
 * 3. 矛盾检测 — 检测答案内部矛盾和答案与材料的矛盾
 */
public class SelfReflection {

    private final ChatClient chatClient;

    /**
     * 对答案草稿进行自反思。
     *
     * @param query        原始用户查询
     * @param subQueries   分解出的子查询列表（可能为 null）
     * @param draftAnswer  生成的答案草稿
     * @param context      检索到的上下文
     * @return 反思报告
     */
    public ReflectionReport reflect(String query, List<SubQuery> subQueries,
                                     String draftAnswer, String context) {
        // 1. 引用检查
        List<String> uncitedClaims = checkCitations(draftAnswer);

        // 2. 子查询覆盖检查
        List<String> uncoveredSubQueries = new ArrayList<>();
        if (subQueries != null && !subQueries.isEmpty()) {
            uncoveredSubQueries = checkSubQueryCoverage(subQueries, draftAnswer);
        }

        // 3. 矛盾检测
        List<String> contradictions = checkContradictions(draftAnswer, context);

        return new ReflectionReport(
            !uncitedClaims.isEmpty() || !uncoveredSubQueries.isEmpty() || !contradictions.isEmpty(),
            uncitedClaims,
            uncoveredSubQueries,
            contradictions
        );
    }

    private List<String> checkCitations(String draftAnswer) {
        // 使用 LLM 逐句检查引用
        String prompt = String.format(
            "检查以下答案中的关键主张是否有对应的引用标记 [N]。\n\n%s\n\n" +
            "列出所有没有引用支持的关键主张：", draftAnswer
        );
        String response = chatClient.prompt().user(prompt).call().getContent();
        return parseList(response);
    }

    private List<String> checkSubQueryCoverage(List<SubQuery> subQueries, String draftAnswer) {
        String subQueryText = subQueries.stream()
            .map(sq -> "- " + sq.getQuery())
            .collect(Collectors.joining("\n"));
        String prompt = String.format(
            "原始问题被分解为以下子问题：\n%s\n\n" +
            "生成的答案为：\n%s\n\n" +
            "列出答案中没有覆盖的子问题：", subQueryText, draftAnswer
        );
        String response = chatClient.prompt().user(prompt).call().getContent();
        return parseList(response);
    }

    private List<String> checkContradictions(String draftAnswer, String context) {
        String prompt = String.format(
            "检测以下答案是否存在内部矛盾，或与检索材料矛盾。\n\n" +
            "答案：\n%s\n\n检索材料：\n%s\n\n列出所有矛盾点：",
            draftAnswer, context
        );
        String response = chatClient.prompt().user(prompt).call().getContent();
        return parseList(response);
    }
}

@Data
@AllArgsConstructor
public class ReflectionReport {
    private boolean hasIssues;
    private List<String> uncitedClaims;
    private List<String> uncoveredSubQueries;
    private List<String> contradictions;
}
```

### 5.3 CorrectiveRepair (FR-10)

```java
/**
 * 纠错模块 (FR-10)。
 *
 * 基于 SelfReflection 的发现进行针对性修复：
 * 1. 缺失引用 → 补充检索 + 补充引用
 * 2. 未覆盖子问题 → 对缺失子问题发起专项检索
 * 3. 矛盾 → 重新评估矛盾双方证据强度
 *
 * 修复后再次执行 SelfReflection 验证。
 * 最大重试：2 次
 * 修复仍失败时：优雅降级，声明不确定性。
 */
public class CorrectiveRepair {

    private final ChatClient chatClient;
    private final ToolRegistry toolRegistry;

    /**
     * 执行修复。
     */
    public String repair(String query, String draftAnswer,
                          ReflectionReport report, String context) {
        // 1. 收集缺失的关键词
        Set<String> searchTerms = new LinkedHashSet<>();

        if (report.getUncoveredSubQueries() != null) {
            searchTerms.addAll(report.getUncoveredSubQueries());
        }
        if (report.getUncitedClaims() != null) {
            searchTerms.addAll(extractKeywords(report.getUncitedClaims()));
        }

        // 2. 补充检索
        String supplementalContext = "";
        for (String term : searchTerms) {
            AgentTool vectorTool = toolRegistry.getTool("vector_search");
            if (vectorTool != null && vectorTool.isAvailable()) {
                ToolResult result = vectorTool.execute(Map.of("query", term, "top_k", 3));
                if (result.isSuccess()) {
                    supplementalContext += "\n" + result.getData().toString();
                }
            }
        }

        // 3. 重新生成
        String combinedContext = context + "\n\n=== 补充检索材料 ===\n" + supplementalContext;
        String repairedAnswer = chatClient.prompt()
            .system("基于全部检索信息（含补充材料），重新生成准确、带引用的答案。" +
                    "注意修复之前的问题：" + report.toString())
            .user("问题: " + query + "\n\n全部上下文:\n" + combinedContext)
            .call()
            .getContent();

        return repairedAnswer;
    }
}
```

### 5.4 LLM Judge (FR-11, P1)

```java
/**
 * 运行时质量评估器 (FR-11, P1)。
 *
 * 在答案返回给用户前进行质量评分：
 * - Faithfulness（忠实度）: 答案是否严格基于检索上下文
 * - Answer Relevancy（相关性）: 答案是否直接回答用户问题
 * - Context Precision（上下文精确率）: 检索材料中有多少是回答必需的
 * - Citation Grounding（引用完整性）: 每个关键主张是否有明确引用
 *
 * 低于阈值时：触发重生成或降级
 * 评估结果记录到 rag_evaluations 表，关联 trajectory_id
 */
public class LlmJudge {

    private final ChatClient chatClient;

    public QualityScores evaluate(String query, String answer, String context) {
        String prompt = String.format(
            "作为 LLM Judge，请对以下答案进行质量评分（0~1）。\n\n" +
            "问题: %s\n上下文: %s\n答案: %s\n\n" +
            "评分维度：\n" +
            "1. faithfulness（忠实度）：答案是否严格基于上下文，无编造\n" +
            "2. answer_relevancy（相关性）：答案是否直接回答用户问题\n" +
            "3. citation_grounding（引用完整性）：关键主张是否有 [N] 引用标记\n\n" +
            "请以 JSON 格式返回评分：{\"faithfulness\": 0.9, \"answer_relevancy\": 0.8, \"citation_grounding\": 0.7}",
            query, context, answer
        );

        String response = chatClient.prompt().user(prompt).call().getContent();
        return QualityScores.parse(response);
    }
}

@Data
@AllArgsConstructor
public class QualityScores {
    private double faithfulness;
    private double answerRelevancy;
    private double citationGrounding;

    public boolean isPassing(QualityThresholds thresholds) {
        return faithfulness >= thresholds.getFaithfulness()
            && answerRelevancy >= thresholds.getAnswerRelevancy()
            && citationGrounding >= thresholds.getCitationGrounding();
    }
}
```

---

## 6. 状态与轨迹设计

### 6.1 TrajectoryRecorder

```java
/**
 * Agent 执行轨迹记录器 (NFR-1)。
 *
 * 每次 Agent 执行的完整决策路径被持久化记录到 agent_trajectories 表。
 * 支持：
 * - 完整步骤记录（每个决策/工具调用/检查/反思步骤）
 * - 轨迹回放（给定 trajectoryId 可重现完整执行过程）
 * - 轨迹与评估关联（rag_evaluations 新增 trajectory_id 字段）
 * - 存储成本可控（30 天后可归档）
 */
@Component
public class TrajectoryRecorder {

    private final TrajectoryRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * 记录 Agent 执行轨迹。
     */
    public void record(AgentState state) {
        TrajectoryEntity entity = new TrajectoryEntity();
        entity.setId(state.getTrajectoryId());
        entity.setUserId(state.getUserId());
        entity.setQuery(state.getOriginalQuery());
        entity.setTrajectoryJson(toJson(state.getTrajectory()));
        entity.setTotalSteps(state.getTrajectory().size());
        entity.setTotalLoops(state.getLoopCount());
        entity.setTotalDurationMs(state.getTotalDurationMs());
        entity.setToolsUsed(toJson(getToolsUsed(state)));
        entity.setQualityScores(state.getQualityScores() != null ? toJson(state.getQualityScores()) : null);
        entity.setStatus(state.getStatus().name());
        entity.setErrorMessage(state.getError());
        entity.setCreatedAt(LocalDateTime.now());

        repository.save(entity);
    }

    /**
     * 根据 trajectoryId 查询轨迹。
     */
    public Optional<AgentState> replay(String trajectoryId) {
        return repository.findById(trajectoryId)
            .map(entity -> reconstructState(entity));
    }

    public List<TrajectoryEntity> getByUserId(String userId, Pageable pageable) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    // ...序列化/反序列化辅助方法
}
```

### 6.2 StepRecord

```java
/**
 * Agent 执行单步记录。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepRecord {
    private int stepNumber;             // 步骤序号
    private int loopNumber;             // 所在循环轮次
    private String type;                // 步骤类型:
                                        // DECOMPOSE | TOOL_CALL | SYNTHESIZE |
                                        // CONTEXT_CHECK | GENERATE | REFLECT |
                                        // REPAIR | JUDGE | TERMINATE
    private String description;         // 步骤描述
    private long durationMs;            // 耗时

    // 工具调用相关
    private String toolName;            // 仅 TOOL_CALL 类型
    private Map<String, Object> toolInput;   // 工具输入参数
    private Object toolOutput;          // 工具输出结果

    // LLM 决策相关
    private String llmPrompt;           // LLM 调用时的 Prompt
    private String llmResponse;         // LLM 响应
}
```

### 6.3 存储格式（JSON 示例）

```json
{
  "trajectory_id": "traj_20260614_abc123",
  "user_id": "user123",
  "query": "比较 Spring Boot 和 Quarkus",
  "steps": [
    {
      "step": 1,
      "loop": 1,
      "type": "DECOMPOSE",
      "description": "将问题分解为3个子查询",
      "duration_ms": 1200,
      "llm_prompt": "请将以下问题拆解为原子子查询...",
      "llm_response": "1. Spring Boot 在微服务场景下的特点\n2. Quarkus 在微服务场景下的特点\n3. 基于Java 21+K8s的技术栈匹配度分析"
    },
    {
      "step": 2,
      "loop": 1,
      "type": "TOOL_CALL",
      "description": "调用 vector_search 检索 Spring Boot 微服务",
      "duration_ms": 230,
      "tool_name": "vector_search",
      "tool_input": {"query": "Spring Boot 微服务特点", "top_k": 5},
      "tool_output": {"document_count": 5, "summary": "找到5篇相关文档"}
    }
  ],
  "total_duration_ms": 3450,
  "final_answer": "...",
  "quality_scores": {"faithfulness": 0.92, "answer_relevancy": 0.88},
  "status": "COMPLETED"
}
```

---

## 7. 路由策略设计

### 7.1 QueryRouter

```java
/**
 * 查询路由 — 决定查询走 Workflow RAG 还是 Agentic RAG。
 *
 * 路由策略（ADR-004）：
 * 1. 强制开关优先：forceAgentic / forceWorkflow
 * 2. 规则快速分类：长度 < 20 且无复杂关键词 → SIMPLE
 * 3. LLM 分类兜底：规则无法判断时调用 LLM 分类
 * 4. 路由决策：SIMPLE → Workflow / MODERATE+COMPLEX → Agentic
 */
@Component
public class QueryRouter {

    private final RagFlow advancedRagFlow;
    private final AgenticRagFlow agenticRagFlow;
    private final ChatClient chatClient;

    private static final Set<String> COMPLEX_KEYWORDS = Set.of(
        "比较", "对比", "区别", "差异", "分析", "总结",
        "为什么", "如何", "如果", "优缺点", "推荐",
        "compare", "difference", "analysis", "versus", "vs"
    );

    /**
     * 路由入口。
     */
    public RagAnswer route(AskRequest request) {
        // 强制开关
        if (request.isForceWorkflow()) {
            return advancedRagFlow.executeRag(request.getQuestion(), request.getUserId(), request.getSource());
        }
        if (request.isForceAgentic()) {
            return agenticRagFlow.executeRag(request.getQuestion(), request.getUserId(), request.getSource());
        }

        // 规则分类
        ComplexityLevelEnum complexity = classifyByRule(request.getQuestion());

        // LLM 分类兜底
        if (complexity == ComplexityLevelEnum.AMBIGUOUS) {
            complexity = classifyByLLM(request.getQuestion());
        }

        // 路由决策
        if (complexity == ComplexityLevelEnum.SIMPLE) {
            return advancedRagFlow.executeRag(request.getQuestion(), request.getUserId(), request.getSource());
        }
        return agenticRagFlow.executeRag(request.getQuestion(), request.getUserId(), request.getSource());
    }

    /**
     * 规则快速分类。
     */
    private ComplexityLevelEnum classifyByRule(String question) {
        if (question == null || question.length() < 20) {
            return ComplexityLevelEnum.SIMPLE;
        }
        // 检查是否包含复杂关键词
        boolean hasComplexKeyword = COMPLEX_KEYWORDS.stream()
            .anyMatch(kw -> question.contains(kw));
        if (!hasComplexKeyword) {
            return ComplexityLevelEnum.SIMPLE;
        }
        return ComplexityLevelEnum.AMBIGUOUS;  // 需要 LLM 进一步判断
    }

    /**
     * LLM 分类。
     */
    private ComplexityLevelEnum classifyByLLM(String question) {
        String response = chatClient.prompt()
            .system("请判断以下问题的复杂度级别：\n" +
                    "SIMPLE - 简单事实性问题，单步检索即可回答\n" +
                    "MODERATE - 需要多源信息综合\n" +
                    "COMPLEX - 需要多步推理、比较分析或专业领域知识\n" +
                    "只返回一个词：SIMPLE、MODERATE 或 COMPLEX")
            .user(question)
            .call()
            .getContent();

        if (response.contains("COMPLEX")) return ComplexityLevelEnum.COMPLEX;
        if (response.contains("MODERATE")) return ComplexityLevelEnum.MODERATE;
        return ComplexityLevelEnum.SIMPLE;
    }
}
```

### 7.2 路由策略配置

```yaml
agentic-rag:
  routing:
    strategy: rule_llm           # rule_only | llm_only | rule_llm
    force-agentic: false          # 强制所有查询走 Agent 模式
    force-workflow: false         # 强制所有查询走 Workflow 模式
    simple-threshold: 20          # 小于此长度的查询直接判定为 SIMPLE
```

---

## 8. 接口设计

### 8.1 新增 API

#### POST /api/qa/ask/agent

Agentic RAG 问答端点（新增），接受用户查询并返回 Agent 处理结果。

**请求**:
```json
{
  "userId": "user123",
  "question": "比较 Spring Boot 和 Quarkus 在微服务场景下的优缺点",
  "config": {
    "maxLoops": 5,
    "timeoutMs": 30000,
    "enableExternalSearch": false,
    "forceAgentic": false
  }
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | string | 是 | 用户标识 |
| question | string | 是 | 用户查询文本 |
| config.maxLoops | int | 否 | 最大循环次数（默认 5） |
| config.timeoutMs | int | 否 | 超时时间（默认 30000） |
| config.enableExternalSearch | bool | 否 | 是否启用外部搜索（默认 false） |
| config.forceAgentic | bool | 否 | 是否强制走 Agent 模式（默认 false） |

**响应**:
```json
{
  "answer": "Spring Boot 和 Quarkus 都是优秀的微服务框架...\n\n**Spring Boot 优点**: ... [1]\n**Quarkus 优点**: ... [2]\n\n**推荐结论**: ...",
  "sources": [
    {
      "content": "Spring Boot 在微服务领域...",
      "metadata": { "source": "spring-microservices.md", "tool": "vector_search" }
    },
    {
      "content": "Quarkus 是针对云原生...",
      "metadata": { "source": "quarkus-guide.pdf", "tool": "vector_search" }
    }
  ],
  "trajectoryId": "traj_20260614_abc123",
  "loopCount": 2,
  "qualityScores": {
    "faithfulness": 0.92,
    "answerRelevancy": 0.88,
    "citationGrounding": 0.95
  },
  "totalDurationMs": 3450,
  "agenticMode": true
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| answer | string | 最终答案（Markdown 格式，含引用 [N]） |
| sources | array | 引用来源列表 |
| trajectoryId | string | 执行轨迹 ID（可用于轨迹回放） |
| loopCount | int | Agent 循环轮数 |
| qualityScores | object | 质量评分（LLM Judge 启用时返回） |
| totalDurationMs | long | 总耗时 |
| agenticMode | bool | 是否为 Agent 模式 |

#### GET /api/qa/trajectory/{trajectoryId}

查询 Agent 执行轨迹详情。

**响应**:
```json
{
  "trajectoryId": "traj_20260614_abc123",
  "userId": "user123",
  "query": "比较 Spring Boot 和 Quarkus",
  "steps": [
    {
      "step": 1,
      "type": "DECOMPOSE",
      "description": "分解为3个子查询",
      "durationMs": 1200
    },
    {
      "step": 2,
      "type": "TOOL_CALL",
      "toolName": "vector_search",
      "input": {"query": "Spring Boot 微服务特点", "top_k": 5},
      "output": {"document_count": 5},
      "durationMs": 230
    }
  ],
  "totalDurationMs": 3450,
  "status": "COMPLETED"
}
```

### 8.2 内部接口

#### AgentOrchestrator

| 方法 | 说明 |
|------|------|
| `AgentState execute(String query, String userId)` | 执行完整 Agent 主循环 |
| `AgentState execute(String query, String userId, AgentConfig config)` | 带自定义配置的执行 |

#### AgentTool 实现类

| 方法 | 说明 |
|------|------|
| `ToolResult execute(Map<String, Object> params)` | 执行工具调用 |

#### Quality Pipeline

| 组件 | 方法 | 说明 |
|------|------|------|
| `SufficientContextAgent` | `ContextVerdict check(String query, String context)` | 完备性检查 |
| `SelfReflection` | `ReflectionReport reflect(...)` | 自反思 |
| `CorrectiveRepair` | `String repair(...)` | 纠错修复 |
| `LlmJudge` | `QualityScores evaluate(String query, String answer, String context)` | 质量评分 |

---

## 9. 数据模型设计

### 9.1 新增表：agent_trajectories

```sql
CREATE TABLE agent_trajectories (
    id VARCHAR(36) PRIMARY KEY COMMENT '轨迹唯一标识（格式: traj_yyyyMMdd_xxx）',
    user_id VARCHAR(100) NOT NULL COMMENT '用户ID',
    query TEXT NOT NULL COMMENT '用户原始查询',
    trajectory JSON NOT NULL COMMENT '完整执行轨迹（步骤列表 JSON）',
    total_steps INT DEFAULT 0 COMMENT '总步数',
    total_loops INT DEFAULT 0 COMMENT '总循环轮次',
    total_duration_ms BIGINT DEFAULT 0 COMMENT '总耗时(ms)',
    tools_used JSON COMMENT '使用的工具列表',
    quality_scores JSON COMMENT '质量评分快照',
    status VARCHAR(20) DEFAULT 'COMPLETED' COMMENT 'COMPLETED/FAILED/TIMEOUT',
    error_message TEXT COMMENT '错误信息',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    archived TINYINT(1) DEFAULT 0 COMMENT '是否已归档',
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent执行轨迹表';
```

### 9.2 现有表扩展

```sql
-- rag_evaluations 表新增 trajectory_id 字段
ALTER TABLE rag_evaluations
    ADD COLUMN trajectory_id VARCHAR(36) NULL COMMENT '关联的Agent轨迹ID',
    ADD INDEX idx_trajectory_id (trajectory_id);
```

### 9.3 数据兼容性说明

| 变更 | 兼容性 |
|------|--------|
| `agent_trajectories` 新增表 | 完全向后兼容 |
| `rag_evaluations.trajectory_id` 可为 NULL | 现有 Workflow RAG 记录不受影响 |
| 现有评估查询 API | 全部保持向后兼容 |

---

## 10. 配置设计

### 10.1 Agentic RAG 配置段

```yaml
# ============================================================
# Agentic RAG 配置
# ============================================================
agentic-rag:
  enabled: true                          # 全局开关

  # ── Agent 引擎配置 ──
  agent:
    name: "KnowledgeAssistant"           # Agent 名称
    model: "qwen-max"                    # LLM 模型
    max-loops: 5                         # 主循环最大次数
    max-context-retries: 3               # Context Agent 最大重检
    max-repair-retries: 2                # 修复最大重试
    total-steps-limit: 20                # 总步骤数硬性上限
    max-timeout-ms: 30000                # 总超时
    single-tool-timeout-ms: 15000        # 单工具超时
    enable-streaming: true               # 是否启用流式输出

  # ── 工具配置 ──
  tool:
    vector-search:
      top-k: 5
      similarity-threshold: 0.0
    bm25-search:
      top-k: 5
    external-search:
      top-k: 3
      enabled: false                     # P1 默认关闭
    sql-query:
      max-rows: 1000
      query-timeout-ms: 5000
      enabled: false                     # P1 默认关闭
    memory-query:
      top-k: 5
      enabled: false                     # P1 默认关闭

  # ── 质量保障配置 ──
  quality:
    context-check:
      enabled: true
      max-retries: 3
      improvement-threshold: 2           # 连续 N 轮无改善则提前终止
    self-reflection:
      enabled: true
    corrective-repair:
      enabled: true
      max-retries: 2
    llm-judge:
      enabled: false                     # P1 默认关闭
      thresholds:
        faithfulness: 0.7
        answer-relevancy: 0.6
        citation-grounding: 0.8

  # ── 路由配置 ──
  routing:
    strategy: rule_llm                   # rule_only | llm_only | rule_llm
    force-agentic: false
    force-workflow: false
    simple-threshold: 20                 # 规则分类：小于此长度判定为 SIMPLE

  # ── 工作区配置 ──
  workspace:
    path: "./workspace/knowledge-agent"    # 工作区根目录
    auto-init: true                         # 启动时自动创建目录结构
    agents-md: |
      你是一个智能知识库助手，负责通过检索知识库来回答用户问题。
      使用 [N] 标记引用来源。信息不足时请补充检索。
      使用中文回答，不确定时明确告知用户。

  # ── 轨迹配置 ──
  trajectory:
    enabled: true
    retention-days: 30                   # 轨迹保留天数
```

### 10.2 依赖配置

```xml
<!-- core/pom.xml 新增依赖 -->
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-harness</artifactId>
    <version>2.0.0-RC1</version>
</dependency>
```

---

## 11. AgentScope-Java 集成要点

### 11.1 HarnessAgent 整体架构

HarnessAgent 不是替代 ReActAgent，而是在 ReAct 推理循环外包装了完整的企业级工程能力：

```
┌────────────────────────────────────────────────────────────────────┐
│                     HarnessAgent                                   │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────┐      │
│  │  工作区 (workspace/) — 一切配置的单一事实来源             │      │
│  │  ├─ AGENTS.md    → System Prompt（Agent 人格）           │      │
│  │  ├─ MEMORY.md    → 精炼长期记忆（后台自动维护）            │      │
│  │  ├─ skills/      → 可复用技能（自动注册为工具）            │      │
│  │  ├─ subagents/   → 子 Agent 规格声明                    │      │
│  │  ├─ tools.json   → MCP 服务器和工具白名单                 │      │
│  │  └─ agents/<id>/ → 会话状态、历史、每日记忆               │      │
│  └─────────────────────────────────────────────────────────┘      │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────┐      │
│  │  Hook 管道 — 生命周期拦截器（按优先级执行）                │      │
│  │  ├─ CompactionHook (priority 10)                       │      │
│  │  │  → 历史超阈值时自动压缩                                │      │
│  │  ├─ SubagentsHook (priority 80)                        │      │
│  │  │  → 从 subagents/ 发现并注册子 Agent 工具               │      │
│  │  ├─ WorkspaceContextHook (priority 900)                 │      │
│  │  │  → 自动注入 AGENTS.md + MEMORY.md 到 System Prompt    │      │
│  │  ├─ SessionPersistenceHook (priority 900)               │      │
│  │  │  → 会话快照自动持久化到 agents/<id>/sessions/          │      │
│  │  └─ 自定义 Hook (priority 自由)                          │      │
│  │     → QualityCheckHook / MetricsCollectHook 等           │      │
│  └─────────────────────────────────────────────────────────┘      │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────┐      │
│  │  RuntimeContext — 每次 call() 的身份上下文                │      │
│  │  ├─ userId    → 用户标识（记忆隔离）                      │      │
│  │  ├─ sessionId → 会话标识（工作区目录隔离）                 │      │
│  │  ├─ traceId   → 追踪标识（日志关联）                      │      │
│  │  └─ metadata  → 自定义元数据                              │      │
│  └─────────────────────────────────────────────────────────┘      │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────┐      │
│  │  文件系统抽象层 — AbstractFilesystem                     │      │
│  │  ├─ LocalFilesystem    (本地磁盘)                        │      │
│  │  ├─ SandboxFilesystem  (沙箱隔离)                        │      │
│  │  └─ RemoteFilesystem   (远程存储)                        │      │
│  └─────────────────────────────────────────────────────────┘      │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────┐      │
│  │  底层: ReAct 推理循环 (同 ReActAgent)                    │      │
│  │  分析 → 规划 → 工具调用 → 观察 → 循环 → 回答             │      │
│  └─────────────────────────────────────────────────────────┘      │
└────────────────────────────────────────────────────────────────────┘
```

### 11.2 工作区目录结构

项目初始化时需创建以下工作区目录结构：

```
workspace/knowledge-agent/
├── AGENTS.md                    ← Agent 人格定义（必需）
├── MEMORY.md                    ← 精炼长期记忆（自动维护，初始为空）
├── knowledge/                   ← 领域知识文件（可选）
│   └── system-principles.md     ← 系统原则说明
├── skills/                      ← 可复用技能（可选，自动注册为工具）
├── subagents/                   ← 子 Agent 规格（可选）
├── tools.json                   ← MCP 服务器配置（可选）
└── agents/                      ← 每个 RuntimeContext 实例的会话目录（自动创建）
    └── <sessionId>/
        ├── context/             ← 会话状态快照（进程重启后恢复）
        ├── sessions/            ← 对话历史 JSONL 文件
        └── memory/              ← 每日记忆流水账（YYYY-MM-DD.md）
```

#### AGENTS.md 示例

```markdown
# Knowledge Assistant — Agent 人格定义

你是智能知识库助手，负责通过检索知识库来回答用户问题。

## 行为准则
1. 分析用户问题，拆解为子问题（如需）
2. 使用 vector_search 或 bm25_search 检索知识库
3. 基于检索结果生成准确答案
4. 使用 [N] 标记引用来源
5. 信息不足时，说明缺失内容并继续检索

## 可用工具
- vector_search: 向量+BM25混合检索
- bm25_search: 关键词精确检索
- external_search: 互联网搜索

## 限制
- 不要编造信息
- 不确定时请明确告知
- 使用中文回答
```

### 11.3 HarnessAgent 调用生命周期

```
call(query, ctx)
  │
  ├─ [Hook] onBeforeCall (按优先级升序)
  │   ├─ CompactionHook (10)          ← 历史 > 阈值时压缩
  │   ├─ SubagentsHook (80)           ← 加载子 Agent 工具
  │   ├─ WorkspaceContextHook (900)   ← 注入 AGENTS.md + MEMORY.md
  │   ├─ SessionPersistenceHook (900) ← 加载历史 session
  │   └─ [自定义] QualityCheckHook    ← 质量门禁前置检查
  │
  ├─ ReAct 推理循环（内部）
  │   ├─ Step 1: LLM 分析 + 规划
  │   ├─ Step 2: 调用工具 → 观察结果
  │   ├─ Step 3: LLM 再次分析
  │   ├─ Step 4: 工具调用/回答决策
  │   └─ Step N: 生成最终回答
  │
  ├─ [Hook] onAfterCall (按优先级降序)
  │   ├─ [自定义] MetricsCollectHook ← 记录耗时和 Token
  │   ├─ SessionPersistenceHook (900) ← 持久化 session
  │   └─ CompactionHook (10)         ← 后压缩
  │
  └─ 返回 Msg 响应
```

### 11.4 集成关注点

| 关注点 | 方案 | 说明 |
|--------|------|------|
| **DashScope 模型适配** | AgentScope 原生 DashScopeChatModel | 直接使用通义千问，与 Spring AI Alibaba 共享 API Key |
| **工具注册** | `.tool(new AgentToolImpl())` 在 builder 中注册 | 也可通过 workspace/skills/ 自动发现 |
| **Spring 集成** | `@Bean` 方式创建 HarnessAgent | 注入 Spring 管理的依赖（如数据源） |
| **RuntimeContext** | 每次 `call()` 传入 | 天然 userId/sessionId/traceId 多租户隔离 |
| **System Prompt** | AGENTS.md 自动注入 | 无需手动拼接系统提示词 |
| **记忆管理** | MEMORY.md 自动维护 | 每日流水账 + 后台精炼合并，无需手动实现 |
| **会话持久化** | workspace/agents/<sessionId>/sessions/ | 自动 JSONL 记录，进程重启后可恢复 |
| **流式输出** | AgentScope 支持 Flux 流式 Token | 可对接 SSE |
| **安全沙箱** | AgentScope 内置 gVisor/Docker 沙箱 | 工具执行隔离级别可选 |
| **可观测性** | AgentScope OpenTelemetry + 自定义 Hook | 与现有 Zipkin/Prometheus 集成 |
| **文件系统** | AbstractFilesystem 抽象层 | 本地/沙箱/远程无缝切换 |

### 11.5 Spring 配置类

```java
@Configuration
public class AgenticRagConfig {

    /**
     * 构建 HarnessAgent 实例。
     * - 通过 .workspace() 指定工作区路径
     * - 通过 .tool() 注册 Agent 工具
     * - 通过 .hook() 注入自定义生命周期 Hook
     * - AGENTS.md 和 MEMORY.md 由 WorkspaceContextHook 自动加载
     */
    @Bean
    public HarnessAgent harnessAgent(ToolRegistry toolRegistry) {
        return HarnessAgent.builder()
            .name("KnowledgeAssistant")
            .model(DashScopeChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("qwen-max")
                .build())
            .workspace("./workspace/knowledge-agent")
            // 通过 ToolRegistry 注册所有 AgentTool
            .tools(toolRegistry.getAllTools().toArray(new AgentTool[0]))
            // 自定义 Hook
            .hook(new QualityCheckHook())
            .hook(new MetricsCollectHook())
            .build();
    }

    /**
     * 工具注册表。
     * 自动收集所有 AgentTool Spring Bean。
     */
    @Bean
    public ToolRegistry toolRegistry(List<AgentTool> toolList) {
        ToolRegistry registry = new ToolRegistry();
        registry.registerAll(toolList);
        return registry;
    }
}
```

### 11.6 多租户隔离示例

```java
// 每次用户请求创建一个 RuntimeContext，实现多租户隔离
RuntimeContext ctx = RuntimeContext.builder()
    .userId(userId)                    // 用户标识 — 记忆隔离
    .sessionId(sessionId)              // 会话标识 — 工作区目录隔离
    .traceId(TraceContext.traceId())   // 追踪标识 — 日志关联
    .build();

// HarnessAgent 根据 sessionId 自动:
// 1. 从 workspace/agents/<sessionId>/ 加载历史会话
// 2. 加载该用户的 MEMORY.md
// 3. 写入该用户的 sessions/ 和 memory/
// 4. 不同用户的工作区完全隔离
Msg response = harnessAgent.call(msg, ctx).block();
```

### 11.7 沙箱与安全隔离

HarnessAgent 内置沙箱机制，保护宿主环境免受不可信工具执行的侵害。本项目中**沙箱为可选增强特性**（默认关闭，按需启用）。

| 沙箱模式 | 说明 | 适用场景 | 性能影响 |
|---------|------|---------|---------|
| **NONE**（默认） | 工具直接在 JVM 内执行 | 内部可信工具（vector_search, bm25_search） | 无 |
| **gVisor** | 轻量级容器沙箱 | 外部搜索、SQL 查询等不可信工具 | 中等（~50ms 启动） |
| **Docker** | 全隔离容器 | 第三方代码执行、高风险操作 | 高（~200ms 启动） |

**配置方式**:
```java
HarnessAgent agent = HarnessAgent.builder()
    .name("KnowledgeAssistant")
    .model(model)
    .workspace("./workspace/knowledge-agent")
    .sandbox(SandboxConfig.builder()
        .mode(SandboxMode.gVisor)              // 启用 gVisor 沙箱
        .memoryLimit("512m")                    // 内存限制
        .networkAccess(false)                   // 禁止网络访问
        .build())
    .build();
```

**安全权限配置**:
```yaml
agentic-rag:
  sandbox:
    enabled: false                    # 默认关闭，P1 按需启用
    mode: gVisor                      # NONE | gVisor | Docker
    default-tools: NONE               # vector_search, bm25_search 不隔离
    sandbox-tools:                    # 需要隔离的工具列表
      - external_search
      - sql_query
    memory-limit: 512m
    cpu-limit: 1.0
    network-access: false
```

### 11.8 子 Agent 编排

HarnessAgent 支持通过 `workspace/subagents/` 声明子 Agent，实现复杂任务的动态委派。

**子 Agent 声明文件** (`workspace/knowledge-agent/subagents/data-analyst.json`):
```json
{
  "name": "data_analyst",
  "description": "专门处理数据分析类问题，执行 SQL 查询和统计",
  "model": "qwen-max",
  "tools": ["sql_query"],
  "workspace": "./workspace/subagents/data-analyst",
  "maxSteps": 5
}
```

**自动注册流程**: SubagentsHook (priority 80) 启动时扫描 `workspace/subagents/`，解析每个 `.json` 声明文件并注册为 SubAgentTool。

**本项目子 Agent 场景 (P1+)**:

| 子 Agent | 职责 | 工具 |
|---------|------|------|
| `data_analyst` | 数据查询与统计分析 | sql_query |
| `web_researcher` | 互联网信息搜集 | external_search |
| `memory_keeper` | 用户记忆管理 | memory_query |

### 11.9 Hook 管道详解

HarnessAgent 的 **8+ 个内置 Hook** 按优先级在 Agent 生命周期中执行：

```
优先级     Hook 名称              职责
───       ──────────            ──────────────────
 10       CompactionHook        对话超阈值时自动压缩
 20       TokenCounterHook      Token 计数与限制
 30       RateLimitHook         调用频率限制
 50       SafetyHook            内容安全过滤
 80       SubagentsHook         子 Agent 工具注册
100       MetricsHook           指标采集
200+      [自定义 Hook]          质量检查/监控/日志等
900       WorkspaceContextHook  注入 AGENTS.md + MEMORY.md
900       SessionPersistenceHook 会话持久化快照
```

**内置 Hook 配置**:
```yaml
agentic-rag:
  hook:
    compaction:
      enabled: true
      max-history-length: 50
      max-tokens: 4096
    token-counter:
      enabled: true
      max-prompt-tokens: 8192
      max-response-tokens: 2048
    rate-limit:
      enabled: false
      max-calls-per-minute: 30
    safety:
      enabled: false
```

**自定义 Hook 开发示例** (已在 §3.2 中预留):
```java
public class QualityCheckHook implements AgentHook {
    @Override public void onBeforeCall(RuntimeContext ctx, Msg msg) { }
    @Override public void onAfterCall(RuntimeContext ctx, Msg response) { }
    @Override public int getPriority() { return 200; }
}
```

### 11.10 双层长期记忆管理

HarnessAgent 的**双层记忆**机制自动维护，无需手动编码：

```
第一层: 每日事实日志 (workspace/agents/<id>/memory/YYYY-MM-DD.md)
  └─ 每次 call() 后自动追加 → 后台 Consolidator 定期精炼
     └─ 更新 MEMORY.md（精炼长期记忆）

第二层: 精炼长期记忆 (workspace/agents/<id>/MEMORY.md)
  └─ 持续沉淀的高价值事实 → WorkspaceContextHook 自动注入
```

**配置**:
```yaml
agentic-rag:
  workspace:
    memory:
      enabled: true
      consolidation-interval: 3600    # 精炼间隔（秒）
      max-daily-logs: 30              # 保留最近 30 天
      importance-threshold: 0.6       # 重要性阈值
```

**与现有 LongTermMemoryManager 的关系**:

| 维度 | 现有 LongTermMemoryManager | HarnessAgent 双层记忆 |
|------|--------------------------|----------------------|
| 存储 | MySQL（JPA 持久化） | 文件系统（workspace/） |
| 提取 | MemoryExtractor 每 10 条触发 LLM 提取 | 自动追加 + 后台 Consolidator |
| 检索 | Embedding 向量相似度 | 文件全文 + 自动注入 |
| 关系 | **并行共存**: HarnessAgent 用于会话内上下文；LongTermMemoryManager 用于跨会话持久查询 | |

### 11.11 对话自动压缩

CompactionHook 自动检测历史长度并在超过阈值时执行语义压缩：

```
触发条件: 历史 > 50 条 或 Prompt Token > 8192
压缩策略: LLM 提取关键信息 → 生成压缩摘要 → 保留最近 N 条原始消息
恢复机制: 原始对话在 sessions/ JSONL 中完整保留
```

**配置**:
```yaml
agentic-rag:
  hook:
    compaction:
      enabled: true
      max-history-length: 50
      max-tokens: 4096
      strategy: semantic                   # truncate | semantic
      preserve-last-n: 5                   # 保留最近 5 条原始消息
```

### 11.12 分布式适配与文件系统抽象

AbstractFilesystem 提供可插拔文件系统后端，支持三种模式：

```
AbstractFilesystem (read/write/ls/grep/mv/cp)
  ├── LocalFilesystem     ← 开发/测试（默认）
  ├── SandboxFilesystem   ← 沙箱隔离环境
  └── RemoteFilesystem    ← 分布式/P2+（OSS/S3/MinIO）
```

**配置**:
```yaml
agentic-rag:
  workspace:
    filesystem:
      backend: local                     # local | sandbox | remote
      local:
        base-path: "./workspace"
      # remote:                         # P2+ 分布式
      #   provider: s3
      #   bucket: knowledge-agent-workspace
```

**注意**: Phase 1 默认使用 LocalFilesystem，无额外配置。分布式适配为 **P2+ 特性**。<｜end▁of▁thinking｜>

---

## 文档结束
