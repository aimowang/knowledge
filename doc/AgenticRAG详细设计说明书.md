# Agentic RAG 详细设计说明书

**文档版本**: v1.0  
**编写日期**: 2026-06-14  
**项目名称**: RAG 智能知识库问答系统  
**框架选型**: AgentScope-Java 2.0.0-RC1 (HarnessAgent) + Spring AI Alibaba 1.1.2.3  
**文档类型**: 详细设计说明书  
**前置文档**: 需求规格说明书、开发规格说明书、开发任务分解

---

## 修订记录

| 版本 | 日期 | 修订内容 | 修订人 |
|------|------|---------|--------|
| v1.0 | 2026-06-14 | 初稿：完整类设计、时序图、接口定义、集成方案 | - |

---

## 目录

1. [类设计](#1-类设计)
2. [时序图](#2-时序图)
3. [接口详细定义](#3-接口详细定义)
4. [数据库设计](#4-数据库设计)
5. [集成设计](#5-集成设计)
6. [异常处理与边界](#6-异常处理与边界)
7. [配置参数详解](#7-配置参数详解)
8. [现有代码变更清单](#8-现有代码变更清单)

---

## 1. 类设计

### 1.1 类关系总图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           AgenticRagFlow                                      │
│                           extends AbstractRagFlow                             │
│                                                                               │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │  AgentOrchestrator                                                    │   │
│  │  ┌────────────────────────────────────────────────────────────────┐  │   │
│  │  │  HarnessAgent (AgentScope)                                     │  │   │
│  │  │  ├─ call(msg, RuntimeContext) → 自主 ReAct 推理循环            │  │   │
│  │  │  ├─ .tool(AgentTool) → 工具注册                                │  │   │
│  │  │  ├─ .hook(AgentHook) → 生命周期 Hook                           │  │   │
│  │  │  └─ .workspace(path) → 工作区驱动                              │  │   │
│  │  └────────────────────────────────────────────────────────────────┘  │   │
│  │                                                                       │   │
│  │  ┌────────────────────────────────────────────────────────────────┐  │   │
│  │  │  Quality Pipeline (自研)                                       │  │   │
│  │  │  ├─ SufficientContextAgent.check(query, context) → Verdict     │  │   │
│  │  │  ├─ SelfReflection.reflect(...) → ReflectionReport             │  │   │
│  │  │  ├─ CorrectiveRepair.repair(...) → String                      │  │   │
│  │  │  └─ LlmJudge.evaluate(...) → QualityScores                     │  │   │
│  │  └────────────────────────────────────────────────────────────────┘  │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                               │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │  Tool Layer                                                          │   │
│  │  ├─ AgentTool (interface) ←─── ToolRegistry                         │   │
│  │  ├─ VectorSearchTool : implements AgentTool                          │   │
│  │  ├─ Bm25SearchTool : implements AgentTool                            │   │
│  │  ├─ ExternalSearchTool : implements AgentTool                        │   │
│  │  ├─ MemoryQueryTool : implements AgentTool                           │   │
│  │  └─ SqlQueryTool : implements AgentTool                              │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                               │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │  State & Trajectory                                                  │   │
│  │  ├─ AgentState → Agent 执行状态（查询/工具结果/草稿/评分/轨迹）      │   │
│  │  ├─ AgentConfig → 配置参数                                           │   │
│  │  ├─ TrajectoryRecorder → 记录 AgentState 快照到数据库               │   │
│  │  └─ StepRecord → 单步执行记录                                       │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                               │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │  Router                                                              │   │
│  │  └─ QueryRouter → 规则 + LLM 分类 → Workflow / Agentic              │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘

                     ↓ 继承                  ↓ 调用
              ┌──────────────┐       ┌──────────────────────┐
              │ AbstractRagFlow      │ 现有 Spring Bean      │
              │ (现有基类)    │       │ HybirdContentRetriever│
              ├──────────────┤       │ Bm25Indexer          │
              │ BasicRagFlow │       │ ExternalSearchService │
              │ AdvancedRagFlow      │ ShortTermMemoryMgr   │
              │ AgenticRagFlow│       │ LongTermMemoryMgr    │
              └──────────────┘       └──────────────────────┘
```

### 1.2 新增类清单

| 类名 | 包路径 | 类型 | 职责 | 涉及任务 |
|------|--------|------|------|---------|
| `AgenticRagFlow` | `rag/agentic/` | 类 | AbstractRagFlow 子类，Agentic RAG 入口 | T1.8 |
| `AgentOrchestrator` | `rag/agentic/agent/` | 类 | Agent 主循环引擎，包装 HarnessAgent | T1.7 |
| `AgentState` | `rag/agentic/agent/` | 类 | Agent 执行状态模型 | T1.6 |
| `AgentConfig` | `rag/agentic/agent/` | 类 | Agent 配置参数模型 | T1.1 |
| `AgentDecision` | `rag/agentic/agent/` | 枚举 | Agent 决策类型 (CALL_TOOL, GENERATE, TERMINATE) | T1.6 |
| `AgentStatus` | `rag/agentic/agent/` | 枚举 | Agent 状态 (RUNNING, COMPLETED, FAILED, TIMEOUT) | T1.6 |
| `AgentTool` | `rag/agentic/tool/` | 接口 | 工具接口 | T1.2 |
| `ToolRegistry` | `rag/agentic/tool/` | 类 | 工具注册表 | T1.5 |
| `ToolResult` | `rag/agentic/tool/` | 类 | 工具执行结果封装 | T1.2 |
| `VectorSearchTool` | `rag/agentic/tool/` | 类 | 向量检索工具 (P0) | T1.3 |
| `Bm25SearchTool` | `rag/agentic/tool/` | 类 | BM25 关键词检索工具 (P0) | T1.4 |
| `ExternalSearchTool` | `rag/agentic/tool/` | 类 | 外部搜索工具 (P1) | T1.12 |
| `MemoryQueryTool` | `rag/agentic/tool/` | 类 | 记忆查询工具 (P1) | T3.3 |
| `SqlQueryTool` | `rag/agentic/tool/` | 类 | Text-to-SQL 工具 (P1) | T3.2 |
| `SufficientContextAgent` | `rag/agentic/quality/` | 类 | 上下文完备性检查 (P0) | T2.1 |
| `SelfReflection` | `rag/agentic/quality/` | 类 | 自反思 (P0) | T2.2 |
| `CorrectiveRepair` | `rag/agentic/quality/` | 类 | 纠错 (P0) | T2.3 |
| `LlmJudge` | `rag/agentic/quality/` | 类 | LLM Judge 质量评估 (P1) | T3.1 |
| `ContextVerdict` | `rag/agentic/quality/` | 类 | 完备性判定结果 | T2.1 |
| `ReflectionReport` | `rag/agentic/quality/` | 类 | 自反思报告 | T2.2 |
| `QualityScores` | `rag/agentic/quality/` | 类 | 质量评分模型 | T3.1 |
| `QualityThresholds` | `rag/agentic/quality/` | 类 | 质量阈值配置 | T3.1 |
| `TrajectoryRecorder` | `rag/agentic/trajectory/` | 类 | 轨迹记录器 | T1.11 |
| `TrajectoryRepository` | `rag/agentic/trajectory/` | 接口 | JPA Repository | T1.11 |
| `TrajectoryEntity` | `rag/agentic/trajectory/` | 类 | JPA 实体 | T1.11 |
| `StepRecord` | `rag/agentic/trajectory/` | 类 | 单步记录模型 | T1.6 |
| `QueryRouter` | `rag/agentic/router/` | 类 | 查询路由 | T1.10 |
| `AgenticRagConfig` | `rag/agentic/config/` | 类 | Spring @Configuration | T1.1 |
| `QualityCheckHook` | `rag/agentic/agent/` | 类 | (内部类) 自定义 HarnessAgent Hook | T1.7 |
| `MetricsCollectHook` | `rag/agentic/agent/` | 类 | (内部类) 指标采集 Hook | T1.7 |

### 1.3 枚举与常量

```java
// ── AgentStatus ──
public enum AgentStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    TIMEOUT
}

// ── AgentDecisionType ──
public enum AgentDecisionType {
    DECOMPOSE,
    CALL_TOOL,
    SYNTHESIZE,
    GENERATE,
    TERMINATE
}

// ── StepType ── (用于轨迹记录)
public enum StepType {
    DECOMPOSE,
    TOOL_CALL,
    SYNTHESIZE,
    CONTEXT_CHECK,
    GENERATE,
    REFLECT,
    REPAIR,
    JUDGE,
    TERMINATE
}
```

### 1.4 核心接口定义

#### AgentTool

```java
package org.example.core.rag.agentic.tool;

/**
 * Agent 工具接口 — 适配 HarnessAgent 的 .tool() 注册机制。
 *
 * 实现要求：
 * 1. name 使用 snake_case，如 "vector_search"
 * 2. description 包含适用场景、限制说明、返回格式
 * 3. parametersSchema 使用 JSON Schema 格式的 Map 描述
 * 4. execute() 内部使用 ResilienceHelper 进行容错
 * 5. isAvailable() 返回工具当前可用状态
 */
public interface AgentTool {

    /** 工具名称，LLM 选择工具的依据 */
    String getName();

    /** 工具描述，LLM 理解工具用途的依据 */
    String getDescription();

    /** 
     * 参数 Schema（JSON Schema 格式）。
     * 示例: {"query": {"type": "string", "description": "查询文本", "required": true}}
     */
    Map<String, Object> getParametersSchema();

    /**
     * 执行工具。
     * @param params 参数键值对（由 LLM 根据 schema 生成）
     * @return 执行结果，含成功/失败状态
     */
    ToolResult execute(Map<String, Object> params);

    /** 工具是否可用 */
    default boolean isAvailable() { return true; }
}
```

#### ToolResult

```java
package org.example.core.rag.agentic.tool;

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

    @SuppressWarnings("unchecked")
    public <T> T getData() { return (T) data; }
}
```

#### ToolRegistry

```java
package org.example.core.rag.agentic.tool;

/**
 * 工具注册表 — 收集 AgentTool Bean 供 HarnessAgent 注册。
 *
 * 使用方式：
 *   HarnessAgent.builder()
 *       .tools(toolRegistry.getAllTools().toArray(new AgentTool[0]))
 *       .build();
 */
@Component
public class ToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    /** 注册单个工具 */
    public void register(AgentTool tool) {
        tools.put(tool.getName(), tool);
    }

    /** 批量注册 */
    public void registerAll(List<AgentTool> toolList) {
        toolList.forEach(this::register);
    }

    /** 按名称获取工具 */
    public AgentTool getTool(String name) {
        return tools.get(name);
    }

    /** 获取所有可用工具（过滤不可用） */
    public List<AgentTool> getAllTools() {
        return tools.values().stream()
            .filter(AgentTool::isAvailable)
            .collect(Collectors.toList());
    }

    /** 格式化工具描述（用于日志和调试） */
    public String getToolDescriptionsForPrompt() {
        return getAllTools().stream()
            .map(t -> String.format("### %s\n描述: %s\n参数: %s",
                t.getName(), t.getDescription(), t.getParametersSchema()))
            .collect(Collectors.joining("\n\n"));
    }
}
```

---

## 2. 时序图

### 2.1 完整请求处理流程

```
用户                     KnowledgeQA        KnowledgeQA       QueryRouter        AgenticRagFlow        AgentOrchestrator
 │                       Controller          Service                                               + HarnessAgent
 │                          │                    │                  │                     │                    │
 │ POST /api/qa/ask/agent   │                    │                  │                     │                    │
 │────────────────────────>│                    │                  │                     │                    │
 │                         │ ask()              │                  │                     │                    │
 │                         │───────────────────>│                  │                     │                    │
 │                         │                    │ route(request)   │                     │                    │
 │                         │                    │─────────────────>│                     │                    │
 │                         │                    │                  │── forceAgentic ──→ Agentic              │
 │                         │                    │                  │── SIMPLE ────────→ Workflow(降级)       │
 │                         │                    │                  │── COMPLEX ──────→ Agentic              │
 │                         │                    │<────────────────│                     │                    │
 │                         │                    │ executeRag()     │                     │                    │
 │                         │                    │──────────────────────────────────────>│                    │
 │                         │                    │                  │                     │  execute(query)    │
 │                         │                    │                  │                     │───────────────────>│
 │                         │                    │                  │                     │                    │
 │                         │                    │                  │                     │                    │
 │                         │                    │                  │                     │  ┌─────────────┐  │
 │                         │                    │                  │                     │  │ Phase 1     │  │
 │                         │                    │                  │                     │  │ HarnessAgent│  │
 │                         │                    │                  │                     │  │ call(msg,   │  │
 │                         │                    │                  │                     │  │   ctx)      │  │
 │                         │                    │                  │                     │  │             │  │
 │                         │                    │                  │                     │  │ AGENTS.md   │  │
 │                         │                    │                  │                     │  │ 自动注入     │  │
 │                         │                    │                  │                     │  │ ReAct 推理   │  │
 │                         │                    │                  │                     │  │ 工具调用     │  │
 │                         │                    │                  │                     │  │ 观察结果     │  │
 │                         │                    │                  │                     │  │ 自动记忆     │  │
 │                         │                    │                  │                     │  │ 会话持久化   │  │
 │                         │                    │                  │                     │  └─────────────┘  │
 │                         │                    │                  │                     │                    │
 │                         │                    │                  │                     │  ┌─────────────┐  │
 │                         │                    │                  │                     │  │ Phase 2     │  │
 │                         │                    │                  │                     │  │ ContextCheck│  │
 │                         │                    │                  │                     │  │ → 不足则    │  │
 │                         │                    │                  │                     │  │   补充检索   │  │
 │                         │                    │                  │                     │  └─────────────┘  │
 │                         │                    │                  │                     │                    │
 │                         │                    │                  │                     │  ┌─────────────┐  │
 │                         │                    │                  │                     │  │ Phase 2     │  │
 │                         │                    │                  │                     │  │ 生成草稿    │  │
 │                         │                    │                  │                     │  │ → Self-     │  │
 │                         │                    │                  │                     │  │   Reflection│  │
 │                         │                    │                  │                     │  │ → Repair    │  │
 │                         │                    │                  │                     │  └─────────────┘  │
 │                         │                    │                  │                     │                    │
 │                         │                    │                  │                     │  ┌─────────────┐  │
 │                         │                    │                  │                     │  │ Phase 3     │  │
 │                         │                    │                  │                     │  │ LLM Judge   │  │
 │                         │                    │                  │                     │  │ → 评分/降级  │  │
 │                         │                    │                  │                     │  └─────────────┘  │
 │                         │                    │                  │                     │                    │
 │                         │                    │                  │                     │<───────────────────│
 │                         │                    │ convertToAnswer  │                     │                    │
 │                         │                    │<──────────────────────────────────────│                    │
 │                         │<───────────────────│                  │                     │                    │
 │<────────────────────────│                    │                  │                     │                    │
 │                         │                    │                  │                     │                    │
```

### 2.2 Agent 主循环内部时序

```
AgentOrchestrator        HarnessAgent          AGENTS.md          RuntimeContext         Tool (vector/bm25)
     │                       │                    │                    │                       │
     │  execute(query,uid)   │                    │                    │                       │
     │──────────────────────>│                    │                    │                       │
     │                       │                    │                    │                       │
     │   1. 构建 RuntimeContext                  │                    │                       │
     │<──────────────────────────────────────────│────────────────────│                       │
     │                       │                    │                    │                       │
     │   2. HarnessAgent.call(msg, ctx)          │                    │                       │
     │──────────────────────>│                    │                    │                       │
     │                       │                    │                    │                       │
     │   3. [Hook] WorkspaceContextHook(900)     │                    │                       │
     │                       │──────────────────>│                    │                       │
     │                       │  读取 AGENTS.md   │                    │                       │
     │                       │<──────────────────│                    │                       │
     │                       │  注入 System Prompt                    │                       │
     │                       │                    │                    │                       │
     │   4. [Hook] SessionPersistenceHook(900)   │                    │                       │
     │                       │───────────────────────────────────────>│                       │
     │                       │  加载历史 session   │                    │                       │
     │                       │<───────────────────────────────────────│                       │
     │                       │                    │                    │                       │
     │   5. ReAct 推理循环    │                    │                    │                       │
     │    (a) LLM: 分析查询   │                    │                    │                       │
     │        "需要检索 Spring Boot 微服务特点"    │                    │                       │
     │                       │                    │                    │                       │
     │    (b) LLM: 决定调工具 │                    │                    │                       │
     │        vector_search("Spring Boot 微服务") │                    │                       │
     │                       │─────────────────────────────────────────────────────────────>│
     │                       │                    │                    │                       │
     │    (c) 工具执行        │                    │                    │                       │
     │                       │<─────────────────────────────────────────────────────────────│
     │                       │  返回 5 篇文档     │                    │                       │
     │                       │                    │                    │                       │
     │    (d) LLM: 观察结果   │                    │                    │                       │
     │        "信息足够"      │                    │                    │                       │
     │                       │                    │                    │                       │
     │    (e) LLM: 生成回答   │                    │                    │                       │
     │        "Spring Boot 的微服务特点包括..."   │                    │                       │
     │                       │                    │                    │                       │
     │   6. 返回 Msg 响应     │                    │                    │                       │
     │<──────────────────────│                    │                    │                       │
     │                       │                    │                    │                       │
     │   7. [Hook] SessionPersistenceHook         │                    │                       │
     │                       │───────────────────────────────────────>│                       │
     │                       │  持久化会话到 sessions/ JSONL          │                       │
     │                       │<───────────────────────────────────────│                       │
     │                       │                    │                    │                       │
     │   8. [Hook] MetricsHook (自定义)           │                    │                       │
     │       记录耗时、Token 消耗                  │                    │                       │
```

### 2.3 SufficientContextAgent 检查流程

```
AgentOrchestrator     SufficientContextAgent       ChatClient           HarnessAgent
     │                       │                       │                     │
     │  check(query, context)│                       │                     │
     │──────────────────────>│                       │                     │
     │                       │                       │                     │
     │   Prompt: "判断上下文是否足以回答问题"         │                     │
     │                       │──────────────────────>│                     │
     │                       │                       │                     │
     │   评估结果: 不完备      │                       │                     │
     │                       │<──────────────────────│                     │
     │                       │                       │                     │
     │   ContextVerdict:      │                       │                     │
     │   sufficient=false     │                       │                     │
     │   missingInfo="过敏史"  │                       │                     │
     │   suggestedQuery="过敏"│                       │                     │
     │<──────────────────────│                       │                     │
     │                       │                       │                     │
     │  重检索: "补充检索：过敏"                       │                     │
     │─────────────────────────────────────────────────────────────>│
     │                       │                       │                     │
     │  check(query, context')│                       │                     │
     │──────────────────────>│                       │                     │
     │                       │──────────────────────>│                     │
     │   评估结果: 完备        │                       │                     │
     │<──────────────────────│                       │                     │
     │                       │                       │                     │
     │  确认完备，继续生成答案   │                       │                     │
```

### 2.4 Self-Reflection + Corrective Repair 流程

```
AgentOrchestrator     SelfReflection         ChatClient       CorrectiveRepair    ToolRegistry
     │                     │                    │                  │                │
     │  reflect(query,     │                    │                  │                │
     │   draft, context)   │                    │                  │                │
     │────────────────────>│                    │                  │                │
     │                     │                    │                  │                │
     │  ① 引用检查 Prompt  │                    │                  │                │
     │                     │───────────────────>│                  │                │
     │   "检查无引用声明"    │                    │                  │                │
     │                     │<───────────────────│                  │                │
     │                     │                    │                  │                │
     │  ② 子查询覆盖检查    │                    │                  │                │
     │   "未覆盖：Quarkus"  │                    │                  │                │
     │                     │                    │                  │                │
     │  ③ 矛盾检测          │                    │                  │                │
     │   "无矛盾"           │                    │                  │                │
     │                     │                    │                  │                │
     │  ReflectionReport    │                    │                  │                │
     │  hasIssues=true      │                    │                  │                │
     │<────────────────────│                    │                  │                │
     │                     │                    │                  │                │
     │  有 issues, 启动修复 │                    │                  │                │
     │────────────────────────────────────────────────────────────>│                │
     │                     │                    │                  │                │
     │  repair(query,draft, │                    │                  │                │
     │   report, context)  │                    │                  │                │
     │                     │                    │                  │                │
     │  ① 提取缺失关键词    │                    │                  │                │
     │                     │                    │                  │                │
     │  ② 调用 vector_search                    │                  │                │
     │                     │                    │                  │─────────────>│
     │   "Quarkus 微服务"   │                    │                  │                │
     │                     │                    │                  │<─────────────│
     │                     │                    │                  │                │
     │  ③ 重新生成答案      │                    │                  │                │
     │                     │───────────────────>│                  │                │
     │                     │<───────────────────│                  │                │
     │                     │                    │                  │                │
     │  修复后答案返回       │                    │                  │                │
     │<────────────────────────────────────────────────────────────│                │
     │                     │                    │                  │                │
     │  再次 reflect 确认   │                    │                  │                │
     │   hasIssues=false   │                    │                  │                │
     │                     │                    │                  │                │
     │  确认修复成功         │                    │                  │                │
```

---

## 3. 接口详细定义

### 3.1 REST API

#### POST /api/qa/ask/agent

Agentic RAG 问答端点。

**请求**:
```json
POST /api/qa/ask/agent
Content-Type: application/json
Authorization: Bearer <token>

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

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| userId | string | 是 | - | 用户标识 |
| question | string | 是 | - | 用户查询 |
| config.maxLoops | int | 否 | 5 | Agent 最大循环次数 |
| config.timeoutMs | int | 否 | 30000 | 总超时 (ms) |
| config.enableExternalSearch | bool | 否 | false | 是否启用外部搜索 |
| config.forceAgentic | bool | 否 | false | 强制 Agent 模式 |

**响应 200**:
```json
{
  "answer": "Spring Boot 和 Quarkus 都是优秀的微服务框架...\n\n**Spring Boot 优点**: ... [1]\n**Quarkus 优点**: ... [2]\n\n**推荐结论**: ...",
  "sources": [
    {
      "content": "Spring Boot 在微服务领域...",
      "metadata": { "source": "spring-microservices.md", "tool": "vector_search" }
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

| 字段 | 类型 | 必含 | 说明 |
|------|------|:----:|------|
| answer | string | 是 | 最终答案（Markdown，含 [N] 引用） |
| sources | array | 是 | 引用来源 |
| trajectoryId | string | 否 | 轨迹 ID（Agent 模式返回） |
| loopCount | int | 否 | Agent 循环轮数 |
| qualityScores | object | 否 | LLM Judge 启用时返回 |
| totalDurationMs | long | 是 | 总耗时 |
| agenticMode | bool | 是 | 是否为 Agent 模式 |

**错误响应**:

| HTTP 状态 | 错误场景 | 响应体 |
|:---------:|---------|--------|
| 400 | 参数不合法（question 为空） | `{"error": "question must not be blank"}` |
| 401 | Token 无效 / 未认证 | `{"error": "Authentication failed"}` |
| 408 | Agent 执行超时 | `{"answer": "处理超时，当前已找到的部分信息：...", "trajectoryId": "...", "agenticMode": true}` |
| 429 | 频率限制 | `{"error": "Too many requests"}` |
| 500 | 系统内部错误 | `{"error": "Internal server error"}` |

#### GET /api/qa/trajectory/{trajectoryId}

查询 Agent 执行轨迹。

**响应 200**:
```json
{
  "trajectoryId": "traj_20260614_abc123",
  "userId": "user123",
  "query": "比较 Spring Boot 和 Quarkus",
  "steps": [
    {
      "step": 1,
      "loop": 1,
      "type": "DECOMPOSE",
      "description": "分解为3个子查询",
      "durationMs": 1200
    },
    {
      "step": 2,
      "loop": 1,
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

**错误响应**:

| HTTP 状态 | 场景 | 响应体 |
|:---------:|------|--------|
| 404 | 轨迹不存在 | `{"error": "Trajectory not found"}` |

### 3.2 Java 内部接口

#### AgentOrchestrator

```java
@Component
public class AgentOrchestrator {

    /**
     * 执行 Agent 主循环（使用默认配置）。
     *
     * @param query  用户原始查询
     * @param userId 用户标识
     * @return Agent 执行状态（含最终答案）
     */
    public AgentState execute(String query, String userId);

    /**
     * 执行 Agent 主循环（使用自定义配置）。
     *
     * @param query  用户原始查询
     * @param userId 用户标识
     * @param config 自定义 Agent 配置（覆盖默认值）
     * @return Agent 执行状态
     */
    public AgentState execute(String query, String userId, AgentConfig config);
}
```

#### Quality Pipeline 组件

```java
// ── SufficientContextAgent ──
public class SufficientContextAgent {
    public ContextVerdict check(String query, String context);
}

// ── SelfReflection ──
public class SelfReflection {
    public ReflectionReport reflect(String query, List<SubQuery> subQueries,
                                     String draftAnswer, String context);
}

// ── CorrectiveRepair ──
public class CorrectiveRepair {
    public String repair(String query, String draftAnswer,
                          ReflectionReport report, String context);
}

// ── LlmJudge ──
public class LlmJudge {
    public QualityScores evaluate(String query, String answer, String context);
}
```

#### QueryRouter

```java
@Component
public class QueryRouter {
    /**
     * 路由决策：根据查询复杂度选择 Workflow 或 Agentic 模式。
     *
     * 路由策略：
     * 1. forceAgentic → Agentic（强制）
     * 2. forceWorkflow → Workflow（强制）
     * 3. 规则分类 → SIMPLE → Workflow
     * 4. LLM 分类 → MODERATE/COMPLEX → Agentic
     *
     * @return RagAnswer（由路由确定的模式执行后返回）
     */
    public RagAnswer route(AskRequest request);
}
```

---

## 4. 数据库设计

### 4.1 agent_trajectories 表

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

### 4.2 rag_evaluations 表扩展

```sql
ALTER TABLE rag_evaluations
    ADD COLUMN trajectory_id VARCHAR(36) NULL COMMENT '关联的Agent轨迹ID',
    ADD INDEX idx_trajectory_id (trajectory_id);
```

### 4.3 轨迹 JSON 结构

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
      "llm_response": "1. Spring Boot 微服务特点\n2. Quarkus 微服务特点\n3. 技术栈匹配度分析"
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
    },
    {
      "step": 3,
      "loop": 1,
      "type": "TOOL_CALL",
      "description": "调用 vector_search 检索 Quarkus 微服务",
      "duration_ms": 210,
      "tool_name": "vector_search",
      "tool_input": {"query": "Quarkus 微服务特点", "top_k": 5},
      "tool_output": {"document_count": 3, "summary": "找到3篇相关文档"}
    },
    {
      "step": 4,
      "loop": 1,
      "type": "SYNTHESIZE",
      "description": "综合多源结果",
      "duration_ms": 150
    },
    {
      "step": 5,
      "loop": 1,
      "type": "GENERATE",
      "description": "生成答案草稿",
      "duration_ms": 2000
    },
    {
      "step": 6,
      "loop": 1,
      "type": "REFLECT",
      "description": "自反思检查",
      "duration_ms": 1500,
      "check_results": {
        "uncited_claims": [],
        "uncovered_subqueries": [],
        "contradictions": []
      }
    }
  ],
  "total_duration_ms": 5290,
  "final_answer": "Spring Boot 和 Quarkus 都是优秀的微服务框架...",
  "quality_scores": {"faithfulness": 0.92, "answer_relevancy": 0.88},
  "status": "COMPLETED"
}
```

### 4.4 HarnessAgent 工作区文件结构

```
workspace/knowledge-agent/
├── AGENTS.md                             ← 人格定义（版本控制）
├── MEMORY.md                             ← 精炼长期记忆（自动维护）
├── knowledge/                            ← 领域知识（可选）
│   └── system-principles.md
├── skills/                               ← 技能目录（可选，自动注册为工具）
├── subagents/                            ← 子 Agent 声明（可选）
│   ├── data-analyst.json
│   └── web-researcher.json
├── tools.json                            ← MCP 配置（可选）
└── agents/                               ← 会话目录（自动创建）
    └── <sessionId>/
        ├── context/                      ← 状态快照
        ├── sessions/                     ← 对话历史 JSONL
        │   └── 2026-06-14.jsonl
        └── memory/                       ← 每日记忆流水账
            └── 2026-06-14.md
```

---

## 5. 集成设计

### 5.1 与现有 AbstractRagFlow 的集成

```
现有继承体系:
                     AbstractRagFlow (模板方法)
                    /          |           \
                   /           |            \
          BasicRagFlow   AdvancedRagFlow   AgenticRagFlow ⬅ 新增
          (7 stage)       (11 stage)        (无 Pipeline)
```

```java
// AgenticRagFlow 实现要点
@Component
@ConditionalOnProperty(name = "agentic-rag.enabled", havingValue = "true", matchIfMissing = false)
public class AgenticRagFlow extends AbstractRagFlow {

    private final AgentOrchestrator orchestrator;

    @Override
    protected void configurePipeline(RagPipeline pipeline) {
        // Agentic 模式：Pipeline 为空
    }

    @Override
    public RagAnswer executeRag(String question, String userId, String source) {
        // 不调用 super.executeRag() 的模板流程
        // 直接使用 AgentOrchestrator 执行
        AgentState state = orchestrator.execute(question, userId);
        return convertToRagAnswer(state);
    }

    private RagAnswer convertToRagAnswer(AgentState state) {
        RagAnswer answer = new RagAnswer();
        answer.setAnswer(state.getFinalAnswer());
        answer.setSources(state.getSources());
        // 注入 Agentic 元数据
        answer.getMetadata().put("trajectoryId", state.getTrajectoryId());
        answer.getMetadata().put("loopCount", String.valueOf(state.getLoopCount()));
        answer.getMetadata().put("agenticMode", "true");
        return answer;
    }
}
```

### 5.2 与 KnowledgeQAService 的集成

```java
// KnowledgeQAService 新增 Agentic 路由
@Component
public class KnowledgeQAService {

    private final List<RagFlow> ragFlows;          // 注入所有 RagFlow 实现
    private final QueryRouter queryRouter;

    // 原有方法保持不变（Workflow 模式）
    public RagAnswer askInFlowWithSources(String userId, String question, String source) {
        RagFlow ragFlow = selectRagFlow(question);
        return ragFlow.executeRag(question, userId, source);
    }

    // 新增 Agentic 路由方法
    public RagAnswer askWithAgentic(AskRequest request) {
        return queryRouter.route(request);
    }
}
```

### 5.3 HarnessAgent Spring 配置

```java
@Configuration
public class AgenticRagConfig {

    @Bean
    public HarnessAgent harnessAgent(ToolRegistry toolRegistry, AgentConfig config) {
        return HarnessAgent.builder()
            .name("KnowledgeAssistant")
            .model(DashScopeChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("qwen-max")
                .build())
            .workspace(config.getWorkspace().getPath())
            .tools(toolRegistry.getAllTools().toArray(new AgentTool[0]))
            .hook(new QualityCheckHook())
            .hook(new MetricsCollectHook())
            .build();
    }

    @Bean
    public ToolRegistry toolRegistry(List<AgentTool> toolList) {
        ToolRegistry registry = new ToolRegistry();
        registry.registerAll(toolList);
        return registry;
    }
}
```

### 5.4 与现有容错机制的集成

```java
// Tool 实现中复用 ResilienceHelper
@Component
public class VectorSearchTool implements AgentTool {

    private final HybirdContentRetriever retriever;
    private final ResilienceHelper resilienceHelper;

    @Override
    public ToolResult execute(Map<String, Object> params) {
        try {
            String query = (String) params.get("query");
            int topK = params.containsKey("top_k")
                ? ((Number) params.get("top_k")).intValue() : 5;

            // 复用现有的 ResilienceHelper 容错机制
            List<Document> docs = resilienceHelper.executeWithRetry(
                "vectorSearch",
                () -> retriever.retrieve(query, topK, 0.0, null),
                2  // 重试次数
            );
            return ToolResult.success(docs);
        } catch (Exception e) {
            return ToolResult.failure("向量检索失败: " + e.getMessage());
        }
    }
}
```

### 5.5 与现有指标系统的集成

```java
// 在 MetricsCollectHook 中记录 Prometheus 指标
public class MetricsCollectHook implements AgentHook {

    private final RagMetrics ragMetrics;  // 现有指标服务

    @Override
    public void onAfterCall(RuntimeContext ctx, Msg response) {
        // 记录 Agent 决策次数
        ragMetrics.incrementAgentDecision();
        // 记录工具调用次数（从 ctx 中获取）
        List<String> toolsCalled = ctx.getMetadata("toolsCalled");
        if (toolsCalled != null) {
            toolsCalled.forEach(tool -> ragMetrics.incrementToolCall(tool));
        }
        // 记录循环轮次
        Integer loopCount = ctx.getMetadata("loopCount");
        if (loopCount != null) {
            ragMetrics.recordLoopCount(loopCount);
        }
    }

    @Override
    public int getPriority() { return 100; }
}
```

---

## 6. 异常处理与边界

### 6.1 异常分类

| 异常类型 | 来源 | 处理策略 | 用户可见 |
|---------|------|---------|---------|
| `HarnessAgentException` | AgentScope 内部错误 | 捕获 → 记录轨迹 → 降级返回 | 降级消息 |
| `ToolExecutionException` | 工具调用失败 | 重试（ResilienceHelper）→ 标记失败 | ToolResult.failure |
| `TimeoutException` | 超时控制 | 熔断 → 返回当前结果 | 降级消息 |
| `LlmParseException` | LLM 响应解析失败 | 重试（AgentScope 自修正）→ 兜底 | 无 |
| `IllegalArgumentException` | 参数校验 | 返回 400 | 错误消息 |

### 6.2 循环控制矩阵

```
┌──────────────────┬──────────┬──────────┬──────────────┐
│      层级        │ 默认值   │ 最大值   │ 超时后的行为  │
├──────────────────┼──────────┼──────────┼──────────────┤
│ Agent 主循环      │ 5 次     │ 10 次    │ 返回当前最佳  │
│ Context 重检     │ 3 次     │ 5 次     │ 用现有材料生成 │
│ Repair 重试      │ 2 次     │ 3 次     │ 声明不确定性   │
│ LLM Judge 重试   │ 2 次     │ 3 次     │ 返回原答案     │
│ 单工具调用超时    │ 15s      │ 30s      │ 跳过该工具     │
│ 总超时           │ 30s      │ 60s      │ 降级到 Workflow│
│ 总步骤数上限      │ 20       │ 30       │ 强制终止       │
└──────────────────┴──────────┴──────────┴──────────────┘
```

### 6.3 降级策略

```java
// AgentOrchestrator 中的降级逻辑
switch (failureType) {
    case TIMEOUT:
        // 已找到部分信息 → 返回局部答案
        state.setFinalAnswer(buildFallbackAnswer(state,
            "处理超时，请简化问题后重试"));
        state.setStatus(AgentStatus.TIMEOUT);
        break;

    case MAX_LOOPS_EXCEEDED:
        // 达到循环上限 → 返回当前最佳结果
        state.setFinalAnswer(state.getDraftAnswer() != null
            ? state.getDraftAnswer()
            : buildFallbackAnswer(state, "需要更多信息才能完整回答"));
        state.setStatus(AgentStatus.COMPLETED);
        break;

    case CRITICAL_ERROR:
        // 严重错误 → 降级到 Workflow RAG
        RagAnswer fallback = advancedRagFlow.executeRag(query, userId, null);
        state.setFinalAnswer(fallback.getAnswer());
        state.setStatus(AgentStatus.FAILED);
        state.setError("Agent 模式异常，已降级使用标准模式");
        break;
}
```

### 6.4 边界条件处理

| 边界条件 | 处理方式 |
|---------|---------|
| 空查询 | 返回 400 错误 |
| 超长查询（>2000 字符） | 截断 + 日志警告 |
| 检索结果为空 | Step-Back 查询 → 仍为空 → 声明"知识库暂无相关信息" |
| 所有工具不可用 | Agent 声明"当前没有可用的检索工具" |
| LLM 返回格式错误 | AgentScope 自修正 Parser 自动重试（最多 3 次） |
| HarnessAgent 异常 | 捕获 → 记录轨迹 → 降级消息 |
| 数据库写入失败 | 轨迹记录失败不影响答案返回（仅日志告警） |
| 并发请求 | RuntimeContext 天然隔离，无共享状态 |

---

## 7. 配置参数详解

### 7.1 完整 YAML 配置

```yaml
# ============================================================
# Agentic RAG 配置 — 完整参数说明
# ============================================================
agentic-rag:
  # ── 全局开关 ──
  enabled: true              # false = 完全禁用 Agent 模式，退化为 Workflow RAG

  # ── Agent 引擎配置 ──
  agent:
    name: "KnowledgeAssistant"        # Agent 名称（出现在 LLM 上下文中）
    model: "qwen-max"                 # DashScope 模型名
    max-loops: 5                      # 主循环最大次数 [1, 10]
    max-context-retries: 3            # Context Agent 最大重检次数 [0, 5]
    max-repair-retries: 2             # 修复最大重试次数 [0, 3]
    total-steps-limit: 20             # 总步骤数硬性上限 [5, 30]
    max-timeout-ms: 30000             # 总超时(ms) [5000, 60000]
    single-tool-timeout-ms: 15000     # 单工具调用超时(ms) [3000, 30000]
    enable-streaming: true            # 是否启用流式输出（SSE）

  # ── 工具配置 ──
  tool:
    vector-search:
      top-k: 5
      similarity-threshold: 0.0
    bm25-search:
      top-k: 5
    external-search:
      top-k: 3
      enabled: false                  # P1 默认关闭
    sql-query:
      max-rows: 1000
      query-timeout-ms: 5000
      enabled: false                  # P1 默认关闭
    memory-query:
      top-k: 5
      enabled: false                  # P1 默认关闭

  # ── 工作区配置 ──
  workspace:
    path: "./workspace/knowledge-agent"
    auto-init: true                   # 启动时自动创建目录结构
    filesystem:
      backend: local                  # local | sandbox | remote
      local:
        base-path: "./workspace"
    memory:
      enabled: true
      consolidation-interval: 3600    # 精炼间隔（秒），默认 1 小时
      max-daily-logs: 30
      importance-threshold: 0.6

  # ── 质量保障配置 ──
  quality:
    context-check:
      enabled: true
      max-retries: 3
      improvement-threshold: 2        # 连续 N 轮无改善则提前终止
    self-reflection:
      enabled: true
    corrective-repair:
      enabled: true
      max-retries: 2
    llm-judge:
      enabled: false                  # P1 默认关闭
      thresholds:
        faithfulness: 0.7
        answer-relevancy: 0.6
        citation-grounding: 0.8

  # ── Hook 管道配置 ──
  hook:
    compaction:
      enabled: true
      max-history-length: 50
      max-tokens: 4096
      strategy: semantic              # truncate | semantic
      preserve-last-n: 5
    token-counter:
      enabled: true
      max-prompt-tokens: 8192
      max-response-tokens: 2048
    rate-limit:
      enabled: false
      max-calls-per-minute: 30
    safety:
      enabled: false

  # ── 沙箱配置 ──
  sandbox:
    enabled: false                    # 默认关闭，P1 按需启用
    mode: gVisor                      # NONE | gVisor | Docker
    default-tools: NONE
    sandbox-tools:
      - external_search
      - sql_query
    memory-limit: 512m
    cpu-limit: 1.0
    network-access: false

  # ── 路由配置 ──
  routing:
    strategy: rule_llm                # rule_only | llm_only | rule_llm
    force-agentic: false
    force-workflow: false
    simple-threshold: 20              # 规则分类阈值

  # ── 轨迹配置 ──
  trajectory:
    enabled: true
    retention-days: 30                # 保留天数
```

---

## 8. 现有代码变更清单

### 8.1 修改清单

| 文件 | 路径 | 修改内容 | 行数估计 |
|------|------|---------|---------|
| `KnowledgeQAController.java` | `api/.../controller/` | 新增 2 个端点 | +60 行 |
| `KnowledgeQAService.java` | `core/.../service/` | 新增 `askWithAgentic()`, 修改路由 | +40 行 |
| `RagMetrics.java` | `core/.../metrics/` | 新增 7 个 Agent 指标 | +50 行 |
| `pom.xml` | `core/` | 新增 agentscope-harness 依赖 | +10 行 |
| `application-dev.yml` | `starter/src/main/resources/` | 新增 agentic-rag 配置段 | +80 行 |

### 8.2 新增文件清单

| 文件 | 路径 | 行数估计 |
|------|------|---------|
| `AgenticRagFlow.java` | `core/.../rag/agentic/` | ~60 行 |
| `AgentOrchestrator.java` | `core/.../rag/agentic/agent/` | ~250 行 |
| `AgentState.java` | `core/.../rag/agentic/agent/` | ~120 行 |
| `AgentConfig.java` | `core/.../rag/agentic/agent/` | ~100 行 |
| `AgentDecision.java` | `core/.../rag/agentic/agent/` | ~30 行 |
| `AgentTool.java` | `core/.../rag/agentic/tool/` | ~30 行 |
| `ToolRegistry.java` | `core/.../rag/agentic/tool/` | ~60 行 |
| `ToolResult.java` | `core/.../rag/agentic/tool/` | ~40 行 |
| `VectorSearchTool.java` | `core/.../rag/agentic/tool/` | ~80 行 |
| `Bm25SearchTool.java` | `core/.../rag/agentic/tool/` | ~70 行 |
| `ExternalSearchTool.java` | `core/.../rag/agentic/tool/` | ~70 行 |
| `MemoryQueryTool.java` | `core/.../rag/agentic/tool/` | ~80 行 |
| `SqlQueryTool.java` | `core/.../rag/agentic/tool/` | ~120 行 |
| `SufficientContextAgent.java` | `core/.../rag/agentic/quality/` | ~100 行 |
| `SelfReflection.java` | `core/.../rag/agentic/quality/` | ~120 行 |
| `CorrectiveRepair.java` | `core/.../rag/agentic/quality/` | ~100 行 |
| `LlmJudge.java` | `core/.../rag/agentic/quality/` | ~80 行 |
| `ContextVerdict.java` | `core/.../rag/agentic/quality/` | ~30 行 |
| `ReflectionReport.java` | `core/.../rag/agentic/quality/` | ~50 行 |
| `QualityScores.java` | `core/.../rag/agentic/quality/` | ~50 行 |
| `TrajectoryRecorder.java` | `core/.../rag/agentic/trajectory/` | ~80 行 |
| `TrajectoryRepository.java` | `core/.../rag/agentic/trajectory/` | ~20 行 |
| `TrajectoryEntity.java` | `core/.../rag/agentic/trajectory/` | ~60 行 |
| `StepRecord.java` | `core/.../rag/agentic/trajectory/` | ~50 行 |
| `QueryRouter.java` | `core/.../rag/agentic/router/` | ~120 行 |
| `AgenticRagConfig.java` | `core/.../rag/agentic/config/` | ~60 行 |
| **合计** | **26 个新文件** | **~2,000 行** |

### 8.3 数据库迁移

```sql
-- V20260614.001__create_agent_trajectories.sql
CREATE TABLE agent_trajectories (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    query TEXT NOT NULL,
    trajectory JSON NOT NULL,
    total_steps INT DEFAULT 0,
    total_loops INT DEFAULT 0,
    total_duration_ms BIGINT DEFAULT 0,
    tools_used JSON,
    quality_scores JSON,
    status VARCHAR(20) DEFAULT 'COMPLETED',
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    archived TINYINT(1) DEFAULT 0,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V20260614.002__extend_rag_evaluations.sql
ALTER TABLE rag_evaluations
    ADD COLUMN trajectory_id VARCHAR(36) NULL,
    ADD INDEX idx_trajectory_id (trajectory_id);
```

### 8.4 构建依赖

```xml
<!-- core/pom.xml -->
<dependencies>
    <!-- 原有依赖保持不变 -->

    <!-- 新增: AgentScope Harness -->
    <dependency>
        <groupId>io.agentscope</groupId>
        <artifactId>agentscope-harness</artifactId>
        <version>2.0.0-RC1</version>
    </dependency>
</dependencies>
```

---

## 文档结束

**变更摘要**: 本设计说明书新增 26 个文件（约 2,000 行代码），修改 5 个现有文件（约 240 行），涉及 2 个数据库迁移脚本，新增 1 个 Maven 依赖。
