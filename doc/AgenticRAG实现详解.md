# Agentic RAG 实现详解

**文档版本**: v2.0  
**编写日期**: 2026-06-28  
**项目**: RAG 智能知识库问答系统  
**框架**: AgentScope-Java 2.0.0-RC3 (HarnessAgent) + Spring AI Alibaba 1.1.2.3  
**Git**: `agentic-rag` branch, 16 commits

---

## 目录

1. [架构演进](#1-架构演进)
2. [HarnessAgent 引擎](#2-harnessagent-引擎)
3. [Agent 主循环](#3-agent-主循环)
4. [工具框架](#4-工具框架)
5. [质量保障管道](#5-质量保障管道)
6. [查询转换与路由](#6-查询转换与路由)
7. [状态管理与轨迹审计](#7-状态管理与轨迹审计)
8. [多级缓存体系](#8-多级缓存体系)
9. [沙箱安全隔离](#9-沙箱安全隔离)
10. [可观测性](#10-可观测性)
11. [双模型路由](#11-双模型路由)
12. [记忆系统](#12-记忆系统)
13. [SSE 流式输出](#13-sse-流式输出)

---

## 1. 架构演进

### 1.1 从固定管道到自主决策

传统 Workflow RAG 采用固定管道模式，所有查询经过 11 个阶段：清洗→增强→多查询→并行检索→CRAG→去重→过滤→重排序→压缩→生成。

核心矛盾：**简单问题和复杂问题走同一流程**。

Agentic RAG 的改进是让系统具备自主决策能力：

```
Workflow RAG:  管道决定流程，数据流过每个阶段
Agentic RAG:   Agent 决定下一步做什么，动态规划执行路径
```

### 1.2 并行共存策略

采用**并行共存 → 渐进替代**的方式引入 Agentic RAG，而非替换：

```
路由层 (QueryRouter):
  SIMPLE     → Workflow RAG（快速路径）
  COMPLEX    → Agentic RAG（自主推理路径）
```

### 1.3 代码组织

所有 Agentic RAG 代码集中在 `core/.../rag/agentic/` 包下，不修改现有 Pipeline 阶段。

```
agentic/
├── AgenticRagFlow.java         ← AbstractRagFlow 子类
├── agent/                      ← 引擎核心
│   ├── AgentOrchestrator.java  ← HarnessAgent 包装 + 质量管道编排
│   ├── AgentState.java         ← 业务状态模型
│   ├── AgentConfig.java        ← 全量配置绑定 (27个嵌套类)
│   ├── AgentStatus.java        ← 执行状态枚举
│   └── StreamEvent.java        ← SSE 流事件
├── tool/                       ← 工具框架
│   ├── AgentTool.java          ← 工具接口
│   ├── ToolRegistry.java       ← 工具注册表
│   ├── ToolResult.java         ← 结果封装
│   └── VectorSearch, BM25Search, ExternalSearch, SqlQuery, MemoryQuery
├── quality/                    ← 质量保障管道
│   ├── SufficientContextAgent  ← 上下文完备性检查
│   ├── SelfReflection          ← 自反思 (引用/覆盖/矛盾)
│   ├── CorrectiveRepair        ← 纠错
│   └── LlmJudge                ← 质量评分
├── trajectory/                 ← 轨迹
│   ├── TrajectoryRecorder      ← 轨迹记录器
│   ├── TrajectoryEntity        ← JPA 实体
│   └── StepRecord              ← 单步记录
├── transform/                  ← 查询转换 (待接入)
│   └── QueryDecomposer, StepBackQuery, TransformationGate
├── router/QueryRouter.java     ← 规则+LLM 两级路由
├── sandbox/SandboxConfigurator ← 沙箱配置
└── config/AgenticRagConfig.java
```

---

## 2. HarnessAgent 引擎

### 2.1 定位

> HarnessAgent 不替换 ReActAgent 的推理循环，而是在外面包装了工程化能力的"壳"。

```
HarnessAgent
  ├─ 工作区管理 (workspace/)
  ├─ Middleware 管道 (Compaction, WorkspaceContext, SessionPersistence...)
  ├─ RuntimeContext (多租户: userId + sessionId)
  ├─ 文件系统抽象 (local/sandbox/remote)
  └─ ReActAgent 核心推理引擎（内部）
```

### 2.2 工作区结构

```
workspace/knowledge-agent/
├── AGENTS.md              ← Agent 人格 (每次 call() 自动注入 System Prompt)
├── MEMORY.md              ← 精炼长期记忆 (后台 Consolidator 自动维护)
├── subagents/             ← 子 Agent 声明文件
│   ├── data-analyst.json
│   └── web-researcher.json
└── agents/<sessionId>/
    ├── sessions/          ← 对话历史 JSONL
    └── memory/            ← 每日记忆流水账
```

### 2.3 RuntimeContext 多租户

```java
RuntimeContext ctx = RuntimeContext.builder()
    .userId(userId)           // 用户标识 — 记忆隔离
    .sessionId(trajectoryId)  // 会话标识 — 目录隔离
    .put("query", query)      // 自定义元数据
    .build();

Msg response = harnessAgent.call(msg, ctx).block();
```

### 2.4 构建 HarnessAgent

```java
HarnessAgent.builder()
    .name("KnowledgeAssistant")
    .model(DashScopeChatModel.builder()
        .apiKey(dashScopeApiKey)
        .modelName("qwen-max").build())
    .workspace(Paths.get("./workspace/knowledge-agent"))
    .maxIters(5)
    .middleware(new ToolCallCaptureMiddleware())
    .build();
```

> **注意:** `Hook` API (`io.agentscope.core.hook.Hook`) 已标记 deprecated，
> 推荐使用 `MiddlewareBase`。Phase 1 原有两个只做日志的 Hook（`QualityCheckHook` / `MetricsCollectHook`），
> 在 Phase 2 已替换为 `ToolCallCaptureMiddleware`，负责记录 Agent 执行中的工具调用。
> 配置类 `AgentConfig.Hook` 及 4 个子类（`Compaction` / `TokenCounter` / `RateLimit` / `Safety`）已一并移除。

---

## 3. Agent 主循环

### 3.1 执行流程

```
AgentOrchestrator.execute(query, userId)
  │
  ├─ 0. 初始化 AgentState + MDC 上下文
  │      ├─ 生成 trajectoryId
  │      └─ 创建 toolCalls / trajectory / subQueries 容器
  │
  ├─ 1. 查询分解 (TransformationGate + QueryDecomposer)
  │      复合问题 → 原子子查询 → StepRecord.QUERY_DECOMPOSE
  │
  ├─ 2. HarnessAgent ReAct 循环
  │      call(msg, ctx).block()
  │      ├─ ToolCallCaptureMiddleware 拦截工具调用
  │      ├─ 会话自动持久化
  │      └─ StepRecord.AGENT_REASONING
  │
  ├─ 3. Step-Back 查询（检索结果 < 50 字符时触发）
  │      StepBackQuery -> 放宽查询 -> 重新调用
  │      └─ StepRecord.STEP_BACK_QUERY
  │
  ├─ 4. SufficientContextAgent 完备性检查
  │      LLM 判断 -> 不足 -> 补充检索 (最多 3 轮)
  │      └─ StepRecord.CONTEXT_CHECK
  │
  ├─ 5. 生成答案草稿 (ChatClient)
  │      └─ StepRecord.GENERATE_DRAFT
  │
  ├─ 6. SelfReflection + CorrectiveRepair
  │      引用/覆盖/矛盾检测 -> 修复 (最多 2 轮)
  │      └─ StepRecord.SELF_REFLECTION
  │
  ├─ 7. LLM Judge 质量评估 (可选)
  │      三维评分 -> 不通过则重生成
  │      └─ StepRecord.QUALITY_JUDGE
  │
  └─ 8. 记录轨迹到 MySQL
         └─ 含 steps / toolCalls / qualityScores
```

### 3.2 循环控制

| 控制层 | 默认值 | 最大值 | 超限行为 |
|--------|:------:|:------:|---------|
| HarnessAgent maxIters | 5 | 10 | 强制终止 |
| 总超时 | 30s | 60s | 返回降级答案 |
| 单工具超时 | 15s | 30s | 跳过该工具 |
| Context 重检 | 3 次 | 5 次 | 用现有材料生成 |
| Repaire 重试 | 2 次 | 3 次 | 声明不确定性 |

### 3.3 降级策略

```java
// 三级降级
TIMEOUT         → "处理超时，以下是我已找到的信息..."
MAX_LOOPS       → 返回当前最佳 draftAnswer
CRITICAL_ERROR  → 退回到 AdvancedRagFlow (Workflow 模式)
```

---

## 4. 工具框架

### 4.1 AgentTool 接口

```java
public interface AgentTool {
    String getName();                          // "vector_search" (snake_case)
    String getDescription();                   // LLM 理解工具用途
    Map<String, Object> getParametersSchema();  // JSON Schema 格式
    ToolResult execute(Map<String, Object>);   // 执行逻辑
    default boolean isAvailable();             // 可用性检查
}
```

### 4.2 工具清单

| 工具 | 名称 | 包装对象 | 默认启用 | 说明 |
|------|------|---------|:--------:|------|
| VectorSearch | `vector_search` | `HybirdContentRetriever` | ✅ | 向量+BM25 混合检索 |
| BM25Search | `bm25_search` | `Bm25Indexer` | ✅ | 关键词精确搜索 |
| ExternalSearch | `external_search` | `ExternalSearchService` | ❌ | 互联网搜索 |
| SqlQuery | `sql_query` | `JdbcTemplate` | ❌ | Text-to-SQL |
| MemoryQuery | `memory_query` | `ShortTermMemory + LongTermMemoryManager` | ✅ | 记忆查询 |

### 4.3 SQL 安全校验

使用正则 `\b` 单词边界替代空格分割：

```java
private static final Pattern FORBIDDEN_PATTERN = Pattern.compile(
    "\\b(DROP|ALTER|TRUNCATE|INSERT|UPDATE|DELETE|GRANT|REVOKE|EXEC|EXECUTE)\\b",
    Pattern.CASE_INSENSITIVE);
```

防止 `;DROP TABLE`、`--DROP`、`DROP()IF` 等绕过。

---

## 5. 质量保障管道

### 5.1 SufficientContextAgent

LLM 判断检索材料是否足以回答问题，输出 JSON：

```json
{"sufficient": false, "missing_info": "缺失过敏史", "suggested_query": "过敏"}
```

失败时保守降级 → `ContextVerdict.sufficient()`。

### 5.2 SelfReflection

三路并行 LLM 检查：

| 检查 | 输入 | 输出 |
|------|------|------|
| 引用缺失 | 答案草稿 | `["无引用的主张1", ...]` |
| 子查询覆盖 | 子查询列表 + 答案 | `["未覆盖的子问题", ...]` |
| 矛盾检测 | 答案 + 上下文 | `["矛盾点", ...]` |

List 解析失败时日志为 `warn` 并返回空列表（安全降级）。

### 5.3 CorrectiveRepair

```
发现问题 → 提取关键词 → 调用 vector_search 补充检索
  → 重新生成答案 → 再次 SelfReflection 验证
  → 通过则返回，不通过则重试（最多 2 次）
```

修复仍失败时返回原答案 + 标记不确定性。

### 5.4 LLM Judge

三维评分，每维 0~1：

| 维度 | 说明 | 默认阈值 |
|------|------|:--------:|
| Faithfulness | 答案是否基于上下文 | ≥0.7 |
| Answer Relevancy | 答案是否直接回答问题 | ≥0.6 |
| Citation Grounding | 关键主张是否有 [N] 引用 | ≥0.8 |

低于阈值时：重生成（最多 2 次），仍不达标则标记降级。

```java
// 提取 thresholds 局部变量避免重复调用链
var thresholds = config.getQuality().getLlmJudge().getThresholds();
scores.isPassing(thresholds.getFaithfulness(), ...)
```

---

## 6. 查询转换与路由

### 6.1 QueryRouter

两级分类策略：

```
用户查询 → forceAgentic? → Agentic
         → forceWorkflow? → Workflow
         → 规则分类:
             <20字符 → SIMPLE → Workflow
             含比较/对比/分析 → 需LLM确认
             LLM判断 → COMPLEX → Agentic / SIMPLE → Workflow
```

### 6.2 查询分解、Step-Back、转换门控

三个组件已实现但暂未接入执行路径（标记 `TODO`）：
- `QueryDecomposer` — 复合问题拆解为原子子查询
- `StepBackQuery` — 检索为空时生成更宽泛查询
- `TransformationGate` — 简单查询跳过深度转换

---

## 7. 状态管理与轨迹审计

### 7.1 AgentState

贯穿执行周期的业务状态容器：

```
AgentState
  ├─ 输入层: originalQuery, userId, config.trajectoryId
  ├─ 执行层: toolCalls, synthesizedContext, sources
  ├─ 质量层: contextVerdict, contextRetryCount
  ├─ 生成层: draftAnswer, reflectionReport, repairCount
  ├─ 输出层: finalAnswer, qualityScores
  └─ 轨迹层: trajectory (StepRecord[]), loopCount, status
```

### 7.2 轨迹持久化

```
AgentOrchestrator → AgentState → TrajectoryRecorder
  → TrajectoryRepository → MySQL agent_trajectories 表
```

JSON 轨迹结构：
```json
{
  "trajectory_id": "traj_20260628_abc123",
  "steps": [
    {"step":1, "type":"TOOL_CALL", "tool":"vector_search", "duration_ms":230},
    {"step":2, "type":"GENERATE", "duration_ms":2000}
  ],
  "total_duration_ms": 3450,
  "status": "COMPLETED"
}
```

HarnessAgent 同时自动将原始对话写入 `workspace/agents/<id>/sessions/` JSONL。

---

## 8. 多级缓存体系

| 层级 | 存储 | TTL | 大小 | 内容 |
|:----:|------|:---:|:----:|------|
| L1 | Caffeine | 10min | 1000 | Embedding 向量 |
| L2 | Caffeine | 5min | 500 | 检索结果 |
| L3 | Caffeine | 30min | 200 | Agent 决策规划 |
| L4 | Redis | 1h | - | 最终 Q&A 答案 |

**关键**: `CacheService` 通过 Caffeine 缓存统计方法（`hitRate`, `estimatedSize`）暴露给监控。

---

## 9. 沙箱安全隔离

| 沙箱模式 | 说明 | 性能影响 |
|---------|------|:--------:|
| NONE（默认） | 工具直接在 JVM 内执行 | 无 |
| gVisor | 轻量级容器沙箱 | ~50ms |
| Docker | 全隔离容器 | ~200ms |

默认关闭，通过 `agentic-rag.sandbox.enabled=true` 启用。

---

## 10. 可观测性

### 10.1 Prometheus 指标

| 指标名 | 类型 | 说明 |
|--------|------|------|
| `agent_decision_total` | Counter | Agent 决策总次数 |
| `agent_tool_call_total` | Counter | 各工具调用次数 (tag: tool) |
| `agent_tool_duration_seconds` | Histogram | 工具调用耗时 |
| `agent_loop_count` | Histogram | 循环轮次分布 |
| `agent_context_complete` | Gauge | 上下文完备性评分 |
| `agent_retry_total` | Counter | 重试次数 |
| `agent_reflection_issues` | Gauge | 自反思问题数 |

### 10.2 MDC 日志

JSON 日志中包含 `trajectoryId`, `userId`, `loopCount`, `toolName`, `agentStatus` 字段，
通过 `logback-spring.xml` 的 LogstashEncoder 输出到 ELK。

---

## 11. 双模型路由

### 11.1 背景

所有 23 处 LLM 调用原共享同一个 `ChatClient` bean（qwen-max），
但 83% 是轻量任务（分类/检查/评分/提取），使用大模型是过度消耗。

### 11.2 三 Bean 架构

| Bean 名称 | 模型 | 用途 |
|-----------|------|------|
| `chatClient`（默认） | qwen-max | Workflow RAG 管线（向后兼容） |
| `@Qualifier("fullChatClient")` | qwen-max | Agentic RAG 答案生成 |
| `@Qualifier("fastChatClient")` | qwen-turbo | 轻量推理任务 |

### 11.3 AiModelConfig

```java
@Bean("fullChatClient")
public ChatClient fullChatClient(DashScopeChatModel chatModel) {
    return ChatClient.create(chatModel);
}

@Bean("fastChatClient")
public ChatClient fastChatClient(DashScopeApi dashScopeApi, ...) {
    DashScopeChatOptions options = DashScopeChatOptions.builder()
        .withModel("qwen-turbo").build();
    DashScopeChatModel fastModel = new DashScopeChatModel(
        dashScopeApi, options, toolCallingManager, retryTemplate, observationRegistry);
    return ChatClient.create(fastModel);
}
```

### 11.4 任务分配

| 模型 | 分配文件数 | 代表组件 |
|:----:|:----------:|---------|
| qwen-max | 11 | `AgentOrchestrator`, `GenerationStage`, `CorrectiveRepair` |
| qwen-turbo | 16 | `QueryRouter`, `SufficientContextAgent`, `SelfReflection`, `LlmJudge`, `RagEvaluator`, `QueryDecomposer`, `StepBackQuery`, `SqlQueryTool`, `LLMCompressor`, `KnowledgeRefiner`, `MultiQueryGenerator`, `MemoryBasedQueryEnhancer`, `KeywordExpansionEnhancer`, `RetrievalEvaluator`, `QueryComplexityClassifier`, `RetrievalOptimizer`, `MemoryExtractor`, `KnowledgeQAService` |

### 11.5 AgentOrchestrator 双注入

```java
@Component
public class AgentOrchestrator {
    private final ChatClient fullChatClient;   // 答案生成
    private final ChatClient fastChatClient;   // 质量组件

    // 轻量任务 → new SufficientContextAgent(fastChatClient)
    // 重量任务 → generateDraft(query, context, fullChatClient)
}
```

---

## 12. 记忆系统

### 12.1 架构演进

```
V1 (优化前):                               V2 (当前):
MySQL ─→ 全量加载 ─→ 内存余弦相似度          MySQL ─→ Milvus 语义搜索 ─→ 批量回查
         (N+1, 堆内存膨胀, 无持久化)                   (持久化向量, 渐进式读取)
```

### 12.2 存储架构

**双写策略**：
- 写入时 → MySQL `long_term_memories` + Milvus `ai_memory_vectors` 同时写入
- 查询时 → Milvus `similaritySearch` → 按 memoryId 批量 `findAllById` 回查 MySQL
- 降级时 → 分页关键词查询（每页 100 条，上限 300 条）

### 12.3 渐进式读取

```java
// 轻量：只返回摘要（不含完整 content）
public List<MemorySummary> searchMemorySummaries(String userId, String query, int topK)
// MemorySummary { id, type, summary, importance }

// 按需：获取完整内容
// repository.findById(memoryId) → LongTermMemory { content, ... }
```

`MemorySummary` 直接从 Milvus metadata 中提取 `summary` 字段，
无需查询 MySQL。用户选择某条摘要后再回查完整内容。

### 12.4 性能优化

| 优化项 | 优化前 | 优化后 |
|--------|--------|--------|
| 向量存储 | Java `ConcurrentHashMap` | Milvus 持久化 |
| 向量计算 | 每次查询实时 `embedText` | 写入时计算一次 |
| N+1 查询 | 逐条 `findById` | 批量 `findAllById` |
| 全量加载 | `getUserMemoriesFromDb()` 全部加载 | 分页加载（100条/页，上限300） |
| 数组复制 | `System.arraycopy + 新数组` | 直接返回 `embed()` 结果 |
| 合并判断 | 全量扫描 | Milvus 搜索 + 分页余弦扫描 |

### 12.5 记忆模型

```java
public class LongTermMemory {
    private String id, userId, content;
    private String summary;             // 内容摘要(前100字符)
    private String keywords;            // 逗号分隔关键词
    private MemoryType type;            // FACT / PREFERENCE / CONTEXT
    private Integer importance;         // 1-10
}
```

---

## 13. SSE 流式输出

### 13.1 端点

```
POST /api/qa/ask/agent/stream  →  text/event-stream
```

### 13.2 事件类型

| 事件 | 触发时机 | 说明 |
|------|---------|------|
| `thinking` | Agent 分析/检索 | 进度提示 |
| `tool_call` | 调用工具 | 工具名称和参数 |
| `tool_result` | 工具返回 | 结果数量 |
| `check` | 质量检查 | 检查结果 |
| `generating` | 开始生成 | - |
| `token` | 逐 Token | 使用 `ChatClient.stream()` |
| `done` | 执行完成 | 完整答案 |
| `error` | 执行出错 | 错误消息 |

### 13.3 实现

```java
@PostMapping("/ask/agent/stream")
public SseEmitter askWithAgentStream(@RequestBody AgenticAskRequest request) {
    SseEmitter emitter = new SseEmitter(60_000L);
    ragRetrievalExecutor.submit(() -> {
        qaService.askWithAgentStream(request, event -> {
            emitter.send(SseEmitter.event()
                .name(event.getType()).data(event.getContent()));
        });
        emitter.complete();
    });
    return emitter;
}
```

---

## 附录：优化历史

| Commit | 内容 | 文件数 |
|--------|------|:------:|
| `bc34383` | Phase 1 核心 (T1.1~T1.10) | 34 |
| `3a89db0` | Phase 1 收尾 + Phase 2 质量 | 11 |
| `ad7fe43` | Phase 3 查询优化 (T3.1~T3.7) | 9 |
| `2a5927c` | Phase 3 剩余 (T3.8~T3.10) | 3 |
| `ee2efdd` | SSE 流式输出 | 4 |
| `920ac09` | 双 ChatClient 模型路由 | 11 |
| `e788642` | 最优 ChatClient 配置 | 12 |
| `4093afc` | Milvus 记忆向量化 | 3 |
| `b63b586` | 渐进式分页读取 | 2 |
| `d5400d8` | 记忆摘要 + 渐进式读取 | 4 |
| `c24c3d1` | 批量 17 项优化 | 10 |
| `HEAD` | **Phase 2 质量 + 遗留项治理** | **9** |
| | Hook -> Middleware 迁移：移除 QualityCheckHook / MetricsCollectHook，替换为 ToolCallCaptureMiddleware | AgentOrchestrator |
| | AgentConfig.Hook 及 4 个子类配置移除 | AgentConfig |
| | 线程安全修复：askWithAgentic save/restore 模式 | KnowledgeQAService |
| | totalDurationMs / qualityGateFailed 元数据透传 | AgenticRagFlow / KnowledgeQAService |
| | selectRagFlow 分类路由逻辑恢复 | KnowledgeQAService |
| | 文档内容 Unicode 非法代理项清理 | KnowledgeEmbeddingService |
| | Step-Back 查询接入 Agent 主循环 | AgentOrchestrator |
| | ToolCallCaptureMiddleware 工具调用捕获 + StepRecord 六阶段轨迹记录 | AgentOrchestrator |
| | SandboxConfigurator DockerFilesystemSpec 实际注入 | SandboxConfigurator |
| | Controller 注释代码清理 | KnowledgeQAController |
