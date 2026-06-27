# Agentic RAG 实现详解

**文档版本**: v1.0  
**编写日期**: 2026-06-15  
**项目**: RAG 智能知识库问答系统  
**框架**: AgentScope-Java 2.0.0-RC3 (HarnessAgent) + Spring AI Alibaba 1.1.2.3

---

## 目录

1. [从 Workflow RAG 到 Agentic RAG](#1-从-workflow-rag-到-agentic-rag)
2. [HarnessAgent 引擎剖析](#2-harnessagent-引擎剖析)
3. [Agent 主循环执行流程](#3-agent-主循环执行流程)
4. [工具框架设计](#4-工具框架设计)
5. [质量保障管道](#5-质量保障管道)
6. [查询转换与路由](#6-查询转换与路由)
7. [状态管理与轨迹审计](#7-状态管理与轨迹审计)
8. [多级缓存体系](#8-多级缓存体系)
9. [沙箱安全隔离](#9-沙箱安全隔离)
10. [可观测性](#10-可观测性)

---

## 1. 从 Workflow RAG 到 Agentic RAG

### 1.1 为什么需要 Agentic RAG

传统 Workflow RAG 采用固定管道模式（Pipeline Pattern），所有查询经过相同的阶段序列：

```
用户 → 查询清洗 → 增强 → 多查询生成 → 并行检索 → CRAG修正
    → 去重 → 过滤 → 重排序 → 压缩 → 生成 → 答案
```

这种架构的核心矛盾在于：**简单问题和复杂问题走同一流程**。

| 场景 | Workflow RAG 的行为 | 问题 |
|------|-------------------|------|
| "今天星期几？" | 走完 11 个阶段 | 过度处理，不必要的 Token 消耗 |
| "比较 Spring Boot 和 Quarkus 的优缺点，给出推荐" | 单轮检索 | 信息不足，无法覆盖多跳推理 |

Agentic RAG 的核心改进是让系统具备**自主决策能力**：

```
Workflow RAG:  管道决定流程，数据流过每个阶段
Agentic RAG:  Agent 决定下一步做什么，动态规划执行路径
```

### 1.2 架构演进策略

项目采用**并行共存 → 渐进替代**的演进策略，而非一刀切替换：

```
用户请求
    │
    ▼
QueryRouter（路由层）
    │
    ├─ SIMPLE ──────→ Workflow RAG（快速路径，零额外开销）
    │
    └─ MODERATE/COMPLEX → Agentic RAG（自主推理路径）
```

这样做的好处：
- **风险可控**：Agentic RAG 出问题时 Workflow 作为降级
- **成本优化**：简单查询不经 Agent 循环，节省 Token 和延迟
- **灰度切换**：可通过 `forceAgentic: true` 配置按需开启

### 1.3 ReActAgent vs HarnessAgent 选型

在 AgentScope-Java 中，两个 Agent 引擎的关键区别：

| 维度 | ReActAgent | HarnessAgent |
|------|-----------|-------------|
| 定位 | 纯推理引擎 | 工程化封装 |
| 工作区 | ❌ 需手动管理 | ✅ `workspace(path)` 自动管理 |
| AGENTS.md | ❌ 需手动构建 System Prompt | ✅ 自动注入 |
| 会话持久化 | ❌ 需手动实现 | ✅ SessionPersistenceHook |
| 双层记忆 | ❌ 无 | ✅ MEMORY.md 自动维护 |
| Hook 管道 | ⚠️ 基础 Hook | ✅ 8+ Middleware |
| 沙箱 | ❌ | ✅ gVisor/Docker |

选择 HarnessAgent 的核心原因是：**省去了大量工程代码**。工作区管理、会话持久化、System Prompt 注入、记忆管理等都是工程落地中最耗时且容易出错的部分。

---

## 2. HarnessAgent 引擎剖析

### 2.1 HarnessAgent 不是什么

> HarnessAgent 不替换 ReActAgent 的推理循环。它是在 ReActAgent 外面包装了工程化能力的"壳"。

```
HarnessAgent（壳）
  ├─ 工作区管理（workspace/）
  ├─ Hook 管道（Middleware）
  ├─ RuntimeContext（多租户）
  ├─ 文件系统抽象（local/sandbox/remote）
  └─ ReActAgent（核心推理引擎 — 内部）
       ├─ 分析意图
       ├─ 规划步骤
       ├─ 调用工具
       ├─ 观察结果
       └─ 循环直到完成
```

### 2.2 工作区（Workspace）

工作区是 HarnessAgent 的核心创新，它是**一切配置的单一事实来源**：

```
workspace/knowledge-agent/
├── AGENTS.md              ← Agent 人格定义（每次 call() 自动注入 System Prompt）
├── MEMORY.md              ← 精炼长期记忆（后台自动维护）
├── knowledge/             ← 领域知识文件（可选）
├── skills/                ← 可复用技能（可选，自动注册为工具）
├── subagents/             ← 子 Agent 声明（可选）
│   ├── data-analyst.json
│   └── web-researcher.json
├── tools.json             ← MCP 服务器配置（可选）
└── agents/<sessionId>/
    ├── context/           ← 会话状态快照
    ├── sessions/          ← 对话历史 JSONL
    └── memory/            ← 每日记忆流水账
```

**AGENTS.md 示例**：
```markdown
# Knowledge Assistant — Agent 人格定义
你是一个智能知识库助手，负责通过检索知识库来回答用户问题。

## 行为准则
1. 分析用户问题，必要时拆解为子问题
2. 使用 vector_search 或 bm25_search 检索知识库
3. 使用 [N] 标记引用来源
4. 信息不足时，说明缺失内容并补充检索
```

**关键机制**：`WorkspaceContextMiddleware`（优先级 900）在每次 `call()` 时读取 `AGENTS.md` 并注入到 System Prompt。开发者**不需要**手动拼接系统提示词。

### 2.3 RuntimeContext（多租户上下文）

RuntimeContext 是每次 `call()` 的身份上下文，天然支持多租户隔离：

```java
RuntimeContext ctx = RuntimeContext.builder()
    .userId(userId)              // 用户标识 — 记忆隔离
    .sessionId(trajectoryId)     // 会话标识 — 目录隔离
    .put("query", query)         // 自定义元数据
    .build();

Msg response = harnessAgent.call(msg, ctx).block();
```

HarnessAgent 根据 `sessionId` 自动：
- 从 `workspace/agents/<sessionId>/` 加载历史会话
- 将本次会话写入 `sessions/` 目录
- 不同用户的工作区完全隔离

### 2.4 Hook 管道（Middleware）

HarnessAgent 将工程化能力通过 **Middleware（中间件）** 管道注入：

```
优先级        Middleware                 职责
───          ──────────                ──────────────────
 10          CompactionMiddleware      对话超阈值时自动压缩
 20          TokenCounterMiddleware    Token 计数与限制
 50          SubagentsMiddleware       子 Agent 工具注册
100          AgentTraceMiddleware      追踪链路
200+         [自定义 Middleware]        质量检查/指标采集
900          WorkspaceContextMiddleware 注入 AGENTS.md
900          SessionPersistenceMiddleware 会话持久化
```

**注意**：v2.0.0-RC3 中 `Hook` 接口已标记 deprecated，推荐使用 `MiddlewareBase`。Phase 1 仍使用 Hook 的原因：
1. Hook API 仍然可用
2. Phase 1 的 Hook 逻辑简单（仅日志和指标）
3. 后续可一次性迁移到 Middleware

### 2.5 构建 HarnessAgent

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("KnowledgeAssistant")
    .model(DashScopeChatModel.builder()
        .apiKey(System.getenv("DASHSCOPE_API_KEY"))
        .modelName("qwen-max")
        .build())
    .workspace(Paths.get("./workspace/knowledge-agent"))
    .maxIters(5)                    // 最大推理步数
    .hooks(List.of(                 // 自定义 Hook
        new QualityCheckHook(),
        new MetricsCollectHook()
    ))
    .build();
```

---

## 3. Agent 主循环执行流程

这是整个系统的核心。`AgentOrchestrator.execute()` 实现了完整的执行流程：

### 3.1 整体执行流程

```
AgentOrchestrator.execute(query, userId)
  │
  ├─ 0. 初始化 AgentState + MDC 上下文
  │
  ├─ 阶段 1: HarnessAgent 自主推理
  │    call(msg, ctx).block()
  │    ├─ 自动注入 AGENTS.md（System Prompt）
  │    ├─ ReAct 推理循环（分析→工具调用→观察→循环）
  │    └─ 自动会话持久化
  │
  ├─ 阶段 2: SufficientContextAgent
  │    ├─ LLM 判断上下文是否完备
  │    ├─ 不完备 → 补充检索 → 重试（最多 3 次）
  │    └─ 连续 2 轮无改善 → 提前终止
  │
  ├─ 阶段 3: 生成答案草稿
  │    └─ ChatClient 基于完整上下文生成
  │
  ├─ 阶段 4: SelfReflection + CorrectiveRepair
  │    ├─ 引用缺失检查 → 补充检索 + 重新生成
  │    ├─ 子查询覆盖检查 → 专项检索 + 重新生成
  │    ├─ 矛盾检测 → 重评估 + 重新生成
  │    └─ 最大重试 2 次
  │
  ├─ 阶段 5: LLM Judge（可选）
  │    ├─ Faithfulness/Relevancy/Citation 三维评分
  │    ├─ 低于阈值 → 重生成（最多 2 次）
  │    └─ 仍不达标 → 标记降级
  │
  └─ 最终: 记录轨迹到 MySQL + 清理 MDC
```

### 3.2 循环控制矩阵

为了防止失控，设置了多层保护：

```yaml
agent:
  max-loops: 5              # HarnessAgent 最大推理步数
  max-context-retries: 3     # Context Agent 最大重检
  max-repair-retries: 2      # 修复最大重试
  total-steps-limit: 20      # 总步数硬性上限
  max-timeout-ms: 30000      # 总超时
  single-tool-timeout-ms: 15000  # 单工具超时
```

超限行为：
- 超时 → 返回已找到的局部信息
- 循环超限 → 返回当前最佳答案
- 严重错误 → 降级到 Workflow RAG

### 3.3 降级策略

```java
// 三种降级级别
switch (failureType) {
    case TIMEOUT:
        // 返回部分结果 + 提示语
        return "抱歉，处理超时。以下是我已找到的信息：...";

    case MAX_LOOPS_EXCEEDED:
        // 返回当前最佳结果
        return haveDraft ? draftAnswer : "需要更多信息才能完整回答";

    case CRITICAL_ERROR:
        // 退回到 AdvancedRagFlow（Workflow 模式）
        return advancedRagFlow.executeRag(query, userId, null);
}
```

---

## 4. 工具框架设计

### 4.1 AgentTool 接口

工具是 Agent 与外部世界的接口。每个工具包装一个现有检索组件：

```java
public interface AgentTool {
    String getName();                          // "vector_search"（snake_case）
    String getDescription();                   // LLM 理解工具用途
    Map<String, Object> getParametersSchema();  // JSON Schema 格式
    ToolResult execute(Map<String, Object>);   // 执行逻辑
    default boolean isAvailable();             // 可用性检查
}
```

**设计要点**：
- `name` 使用 `snake_case`（如 `vector_search`），LLM 更易识别
- `description` 包含适用场景和限制，帮助 LLM 做出正确的工具选择
- `getParametersSchema()` 返回 JSON Schema，HarnessAgent 据此生成 Function Calling 参数

### 4.2 工具注册机制

```
Spring 容器                    ToolRegistry              HarnessAgent
    │                              │                          │
    ├── VectorSearchTool ──────────┤                          │
    ├── Bm25SearchTool ────────────┤                          │
    ├── ExternalSearchTool ────────┤── getAllTools() ────────>│ .toolkit(...)
    ├── MemoryQueryTool ───────────┤                          │ .hooks(...)
    └── SqlQueryTool ──────────────┤                          │ .build()
```

`ToolRegistry` 统一收集 Spring 容器中的 `AgentTool` Bean，供给 HarnessAgent 注册。

### 4.3 VectorSearchTool（核心检索）

包装 `HybirdContentRetriever`，实现**向量 + BM25 混合检索 + RRF 融合**：

```java
HybirdContentRetriever.retrieve(query, topK, threshold, source)
    → Vector Search (Milvus, topK * 2)
    → BM25 Search (本地索引, topK * 2)
    → RRF 融合: score(d) = 0.7/(60 + r_vec) + 0.3/(60 + r_bm25)
    → 返回 List<Document>
```

### 4.4 SqlQueryTool（Text-to-SQL）

将自然语言转为 SQL 并执行，含三层安全防护：

```
用户: "查询上个月的订单总量"
    │
    ▼
① Text-to-SQL: LLM 将自然语言转为 SQL
    "SELECT COUNT(*) FROM orders WHERE created_at >= DATE_SUB(NOW(), INTERVAL 1 MONTH)"
    │
    ▼
② SQL 安全校验（关键字白名单）
    ├─ 必须是 SELECT 开头
    ├─ 禁止 INSERT/UPDATE/DELETE/DROP/ALTER/CREATE/TRUNCATE
    └─ 不通过 → 拒绝执行
    │
    ▼
③ JDBC 执行（超时 5s，行数限制 1000）
    → 结果格式化为表格文本
```

### 4.5 工具的条件启用

通过 `@ConditionalOnProperty` 控制工具是否注册，P1 工具默认关闭：

```yaml
agentic-rag.tool:
  external-search.enabled: false   # 需 API Key
  sql-query.enabled: false         # 需数据库权限
  memory-query.enabled: false      # 需记忆数据
```

---

## 5. 质量保障管道

质量保障是 Agentic RAG 区别于 Workflow RAG 的核心能力。我们实现了**四阶段质量管道**：

### 5.1 SufficientContextAgent（上下文完备性检查）

**解决的问题**：检索到的材料是否足以回答问题？不足时缺什么？

**实现**：LLM 判断 + JSON 结构化输出

```
输入: 用户问题 + 检索上下文

LLM Prompt:
    判断以下检索到的信息是否足以回答用户问题。
    以 JSON 格式返回：
    {
      "sufficient": true/false,
      "missing_info": "缺失什么信息",
      "suggested_query": "建议的补充搜索词"
    }

输出: ContextVerdict(sufficient, missingInfo, suggestedQuery)
```

**循环控制**：
- 最多 3 轮重检
- 连续 2 轮无改善 → 提前终止（防止死循环）
- 输入长度截断到 3000 字符（防止 Token 溢出）

### 5.2 SelfReflection（自反思）

**解决的问题**：生成的答案质量如何？有没有遗漏或矛盾？

**三路并行检查**：

```
① 引用缺失检查
   Prompt: "检查答案中的关键主张是否有 [N] 引用标记"
   输出: ["无引用的主张1", "无引用的主张2"]

② 子查询覆盖检查
   Prompt: "检查答案是否覆盖了所有子问题"
   输出: ["未覆盖的子问题1"]

③ 矛盾检测
   Prompt: "检测答案是否存在内部矛盾，或与检索材料矛盾"
   输出: ["矛盾点1"]
```

每个检查独立调用 LLM，返回 JSON 列表。三个列表汇总为 `ReflectionReport`。

### 5.3 CorrectiveRepair（纠错）

**解决的问题**：自反思发现问题后，如何修复？

**修复流程**：

```
ReflectionReport 发现问题
    │
    ▼
① 提取关键词（从未覆盖子查询 + 无引用声明中提取）
    │
    ▼
② 调用 vector_search 补充检索
    │
    ▼
③ 基于完整上下文重新生成答案
    │
    ▼
④ 再次 SelfReflection 验证
    ├─ 通过 → 返回修复后答案
    └─ 不通过 → 回到①（最多 2 次）
        └─ 仍失败 → 返回原答案 + 标记不确定性
```

### 5.4 LLM Judge（质量评估）

**解决的问题**：答案返回用户前，量化评估质量。

**三个维度**：

| 维度 | 含义 | 评分范围 | 阈值 |
|------|------|:--------:|:----:|
| Faithfulness | 答案是否基于上下文，无编造 | 0~1 | ≥0.7 |
| Answer Relevancy | 答案是否直接回答问题 | 0~1 | ≥0.6 |
| Citation Grounding | 关键主张是否有 [N] 引用 | 0~1 | ≥0.8 |

**低于阈值的处理**：尝试重生成（最多 2 次），仍不达标则标记降级。

---

## 6. 查询转换与路由

### 6.1 QueryRouter（路由策略）

**解决的问题**：如何在不增加简单查询延迟的前提下，让复杂查询获得 Agent 能力？

**两级分类策略**：

```
用户查询
    │
    ├─ forceAgentic=true? ───────────────→ Agentic
    ├─ forceWorkflow=true? ──────────────→ Workflow
    │
    └─ 规则分类:
        ├─ 长度 < 20 字符? → SIMPLE → Workflow
        ├─ 含"比较"/"对比"/"vs"? → 需 LLM 确认
        └─ 否则 → SIMPLE → Workflow

        LLM 分类（仅规则无法判断时）:
        ├─ COMPLEX/MODERATE → Agentic
        └─ SIMPLE → Workflow
```

**设计要点**：
- LLM 分类只占**边界情况**（规则无法明确判断的），大部分查询走规则路径
- `simple-threshold: 20` — 小于 20 字符的查询直接判定为 SIMPLE

### 6.2 QueryDecomposer（查询分解）

**解决的问题**：复合问题如何拆解为可独立检索的原子问题？

```java
"比较 Spring Boot 和 Quarkus 的优缺点，并给出基于 Java 21 + K8s 的推荐"
    │ LLM 分解
    ▼
[
  {"id": "sq1", "query": "Spring Boot 微服务特点",     "depends_on": []},
  {"id": "sq2", "query": "Quarkus 微服务特点",         "depends_on": []},
  {"id": "sq3", "query": "Java 21 + K8s 技术栈推荐",   "depends_on": ["sq1", "sq2"]}
]
```

- 无依赖的子查询（sq1, sq2）可**并行执行**
- 有依赖的子查询（sq3 依赖 sq1+sq2）**等待前置完成**
- 每个子查询附带同义变体（Multi-Query 扩展）

### 6.3 StepBackQuery（后退式查询）

**解决的问题**：检索结果为空或得分低时怎么办？

LLM 生成更宽泛的查询词：

```
"Spring Boot 3.5 @EnableWebMvc 变更 配置类"
    │ Step-Back
    ▼
"Spring Boot 3.5 Web MVC 变更"
    │ 再 Step-Back
    ▼
"Spring Boot Web MVC"
```

### 6.4 TransformationGate（转换门控）

**解决的问题**：如何避免对简单查询进行不必要的深度转换？

```java
boolean shouldTransform(String query) {
    // 短查询直接跳过
    if (query.length() < 20) return false;
    // 无复杂关键词跳过
    if (!containsComplexWord(query)) return false;
    // 通过 → 需要转换
    return true;
}
```

---

## 7. 状态管理与轨迹审计

### 7.1 AgentState vs RuntimeContext

两个状态容器分工明确：

```
AgentState（业务状态 — 我们管理）
  ├─ 输入层: originalQuery, userId, config
  ├─ 执行层: toolCalls, synthesizedContext, sources
  ├─ 质量层: contextVerdict, contextRetryCount
  ├─ 生成层: draftAnswer, reflectionReport, repairCount
  ├─ 输出层: finalAnswer, qualityScores
  └─ 轨迹层: trajectory (List<StepRecord>), loopCount, status

RuntimeContext（运行时上下文 — HarnessAgent 管理）
  ├─ userId, sessionId
  └─ 自定义 metadata (query, traceId)
```

### 7.2 轨迹持久化

每次 Agent 执行完成后，轨迹被持久化到 MySQL：

```json
{
  "trajectory_id": "traj_20260614_abc123",
  "user_id": "user123",
  "query": "比较 Spring Boot 和 Quarkus",
  "steps": [
    {"step": 1, "type": "DECOMPOSE", "description": "分解为3个子查询", "duration_ms": 1200},
    {"step": 2, "type": "TOOL_CALL", "tool_name": "vector_search", "duration_ms": 230},
    {"step": 3, "type": "REFLECT", "description": "自反思检查", "duration_ms": 1500}
  ],
  "total_duration_ms": 5290,
  "status": "COMPLETED",
  "quality_scores": {"faithfulness": 0.92, "answer_relevancy": 0.88}
}
```

**数据流向**：
```
AgentOrchestrator → AgentState → TrajectoryRecorder → TrajectoryRepository → MySQL
                                                                     ↓
HarnessAgent 自动 → workspace/agents/<id>/sessions/ → JSONL 文件（原始对话）
```

- 结构化轨迹（业务数据）→ MySQL `agent_trajectories` 表
- 原始对话（HarnessAgent 自动）→ `workspace/sessions/` JSONL

### 7.3 轨迹的两种用途

| 用途 | 数据源 | 查询方式 |
|------|--------|---------|
| API 查询轨迹详情 | MySQL agent_trajectories | `GET /api/qa/trajectory/{id}` |
| 审计回放 | MySQL + JSONL | 需要完整上下文时 |
| 质量分析 | MySQL 聚合查询 | 统计循环次数、评分分布 |

---

## 8. 多级缓存体系

缓存分四个层级，从快到慢：

```
L1: Embedding 缓存（Caffeine, 10min）
    查询 → Embedding 向量
    命中时: 跳过 Embedding API 调用
    大小: 1000 条

L2: 检索结果缓存（Caffeine, 5min）
    查询 → 检索文档列表
    命中时: 跳过向量检索和 BM25 搜索
    大小: 500 条

L3: Agent 决策缓存（Caffeine, 30min）
    相似查询 → 规划结果（工具选择、查询分解）
    命中时: 跳过 LLM 决策
    大小: 200 条

L4: 最终答案缓存（Redis, 1h）
    用户+查询 → 答案对象
    命中时: 完全跳过 Agent 流程
    复用现有 CacheService
```

**缓存统计**：通过 `CacheService.getCacheStats()` 可获取每个缓存的命中率和大小，用于监控。

---

## 9. 沙箱安全隔离

可选特性，默认关闭。为不可信工具提供隔离执行环境：

| 沙箱模式 | 说明 | 适用场景 | 性能影响 |
|---------|------|---------|---------|
| NONE（默认） | 工具直接在 JVM 内执行 | 内部可信工具 | 无 |
| gVisor | 轻量级容器沙箱 | 外部搜索、SQL 查询 | ~50ms 启动 |
| Docker | 全隔离容器 | 高风险操作 | ~200ms 启动 |

启用方式：
```yaml
agentic-rag:
  sandbox:
    enabled: true
    mode: gVisor
    sandbox-tools:
      - external_search
      - sql_query
    memory-limit: 512m
    network-access: false
```

---

## 10. 可观测性

### 10.1 Prometheus 指标

新增 7 个 Agent 指标：

| 指标名 | 类型 | 说明 |
|--------|------|------|
| `agent_decision_total` | Counter | Agent 决策总次数 |
| `agent_tool_call_total` | Counter | 各工具调用次数 |
| `agent_tool_duration_seconds` | Histogram | 工具调用耗时 |
| `agent_loop_count` | Histogram | 循环轮次分布 |
| `agent_context_complete` | Gauge | 上下文完备性评分 |
| `agent_retry_total` | Counter | 重试次数及原因 |
| `agent_reflection_issues` | Gauge | 自反思问题数 |

### 10.2 结构化日志

通过 MDC（Mapped Diagnostic Context）在 JSON 日志中注入 Agent 上下文：

```xml
<includeMdcKeyName>trajectoryId</includeMdcKeyName>
<includeMdcKeyName>userId</includeMdcKeyName>
<includeMdcKeyName>loopCount</includeMdcKeyName>
<includeMdcKeyName>toolName</includeMdcKeyName>
```

日志示例（JSON 格式，ELK 友好）：
```json
{
  "@timestamp": "2026-06-14T23:37:13.000+08:00",
  "level": "INFO",
  "logger": "o.e.c.r.a.a.AgentOrchestrator",
  "message": "轨迹持久化完成",
  "trajectoryId": "traj_20260614_abc123",
  "userId": "user123",
  "agentStatus": "COMPLETED",
  "loopCount": 2
}
```

### 10.3 关键日志点

| 位置 | 级别 | 记录内容 |
|------|:----:|---------|
| Agent 启动 | INFO | 查询、用户、配置参数 |
| 工具调用 | INFO | 工具名、参数、耗时、结果摘要 |
| 循环轮次 | INFO | 当前轮次、已耗时 |
| 质量检查 | INFO | 检查结果、评分 |
| 决策异常 | WARN | 决策失败、重试信息 |
| 系统异常 | ERROR | 异常堆栈、Agent 状态快照 |

---

## 附录：关键类速查表

| 类名 | 包路径 | 一句话说明 |
|------|--------|-----------|
| `AgentOrchestrator` | `agent/` | 主循环引擎，协调 HarnessAgent 和 Quality Pipeline |
| `AgentState` | `agent/` | 贯穿执行周期的业务状态容器 |
| `AgentConfig` | `agent/` | `@ConfigurationProperties` 配置绑定 |
| `HarnessAgent` | agentscope-harness | AgentScope 提供的工程化 Agent 引擎 |
| `AgentTool` | `tool/` | 工具接口，包装现有检索组件 |
| `ToolRegistry` | `tool/` | 工具注册表，收集 Spring Bean 供 HarnessAgent |
| `VectorSearchTool` | `tool/` | 向量+BM25 混合检索（P0） |
| `Bm25SearchTool` | `tool/` | BM25 关键词搜索（P0） |
| `ExternalSearchTool` | `tool/` | 互联网搜索（P1，默认关闭） |
| `SqlQueryTool` | `tool/` | Text-to-SQL（P1，默认关闭） |
| `MemoryQueryTool` | `tool/` | 短期+长期记忆查询（P1，默认关闭） |
| `SufficientContextAgent` | `quality/` | LLM 上下文完备性检查 |
| `SelfReflection` | `quality/` | 自反思：引用/覆盖/矛盾检查 |
| `CorrectiveRepair` | `quality/` | 纠错：补充检索+验证+降级 |
| `LlmJudge` | `quality/` | 运行时质量评估 |
| `QueryRouter` | `router/` | 规则+LLM 两级路由 |
| `QueryDecomposer` | `transform/` | LLM 查询分解+依赖 DAG |
| `StepBackQuery` | `transform/` | 后退式宽泛查询生成 |
| `TransformationGate` | `transform/` | 简单查询跳过转换 |
| `TrajectoryRecorder` | `trajectory/` | 轨迹持久化到 MySQL |
| `TrajectoryEntity` | `trajectory/` | 轨迹 JPA 实体 |
| `SandboxConfigurator` | `sandbox/` | 沙箱隔离配置器 |
