# Agentic RAG 第一阶段实施报告

**文档版本**: v1.0  
**编写日期**: 2026-06-14  
**项目名称**: RAG 智能知识库问答系统  
**阶段**: Phase 1 — 核心 Agent 循环（P0）  
**状态**: ✅ 实施完成（T1.1 ~ T1.10 编译通过）  
**提交记录**: `bc34383` — 34 files, 7036 insertions

---

## 目录

1. [项目概述](#1-项目概述)
2. [架构设计思路](#2-架构设计思路)
3. [核心技术选型](#3-核心技术选型)
4. [模块实现详解](#4-模块实现详解)
5. [实现过程中的关键决策](#5-实现过程中的关键决策)
6. [文档体系](#6-文档体系)
7. [当前局限与后续规划](#7-当前局限与后续规划)

---

## 1. 项目概述

### 1.1 项目背景

本项目将一个基于 Spring AI 1.1.7 构建的 **Workflow RAG（固定管道模式）** 知识库问答系统升级为 **Agentic RAG（自主代理循环）** 系统。原系统采用 11 阶段固定管道（查询清洗→增强→多查询生成→并行检索→CRAG修正→去重→过滤→重排序→压缩→生成），所有查询无论简单还是复杂都走同一流程。

**核心矛盾**：固定管道无法适应查询多样性 — 简单问题被过度处理（增加延迟和 Token 消耗），复杂问题则处理不足（单轮检索无法覆盖多跳推理需求）。

Agentic RAG 的核心价值在于让系统具备**自主决策能力**：
- 根据查询复杂度动态选择处理路径
- 多轮检索直到信息完备
- 自我检查答案质量并纠错
- 完整记录决策过程便于审计

### 1.2 定量目标

| 指标 | 当前系统 | 目标 | Phase 1 达成度 |
|------|---------|------|:-------------:|
| 多跳准确率 | ~50% | ≥80% | 架构已支持，待 Phase 2 质量门禁完善 |
| 幻觉率 | ~20-28% | ≤10% | 同上 |
| 简单查询 p95 延迟 | ~3s | ≤3s | 路由策略确保 SIMPLE 走 Workflow，不增加延迟 |
| 复杂查询 p95 延迟 | ~6s | ≤8s | 受 HarnessAgent 循环控制，上限 30s |
| 可审计性 | 无 | 完整轨迹记录 | AgentState + StepRecord 结构已完成 |

### 1.3 阶段范围

Phase 1 聚焦于建立 Agentic RAG 的**核心骨架**：从依赖引入、工具框架搭建、HarnessAgent 集成，到 API 端点和路由策略的完整链路打通。质量保障和高级特性在后续阶段实现。

```
Phase 1 边界:
  ✅  AgentScope-Java HarnessAgent 集成
  ✅  工具注册框架（AgentTool + ToolRegistry）
  ✅  两个核心检索工具（向量搜索 + BM25）
  ✅  Agent 执行引擎（AgentOrchestrator）
  ✅  API 端点 + 路由策略
  ⏳ 轨迹数据库持久化（T1.11，已预留结构）
  ⏳ 外部搜索工具（T1.12，P1 特性）
```

---

## 2. 架构设计思路

### 2.1 从 Workflow 到 Agentic 的演进策略

```
时间轴 →
┌────────────────────────────────────────────────────────────┐
│  阶段 0: 现状                                              │
│  Workflow RAG (AdvancedRagFlow) — 11 阶段固定管道          │
│  用户 → 清洗 → 增强 → 多查询 → 并行检索 → CRAG → ... → 答案 │
└────────────────────────┬───────────────────────────────────┘
                         │ 新增，不改动现有
                         ▼
┌────────────────────────────────────────────────────────────┐
│  阶段 1: 并行共存 (当前)                                    │
│  ┌────────────────┐    ┌────────────────────────────────┐   │
│  │ Workflow RAG   │    │ Agentic RAG (新增)              │   │
│  │ AdvancedRagFlow│    │ AgenticRagFlow                  │   │
│  │ (保留，降级)   │    │  └─ AgentOrchestrator           │   │
│  └────────────────┘    │      └─ HarnessAgent            │   │
│                        └────────────────────────────────┘   │
│  路由: QueryRouter (规则+LLM) → SIMPLE→Workflow / COMPLEX→Agentic │
└────────────────────────────────────────────────────────────┘
                         │ 稳定后淘汰 Workflow
                         ▼
┌────────────────────────────────────────────────────────────┐
│  阶段 N: Agentic 统一（远期目标）                            │
│  Agentic RAG 作为唯一 RAG 模式                              │
└────────────────────────────────────────────────────────────┘
```

**设计考量**：
- **并行共存而非替代**：新系统不稳定时 Workflow 作为降级路径，风险可控
- **路由策略保护简单查询**：短查询（<20字符）走快速路径，避免 Agent 循环的额外开销
- **渐进式迁移**：后续可通过配置 `forceAgentic: true` 灰度切换

### 2.2 HarnessAgent 选型思考

在 ReActAgent 和 HarnessAgent 之间的选择是 Phase 0 的关键决策：

| 维度 | ReActAgent | HarnessAgent（选定） |
|------|-----------|-------------------|
| 定位 | 纯推理引擎 | 工程化封装（推理+工作区+记忆+Hook） |
| 工作区 | ❌ 无 | ✅ .workspace(path) 结构化目录 |
| AGENTS.md | ❌ 需手动 System Prompt | ✅ 自动注入 |
| 会话持久化 | ❌ 需手动实现 | ✅ SessionPersistenceHook 自动完成 |
| 双层记忆 | ❌ 无 | ✅ MEMORY.md 自动维护 |
| Hook 管道 | ⚠️ 基础 | ✅ 8+ 内置 Middleware |
| 沙箱隔离 | ❌ 无 | ✅ gVisor/Docker |

选择 HarnessAgent 的核心原因是：**它为我们省去了大量工程代码**。如果使用裸 ReActAgent，我们需要自研工作区管理、会话持久化、System Prompt 注入、记忆管理等 —— 这些恰好是工程落地中最耗时且容易出错的部分。

### 2.3 与现有系统的集成边界

Phase 1 的一个重要设计原则是**最小侵入**：新增代码全部在 `rag/agentic/` 包下，不修改现有的 Pipeline 阶段和 RagFlow 实现。

```
集成点                    侵入程度
───────────────────────── ────────
AbstractRagFlow 继承       ✅ 新增子类，不修改基类
KnowledgeQAService 扩展    ⚠️ 新增方法，不修改原有逻辑
KnowledgeQAController 扩展 ⚠️ 新增端点，保留原有端点
application-dev.yml 追加   ⚠️ 新增配置段，不修改现有配置
pom.xml                   ✅ 新增依赖 agentscope-harness
HybirdContentRetriever     ❌ 被包装调用，无需修改
Bm25Indexer                ❌ 同上
```

---

## 3. 核心技术选型

### 3.1 框架选型

| 技术 | 版本 | 用途 | 选型理由 |
|------|------|------|---------|
| AgentScope-Java | 2.0.0-RC3 | Agent 引擎 | 唯一完整的 Java Harness Framework，阿里通义实验室出品 |
| DashScopeChatModel | 内置 | LLM 底座 | 与现有 Spring AI Alibaba 共享 DashScope API，无额外成本 |
| Spring AI Alibaba | 1.1.2.3 | 基础设施 | 已有依赖，提供 ChatClient、Actuator 等 |

### 3.2 实际 API vs 预期差异

在实施过程中发现了一些设计文档与实际 API 的差异，这是本次实施最重要的经验：

| 预期 | 实际 | 影响 |
|------|------|------|
| 包名 `com.alibaba.agentscope` | `io.agentscope` | Maven 坐标和 import 全部修正 |
| `.tool()` 注册工具 | `.toolkit(Toolkit)` + builder 字段 | 需要额外构建 Toolkit 对象 |
| `Hook` API 为最佳实践 | `Hook` 已标记 deprecated，推荐 `MiddlewareBase` | Phase 1 继续使用 Hook，后续迁移 |
| `.compactionConfig()` setter | 包级私有字段，无公开 setter | 使用默认值，无法在 builder 中配置 |
| `.memoryConfig()` setter | 同上 | 同上 |

### 3.3 工具注册方案

我们选择了**自定义 AgentTool 接口**而非直接使用 AgentScope 的 AgentTool 接口，原因是：

1. AgentScope 的 `AgentTool.callAsync()` 返回 `Mono<ToolResultBlock>`（响应式），而我们现有的检索组件（`HybirdContentRetriever` 等）是同步调用
2. 自定义接口提供 `execute(Map<String, Object>)` 返回 `ToolResult`，更直观
3. 通过 `ToolRegistry` 统一收集后传入 HarnessAgent builder，保持 Spring Bean 管理

```
Spring 容器                    ToolRegistry              HarnessAgent
    │                              │                          │
    ├── VectorSearchTool ──────────┤                          │
    ├── Bm25SearchTool ────────────┤                          │
    ├── ExternalSearchTool ────────┤── getAllTools() ────────>│ .toolkit(...)
    ├── MemoryQueryTool ───────────┤                          │ .hooks(...)
    └── SqlQueryTool ──────────────┤                          │ .build()
```

---

## 4. 模块实现详解

### 4.1 配置层（T1.1）

**解决的问题**：所有 Agentic RAG 的可配置参数集中管理。

**核心类**：
- `AgentConfig.java` — 使用 `@ConfigurationProperties(prefix = "agentic-rag")` 绑定 YAML 配置
- 包含 9 个内部配置类：Agent、Tool、Workspace、Quality、Hook、Sandbox、Routing、Trajectory、Memory
- 每个配置项有合理的默认值（如 maxLoops=5, maxTimeoutMs=30000）

**配置层级结构**：
```
agentic-rag
  ├── enabled             全局开关
  ├── agent               引擎参数（maxLoops, timeout, model）
  ├── tool                各工具参数（topK, enabled）
  ├── workspace           工作区配置（path, memory）
  ├── quality             质量门禁（contextCheck, selfReflection, llmJudge）
  ├── hook                Hooks 配置（compaction, tokenCounter, rateLimit, safety）
  ├── sandbox             沙箱配置（mode, memoryLimit）
  ├── routing             路由策略（forceAgentic, simpleThreshold）
  └── trajectory          轨迹配置（retentionDays）
```

### 4.2 工具框架（T1.2~T1.5）

**解决的问题**：将现有检索组件统一封装为 Agent 可调用的工具。

**接口设计**：
```java
public interface AgentTool {
    String getName();                          // LLM 选择工具的依据
    String getDescription();                   // LLM 理解工具用途的依据
    Map<String, Object> getParametersSchema(); // LLM 生成调用参数的依据
    ToolResult execute(Map<String, Object>);   // 实际执行逻辑
    default boolean isAvailable();             // 可用性检查
}
```

**设计要点**：
- `name` 使用 `snake_case`（如 `vector_search`），LLM 更易识别
- `description` 包含适用场景和限制，帮助 LLM 做出正确的工具选择
- `getParametersSchema()` 返回 JSON Schema 格式，AgentScope 据此生成 Function Calling 参数
- 内部使用 `ResilienceHelper` 复用现有的容错机制

**VectorSearchTool 的包装逻辑**：
```
Agent ──call──> VectorSearchTool.execute({query, top_k})
                      │
                      ▼
            HybirdContentRetriever.retrieve(query, topK, threshold, source)
                      │
                      ▼
            RRF 融合（向量 0.7 + BM25 0.3）
                      │
                      ▼
            ToolResult.success(List<Document>)
```

### 4.3 执行引擎（T1.6~T1.7）

这是 Phase 1 的核心，实现了 Agent 主循环引擎。

**AgentOrchestrator 执行流程**：

```
AgentOrchestrator.execute(query, userId)
  │
  ├─ 1. 构建 RuntimeContext (userId + sessionId + traceId)
  │
  ├─ 2. HarnessAgent.call(msg, ctx)
  │      内部自动完成:
  │      ├─ 加载 AGENTS.md → 注入 System Prompt
  │      ├─ 加载 MEMORY.md → 长期记忆
  │      ├─ ReAct 推理循环 → 调用工具 → 观察 → 循环
  │      └─ 会话自动写入 sessions/
  │
  ├─ 3. SufficientContextAgent 检查（Phase 2 完善）
  │      上下文不足 → 补充检索 → 循环（max 3 次）
  │
  ├─ 4. 生成答案草稿（ChatClient）
  │
  ├─ 5. Self-Reflection（Phase 2 实现，当前跳过）
  │
  ├─ 6. LLM Judge（Phase 3 实现，当前跳过）
  │
  └─ 7. 返回最终答案 + 记录轨迹
```

**HarnessAgent 的构建**：

```java
HarnessAgent.builder()
    .name("KnowledgeAssistant")
    .model(DashScopeChatModel.builder()
        .apiKey(System.getenv("DASHSCOPE_API_KEY"))
        .modelName("qwen-max")
        .build())
    .workspace(Paths.get("./workspace/knowledge-agent"))
    .maxIters(5)                    // 最大推理步数
    .hooks(List.of(                  // 自定义 Hook
        new QualityCheckHook(),
        new MetricsCollectHook()
    ))
    .build();
```

**RuntimeContext 的多租户设计**：

每次 Agent 调用通过 `RuntimeContext` 传递用户上下文，实现天然的多租户隔离：

```java
RuntimeContext ctx = RuntimeContext.builder()
    .userId(userId)              // 用户标识 — 记忆隔离
    .sessionId(trajectoryId)     // 会话标识 — 目录隔离
    .put("query", query)         // 自定义元数据
    .build();
```

HarnessAgent 根据 `sessionId` 自动将不同用户的会话写入不同的工作区子目录：`workspace/agents/<sessionId>/sessions/`，无需手动管理。

### 4.4 状态模型（T1.6）

AgentState 的设计遵循**分层职责**原则：

```
AgentState（业务状态）
  ├─ 输入层: originalQuery, userId, config
  ├─ 执行层: toolCalls, synthesizedContext, sources
  ├─ 质量层: contextVerdict, contextRetryCount
  ├─ 生成层: draftAnswer, reflectionReport, repairCount
  ├─ 输出层: finalAnswer, qualityScores, qualityGateFailed
  └─ 轨迹层: trajectory (List<StepRecord>), loopCount, status

RuntimeContext（运行时上下文，由 HarnessAgent 管理）
  ├─ userId, sessionId
  └─ 自定义 metadata
```

两者分工明确：AgentState 管理 RAG 问答流程的业务状态，RuntimeContext 管理执行时的多租户上下文。

### 4.5 API 与路由（T1.8~T1.10）

**API 端点**：

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/qa/ask` | 原有 Workflow RAG 问答（保持不变） |
| POST | `/api/qa/ask/agent` | Agentic RAG 问答（新增） |
| GET | `/api/qa/trajectory/{id}` | 轨迹查询（新增，Phase 3 完善） |

**路由策略**：

QueryRouter 实现了两级分类：

```
用户查询
  │
  ├─ forceAgentic=true? ───────────────→ Agentic RAG
  ├─ forceWorkflow=true? ──────────────→ Workflow RAG
  │
  └─ 规则分类:
      ├─ 长度 < 20 字符? → SIMPLE → Workflow RAG
      ├─ 含"比较"/"对比"/"vs"? → AMBIGUOUS → LLM 分类兜底
      └─ 否则 → SIMPLE → Workflow RAG

      LLM 分类:
      ├─ COMPLEX/MODERATE → Agentic RAG
      └─ SIMPLE → Workflow RAG
```

### 4.6 工作区结构

```
workspace/knowledge-agent/
├── AGENTS.md              ← Agent 人格定义（版本控制）
├── MEMORY.md              ← 精炼长期记忆（自动维护）
├── knowledge/             ← 领域知识（可选）
├── skills/                ← 技能目录（可选，自动注册）
├── subagents/             ← 子 Agent 声明（可选）
└── agents/                ← 会话目录（自动生成，已 gitignore）
    └── <sessionId>/
        ├── sessions/       ← 对话历史 JSONL
        └── memory/         ← 每日记忆流水账
```

`AGENTS.md` 是 HarnessAgent 的关键配置，替代了传统的手动 System Prompt 构建：

```markdown
# Knowledge Assistant — Agent 人格定义
你是一个智能知识库助手...
## 行为准则
1. 分析用户问题，必要时拆解为子问题
2. 使用 vector_search 或 bm25_search 检索知识库
3. 使用 [N] 标记引用来源
4. 信息不足时补充检索
```

---

## 5. 实现过程中的关键决策

### 5.1 HarnessAgent 的实际 API 适配

| 决策 | 预期 | 实际 | 调整 |
|------|------|------|------|
| 工具注册 | `.tool(AgentTool)` | `.toolkit(Toolkit)` | 使用 ToolRegistry 收集后构建 Toolkit |
| Hook 实现 | 同步回调 | 响应式 `Mono<HookEvent>` | Hook 中返回 `Mono.just(event)` |
| 配置注入 | `.compactionConfig()` setter | 包级私有字段 | 使用默认值，不强制配置 |
| 模型注入 | `DashScopeChatModel` 在 `dashscope` 包 | 在 `core.model` 包 | 修正 import |
| Msg 内容 | `response.getTextContent()` | ✅ 可用 | 无调整 |

### 5.2 未使用 Middleware 的原因

HarnessAgent 2.0.0-RC3 中 `Hook` 已标记 deprecated，推荐使用 `MiddlewareBase`。但 Phase 1 仍使用 Hook 的原因：

1. **最小改动原则**：Hook API 仍然可用，不影响功能
2. **Phase 1 的 Hook 逻辑简单**：QualityCheckHook 和 MetricsCollectHook 目前仅做日志记录
3. **迁移时机**：待 Phase 2 的完整质量门禁实现时一并迁移到 Middleware

### 5.3 Agent 循环的边界控制

为了防止无限循环和过高延迟，设置了多层保护：

| 控制层 | 默认值 | 最大值 | 超限行为 |
|--------|:------:|:------:|---------|
| HarnessAgent maxIters | 5 | 10 | 强制终止 |
| 总超时 | 30s | 60s | 返回降级答案 |
| 单工具超时 | 15s | 30s | 跳过该工具 |
| Context 重检 | 3 次 | 5 次 | 用现有材料生成 |
| totalStepsLimit | 20 | 30 | 直接终止 |

---

## 6. 文档体系

### 6.1 文档清单

| 文档 | 类型 | 说明 |
|------|------|------|
| `需求规格说明书` | 需求 | Agentic RAG 的功能和非功能需求 |
| `需求澄清与技术选型报告` | 分析 | 技术方案对比和选型决策 |
| `开发规格说明书` | 设计 | 架构、模块、接口、配置的设计规格 |
| `详细设计说明书` | 设计 | 类图、时序图、接口定义、集成方案 |
| `开发任务分解` | 管理 | 三阶段任务分解、依赖、验收标准 |
| `本报告` | 总结 | Phase 1 实施总结和思路说明 |

### 6.2 文档之间的关系

```
需求规格说明书（做什么）
      │
      ▼
需求澄清与技术选型报告（选什么框架）
      │
      ├──→ 开发规格说明书（怎么设计）
      │         │
      │         ├──→ 详细设计说明书（怎么实现）
      │         │
      │         └──→ 开发任务分解（怎么分工）
      │
      └──→ 本报告（做得怎么样）
```

---

## 7. 当前局限与后续规划

### 7.1 已知局限

1. **Hook API 已过时**：当前使用 `Hook` 接口，HarnessAgent 推荐迁移到 `MiddlewareBase`
2. **Compaction/Memory 配置受限**：builder 未暴露 compactionConfig/memoryConfig 的 setter
3. **轨迹仅日志未入库**：`TrajectoryRecorder` 目前仅打印日志，尚未写入 `agent_trajectories` 表
4. **Quality 组件为桩实现**：`SufficientContextAgent` 使用简单的字符长度判断，非 LLM 评估
5. **工具注册层冗余**：自定义 `AgentTool` 接口需要额外适配层，后期可考虑直接使用 AgentScope 原生 `AgentTool`

### 7.2 后续实施建议

**Phase 1 收尾（~1.5 人日）**：
- T1.11：完成 `agent_trajectories` 表创建和 TrajectoryRepository JPA 实现
- T1.12：ExternalSearchTool 包装（P1，可选）

**Phase 2 — 质量保障（~8.5 人日）**：
- SufficientContextAgent LLM 完备性检查
- SelfReflection 自反思（引用/覆盖/矛盾检测）
- CorrectiveRepair 纠错（补充检索/修复/降级）
- Prometheus 指标 + 日志增强
- Hook → Middleware 迁移

**Phase 3 — 增强优化（~10.5 人日）**：
- LLM Judge 运行时质量评估
- SqlQueryTool + MemoryQueryTool
- 查询分解 + Step-Back + 多级缓存
- 子 Agent 编排 + 双层记忆集成

### 7.3 经验总结

1. **框架 API 验证优先**：在详细设计之前应做 API 可行性验证（Phase 0 PoC），避免设计文档与实际 API 的偏差
2. **增量式集成**：最小侵入原则（新增包而非修改现有代码）降低了风险，使 Phase 1 可以独立编译和测试
3. **HarnessAgent 的价值**：工作区驱动和自动记忆管理大幅减少了工程代码量，但 API 的稳定性仍需关注

---

## 附录 A：文件变动清单

| 操作 | 文件 | 行数 |
|:----:|------|:----:|
| 新增 | `core/.../rag/agentic/` — 21 个 Java 文件 | ~1,800 |
| 新增 | `model/AgenticAskRequest.java` + `AgenticAskResponse.java` | ~80 |
| 新增 | `workspace/knowledge-agent/AGENTS.md` | ~20 |
| 修改 | `api/.../KnowledgeQAController.java` — 新增 2 个端点 | +50 |
| 修改 | `core/.../KnowledgeQAService.java` — 新增 Agentic 路由 | +60 |
| 修改 | `starter/.../application-dev.yml` — 新增 agentic-rag 配置段 | +80 |
| 修改 | `pom.xml` + `core/pom.xml` — 新增 agentscope-harness 依赖 | +10 |
| 修改 | `.gitignore` — 新增 workspace runtime 排除规则 | +10 |
| **总计** | **34 个文件** | **~7,000 行** |

## 附录 B：编译验证

```bash
$ mvn compile
[INFO] knowledge .......................................... SUCCESS
[INFO] model .............................................. SUCCESS
[INFO] core ............................................... SUCCESS
[INFO] api ................................................ SUCCESS
[INFO] starter ............................................ SUCCESS
[INFO] BUILD SUCCESS
```
