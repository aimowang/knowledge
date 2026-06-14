# Agentic RAG 项目 — 需求澄清与技术选型报告

**文档版本**: v1.0  
**编写日期**: 2026-06-14  
**项目名称**: RAG 智能知识库问答系统  
**文档类型**: 需求澄清 + 技术选型

---

## 修订记录

| 版本 | 日期 | 修订内容 |
|------|------|---------|
| v1.0 | 2026-06-14 | 初稿：需求澄清 + 框架对比 + 技术选型 |

---

## 目录

1. [项目现状回顾](#1-项目现状回顾)
2. [关键决策：需要澄清的问题](#2-关键决策需要澄清的问题)
3. [Java Agent 框架全景对比](#3-java-agent-框架全景对比)
4. [框架选型推荐](#4-框架选型推荐)
5. [基于 Spring AI 的 Agentic RAG 架构](#5-基于-spring-ai-的-agentic-rag-架构)
6. [需求细化与决策记录](#6-需求细化与决策记录)
7. [需求确认清单](#7-需求确认清单)
8. [附录：关键实现细节](#8-附录关键实现细节)

---

## 1. 项目现状回顾

### 1.1 当前系统摘要

| 维度 | 现状 |
|------|------|
| 框架 | Spring Boot 3.5 + Spring AI 1.1.7 |
| AI 模型 | DashScope Qwen-Max (LLM) + text-embedding-v4 (Embedding) |
| 向量库 | Milvus 2.3 |
| 存储 | MySQL 8.0 + Redis 7 |
| 检索 | HybirdContentRetriever（向量 + BM25 混合，RRF 融合） |
| 流程 | 固定管道（Pipeline），11 个阶段顺序执行 |
| 质量保障 | CRAG（检索评估）+ 异步 RAGAS 评估 |
| 容错 | Resilience4j（熔断/重试/超时） |
| 可观测性 | Prometheus + Grafana + Zipkin + ELK |

### 1.2 当前架构的关键局限

1. **固定管道无法适应查询多样性**：简单问题和复杂问题走同一流程，无法动态调整检索策略
2. **无 Agent 自主决策**：系统无法"思考"需要检索什么、何时检索、检索结果是否足够
3. **无多步推理**：不能根据中间结果决定下一步行动
4. **无工具抽象**：检索逻辑硬编码在管道中，扩展新的检索源需要修改管道代码
5. **无完备性检查**：生成答案前没有确认检索材料是否足以回答问题

### 1.3 需解答的关键问题

在进入详细设计前，以下决策需要明确：

| 序号 | 问题 | 影响范围 |
|------|------|---------|
| Q1 | 选用哪个 Java Agent 框架？ | 架构、技术栈、学习成本 |
| Q2 | 新架构与现有 Workflow RAG 的关系？ | 兼容性、迁移策略 |
| Q3 | Agent 模式下各模块的职责如何划分？ | 代码结构、接口设计 |
| Q4 | 哪些现有组件拆为 Tool，哪些保留为基础设施？ | 重构范围、工作量 |
| Q5 | Agent 循环控制的边界（最大轮次/超时/兜底）？ | 系统稳定性 |
| Q6 | Agent 模式是否完全替代 Workflow，还是并存？ | 演进策略 |

---

## 2. 关键决策：需要澄清的问题

### 2.1 本章说明

本章列出建设 Agentic RAG 项目前需要确定的决策项。每个问题附带分析建议，供最终确定。

---

### Q1: Workflow RAG 是否保留？

**问题**: 引入 Agentic RAG 后，现有的 AdvancedRagFlow / BasicRagFlow（Workflow 模式）是否需要保留？

**选项**:

| 选项 | 说明 | 优点 | 风险 |
|------|------|------|------|
| **A: 完全替代** | AgenticRagFlow 成为唯一 RAG 模式，删除 Workflow 代码 | 维护成本最低，架构统一 | 新系统不稳定时无降级方案，简单问题也走 Agent 循环增加延迟 |
| **B: 并行共存** | 新增 AgenticRagFlow，保留现有实现，通过 KnowledgeQAService 路由 | 可灰度切换，Agent 出问题时降级到 Workflow | 两套代码维护，测试工作量翻倍 |
| **C: 渐进替换** | 先并行 → 稳定后删除 Workflow | 平滑过渡 | 需要明确的时间节点和退出标准 |

**建议**: **B → C**。第一阶段先并行共存，通过配置或问题复杂度路由（简单问题走 Workflow，复杂问题走 Agent）。待 Agent 模式稳定后（评估准确率 ≥80%、延迟 ≤8s），再逐步下线 Workflow 代码。

---

### Q2: 如何确定"哪些查询走 Agent 模式"？

**问题**: 路由策略如何确定？

**选项**:

| 选项 | 说明 | 优点 | 缺点 |
|------|------|------|------|
| **A: LLM 分类** | 用 LLM 判断查询复杂度 → SIMPLE 走 Workflow，MODERATE/COMPLEX 走 Agent | 准确率高 | 增加一次 LLM 调用成本和延迟 |
| **B: 规则分类** | 基于长度、关键词等规则判断（与现有 ComplexityLevelEnum 复用） | 零额外成本 | 边界情况不准 |
| **C: 统一走 Agent** | 所有查询都走 Agent 模式，Agent 自行决定是否需要循环 | 架构最简 | 简单问答延迟增加，Token 消耗增加 |

**建议**: **A + B 结合**。先走规则分类（快速路径），边界不清时用 LLM 分类。SIMPLE 走 Workflow（或简化版 Agent，单步零循环），MODERATE/COMPLEX 走完整 Agent 模式。

---

### Q3: Agent 循环的边界条件？

**问题**: Agent 最大循环次数、单次循环超时、整体超时如何设定？

**建议值**:

| 参数 | 默认值 | 最大值 | 说明 |
|------|--------|--------|------|
| 主循环最大次数 | 5 | 10 | 超过后强制终止，返回当前最佳结果 |
| 单次循环超时 | 15s | 30s | 含 LLM 决策 + 工具调用 |
| 总超时 | 30s | 60s | 超过后降级到 Workflow 模式 |
| Context Agent 重检索 | 3 轮 | 5 轮 | 连续 2 轮无改善提前终止 |
| Repair 重试 | 2 次 | 3 次 | 修复后质量仍不达标则降级 |

---

### Q4: Tool 的粒度？

**问题**: 工具拆分的颗粒度如何？

**选项**:

| 选项 | 说明 | 示例 |
|------|------|------|
| **A: 粗粒度** | 每个检索源一个大工具 | `retrieve(query)` 内部自动做向量+BM25+RRF |
| **B: 细粒度** | 每个检索策略一个独立工具 | `vector_search(query)`, `bm25_search(query)`, `sql_query(query)` |
| **C: 混合粒度** | 常用策略封装为独立工具，同时提供"全检索"快捷工具 | 兼有 B 的独立工具和 A 的快捷工具 |

**建议**: **C**。向量检索和 BM25 拆为独立工具让 Agent 能精确选择，同时提供 `hybrid_search` 快捷工具减少 Agent 决策次数。

---

## 3. Java Agent 框架全景对比

### 3.1 候选框架一览

| 框架 | 版本 | 定位 | Stars | 厂商 |
|------|------|------|-------|------|
| **Spring AI** | 1.1.7 (稳定) / 2.0.0-RC1 (2026.06) | Spring 官方 AI 框架 | - | VMware / Broadcom |
| **LangChain4j** | 1.13.0 (2026.04) | 全能型 LLM 框架 | 高 | 社区 + LangChain |
| **Spring AI Alibaba** | 1.1.2.3 (2026.02) | 企业级 AI Agent 全栈 | 9.9k+ | Alibaba |
| **LangGraph4j** | 1.7.10 | 状态机工作流引擎 | - | 社区 |
| **Koog** | 2026.04 | Agent 编排层 | - | JetBrains |

### 3.2 本项目当前依赖

```xml
<!-- 已在使用 -->
spring-ai-bom:1.1.7
spring-ai-alibaba-starter-dashscope:1.1.2.3
spring-ai-starter-vector-store-milvus
spring-ai-pdf-document-reader
```

项目已深度绑定 **Spring AI 1.1.7 + Spring AI Alibaba DashScope**。

### 3.3 核心能力对比

| 能力 | Spring AI 1.1.7 | Spring AI 2.0-RC1 | LangChain4j 1.13 | Spring AI Alibaba | LangGraph4j |
|------|----------------|-------------------|-----------------|-------------------|-------------|
| **Tool/Function Calling** | ⚠️ 基础 (@Tool) | ✅ ToolCallingAdvisor | ✅ 原生 @Tool | ✅ Agent Framework | ❌ 需配合 LLM 框架 |
| **多步 Agent 循环** | ❌ 需手动实现 | ❌ 需手动实现 | ✅ Agent + Chain | ✅ Agent Skills | ✅ StateGraph |
| **状态管理** | ❌ | ❌ | ✅ Memory | ✅ Context | ✅ Checkpointer |
| **Supervisor-Worker** | ❌ | ❌ | ✅ 可组合 | ✅ 内置 | ✅ 可组合 |
| **MCP 协议** | ❌ 1.x | ✅ 原生 | ✅ 实验 | ✅ | ❌ |
| **Spring 集成** | ✅ 原生 | ✅ 原生 | ⚠️ Starter | ✅ 原生 | ❌ 独立 |
| **DashScope 支持** | ✅ | ✅ | ⚠️ 实验 | ✅ 深度集成 | ❌ |
| **Agent 可观测性** | ✅ Actuator | ✅ Actuator | ⚠️ 手动 | ✅ 内置 | ❌ |
| **生产稳定性** | ✅ 成熟 | ⚠️ RC | ⚠️ 部分实验 | ✅ 成熟 | ⚠️ Beta |
| **学习曲线** | 低 | 低 | 中 | 中 | 高 |

### 3.4 关键特性详解

#### 3.4.1 Spring AI 1.1.7（当前）— Tool Calling

Spring AI 1.1.7 通过 `@Tool` 注解支持基础的 Function Calling：

```java
@Configuration
public class AgentTools {
    @Tool("检索知识库文档")
    public List<Document> vectorSearch(String query, @Value("5") int topK) {
        return vectorStore.similaritySearch(
            SearchRequest.builder().query(query).topK(topK).build()
        );
    }
}

// 注入到 ChatClient
ChatClient chatClient = ChatClient.builder(model)
    .defaultTools("vectorSearch")
    .build();
```

**局限**: 单轮调用，不支持 Agent 自主决定"是否还需要再检索"。

#### 3.4.2 Spring AI 2.0-RC1（2026 年 6 月）— Tool Calling 重做

2.0-RC1 的变化：
- `ToolCallingAdvisor` 成为推荐的 Tool 执行方式
- `ToolSearchToolCallingAdvisor` 支持**动态工具发现**（34-64% Token 节省）
- 工具必须显式注册为 `ToolCallback` Bean
- 与现有系统 **不兼容**（移除 `internalToolExecutionEnabled`、`toolNames() API` 等）

**⚠️ 注意**: Spring AI 1.1.x 适配 Spring Boot 3.5，将于 **2026 年 6 月 EOL**。但 2.0-GA 发布日期未定。

#### 3.4.3 Spring AI Alibaba — Agent Framework

Spring AI Alibaba 1.1.2.3 内置了完整的 Agent 三层架构：

```
┌──────────────────────────────────────────┐
│         Agent Framework                  │
│  ┌──────────────────────────────────┐   │
│  │    Agent Skills (技能库)          │   │
│  │  @AgentSkill 注解标记可执行技能    │   │
│  └──────────────────────────────────┘   │
│  ┌──────────────────────────────────┐   │
│  │    Supervisor (编排器)            │   │
│  │  Router / Sequencer / Parallel   │   │
│  └──────────────────────────────────┘   │
│  ┌──────────────────────────────────┐   │
│  │    Graph Core (DAG 工作流)        │   │
│  │  状态管理 + Human-in-the-Loop    │   │
│  └──────────────────────────────────┘   │
└──────────────────────────────────────────┘
```

**关键能力**（与我们需求匹配）:
- `ReactAgent` — 内置 ReAct 推理循环（规划 → 执行 → 观察 → 循环）
- `RoutingAgent` — 根据意图路由到不同技能
- `LoopAgent` — 支持循环执行直到条件满足
- `SequentialAgent` / `ParallelAgent` — 顺序/并行执行
- A2A 协议 — Agent 间通信

```java
// Spring AI Alibaba Agent 示例
@AgentSkill("检索知识库")
public class RetrievalSkill {
    @SkillMethod("基于语义向量检索文档")
    public List<Document> search(@SkillParam("查询内容") String query,
                                  @SkillParam(value = "返回数量", defaultValue = "5") int topK) {
        return vectorStore.similaritySearch(query, topK);
    }
}

// Supervisor 编排
Supervisor supervisor = Supervisor.builder()
    .agent(new ReactAgent(model, new RetrievalSkill()))
    .agent(new ExternalSearchAgent(model, new WebSearchSkill()))
    .build();

String answer = supervisor.chat("比较 Spring Boot 和 Quarkus 的优缺点");
```

#### 3.4.4 LangChain4j — Agent + Tool 原生支持

LangChain4j 是当前 Java 生态中最成熟的 Agent 框架：

```java
// Tool 定义
public class WeatherTool implements Tool {
    @Override
    public String execute(String input) { ... }
}

// Agent 装配（自带多步推理循环）
ChatLanguageModel model = OpenAiChatModel.builder().apiKey("sk-...").build();
ToolSpecification toolSpec = ToolSpecification.builder()
    .name("vectorSearch")
    .description("检索知识库")
    .build();

// 手动实现 Agent 循环
public String agenticLoop(String query) {
    int maxSteps = 5;
    String currentQuery = query;
    for (int step = 0; step < maxSteps; step++) {
        Response<AiMessage> response = model.generate(currentQuery, tools);
        if (response.content().hasToolExecutionRequests()) {
            // 执行工具 → 继续循环
            String result = executeTools(response.content().toolExecutionRequests());
            currentQuery = query + "\n上一步结果: " + result;
        } else {
            // Agent 决定回答
            return response.content().text();
        }
    }
    return "无法完成";
}
```

**优点**: Agent 循环逻辑灵活可控，适合定制化需求  
**缺点**: 需手动实现 Supervisor、循环控制、状态管理；与 Spring 集成需要额外工作

#### 3.4.5 LangGraph4j — 状态图 + 断点恢复

```java
// LangGraph4j 状态图
StateGraph<AgentState> graph = new StateGraph<>(AgentState::new);

graph.addNode("decompose", this::decomposeQuery);
graph.addNode("retrieve", this::retrieveDocs);
graph.addNode("check", this::checkSufficiency);
graph.addNode("generate", this::generateAnswer);

graph.setEntryPoint("decompose");
graph.addConditionalEdge("check", state -> 
    state.isComplete() ? "generate" : "retrieve"
);
graph.setFinishPoint("generate");
```

**适用场景**: 需要复杂状态管理的工作流，但本项目已有的 Pipeline 模式已部分覆盖此能力。

### 3.5 综合评分

| 维度 | 权重 | Spring AI 1.x | Spring AI Alibaba | LangChain4j | LangGraph4j |
|------|------|:--------------:|:-----------------:|:-----------:|:-----------:|
| 与本项目兼容性 | 30% | **10/10** | **10/10** | 4/10 | 3/10 |
| Agent 原生能力 | 25% | 4/10 | **8/10** | **8/10** | 7/10 |
| 生产稳定性 | 20% | **9/10** | **8/10** | 6/10 | 5/10 |
| Spring 集成 | 10% | **10/10** | **10/10** | 6/10 | 4/10 |
| DashScope 支持 | 10% | 7/10 | **10/10** | 5/10 | 3/10 |
| 学习成本 | 5% | **9/10** | 7/10 | 6/10 | 5/10 |
| **加权总分** | **100%** | **7.8** | **8.7** | 5.9 | 4.5 |

---

## 4. 框架选型推荐

### 4.1 推荐方案：Spring AI Alibaba（主框架）+ 自研 Agent 循环

```mermaid
graph TB
    subgraph "推荐技术栈"
        A[Spring Boot 3.5 已有] --> B[Spring AI 1.1.7 已有]
        B --> C[Spring AI Alibaba 1.1.2.3 已有]
        C --> D[Agent Framework: ReactAgent / RoutingAgent / LoopAgent]
        C --> E[Graph Core: 状态管理 + Human-in-the-Loop]
        B --> F[现有组件包装为 @AgentSkill]
        D --> G[自研: SufficientContextAgent]
        D --> H[自研: Self-Reflection + Repair]
        F --> I[自研: ToolRegistry (基于 @AgentSkill)]
    end
```

### 4.2 推荐理由

| 维度 | 说明 |
|------|------|
| **零新增依赖** | Spring AI Alibaba 1.1.2.3 已是项目现有依赖 |
| **深度 DashScope 集成** | 通义千问模型、Embedding 均已在项目中使用 |
| **内置 Agent 三层架构** | Agent Skills + Supervisor + Graph Core 能满足我们 80% 的 Agent 需求 |
| **兼容性** | 与 Spring Boot 3.5 完全兼容，无版本冲突 |
| **迁移路径** | 当前使用的 Spring AI 1.1.7 将于 2026.06 EOL，Spring AI Alibaba 后续可升级到 2.0 适配版 |
| **社区活跃** | 9.9k+ GitHub Stars，236 贡献者，阿里云官方维护 |

### 4.3 引入 LangGraph4j 的时机

如果后续需要更复杂的状态管理（如断点恢复、审计级轨迹回放），可以考虑在 Spring AI Alibaba 的 Graph Core 之上叠加 LangGraph4j。但第一阶段不建议引入。

### 4.4 不使用 LangChain4j 的理由

| 原因 | 说明 |
|------|------|
| **框架冲突** | LangChain4j 与 Spring AI 的模型抽象层不兼容，同时引入会导致两套 ChatModel/EmbeddingModel |
| **DashScope 支持弱** | 阿里云通义模型在 LangChain4j 中为实验性支持 |
| **导入成本** | 新增依赖链长、版本兼容性需要大量测试 |
| **Spring 集成浅** | 无 Actuator、Security 等原生集成 |

---

## 5. 基于 Spring AI Alibaba 的 Agentic RAG 架构

### 5.1 架构分层

```
┌──────────────────────────────────────────────────────────┐
│                   API Layer                                │
│  KnowledgeQAController (/ask, /ask/agent, /trajectory)   │
└────────────────────────┬─────────────────────────────────┘
                         │
┌────────────────────────▼─────────────────────────────────┐
│             Routing Layer (KnowledgeQAService)             │
│  规则/LLM 分类 → 选择 Workflow Agentic Hybrid             │
└──────┬─────────────────────────────────┬─────────────────┘
       │                                 │
       ▼                                 ▼
┌──────────────┐          ┌──────────────────────────────┐
│ Workflow RAG │          │      Agentic RAG (新增)        │
│ (保留, 降级)  │          │                              │
│              │          │  ┌────────────────────────┐  │
│ BasicRagFlow │          │  │ AgentOrchestrator      │  │
│ Advanced     │          │  │ ReactAgent / LoopAgent │  │
│              │          │  └────────────────────────┘  │
└──────────────┘          │  ┌────────────────────────┐  │
                          │  │ ToolRegistry            │  │
                          │  │ @AgentSkill 工具集      │  │
                          │  └────────────────────────┘  │
                          │  ┌────────────────────────┐  │
                          │  │ Quality Pipeline       │  │
                          │  │ ContextAgent           │  │
                          │  │ Reflection + Repair    │  │
                          │  └────────────────────────┘  │
                          └──────────────────────────────┘
```

### 5.2 Agent 工具清单（基于 @AgentSkill）

| 工具名 | 实现 | 入参 | 说明 |
|--------|------|------|------|
| `vector_search` | 包装 HybirdContentRetriever | query, top_k, source | 向量 + BM25 混合检索 |
| `bm25_search` | 包装 Bm25Indexer | query, top_k | 关键词精确搜索 |
| `external_search` | 包装 ExternalSearchService | query, top_k | 互联网搜索（Tavily/Bing） |
| `memory_query` | 包装 ShortTermMemoryManager + LongTermMemoryManager | query | 用户记忆查询 |
| `sql_query` | Text-to-SQL（新增） | query, table_hint | 结构化数据查询 |
| `hybrid_search` | 同时调 vector + bm25 并合并（快捷工具） | query, top_k | 一键全检索 |

### 5.3 Agent 执行流程

```
User Query
  │
  ▼
┌──────────────────────────────────────────────────────────────────┐
│ Route: 规则/LLM 分类                                               │
│ SIMPLE ──→ Workflow RAG (快速路径，零循环)                        │
│ MODERATE/COMPLEX ──→ Agentic RAG                                 │
└────────────────────────┬─────────────────────────────────────────┘
                         │
┌────────────────────────▼─────────────────────────────────────────┐
│ AgentOrchestrator.start()                                         │
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │ ReactAgent 主循环 (Spring AI Alibaba ReactAgent)          │    │
│  │                                                          │    │
│  │  step 1: 分析问题 → 规划方案                               │    │
│  │  step 2: 调用工具 (并行/串行)                              │    │
│  │  step 3: 观察结果 → 判断是否足够                           │    │
│  │  step 4: 不足 → 返回 step 1 (最多 5 轮)                    │    │
│  │  step 5: 足够 → 生成草稿答案                               │    │
│  └──────────────────────────────────────────────────────────┘    │
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │ Quality Pipeline (质量保障流水线)                          │    │
│  │                                                          │    │
│  │  ① SufficientContextAgent — 检索材料够不够？               │    │
│  │     不够 → 缺失分析 → 生成重检索指令 → 回到主循环           │    │
│  │  ② Self-Reflection — 答案有没有引用/覆盖/矛盾？            │    │
│  │     有问题 → CorrectiveRepair → 回到生成                   │    │
│  │  ③ LLM Judge — 质量评分达标？                              │    │
│  │     不达标 → 重试 (最多 2 次) → 仍不达标 → 降级            │    │
│  └──────────────────────────────────────────────────────────┘    │
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │ 输出: 最终答案 + 引用 + trajectoryId                       │    │
│  └──────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────┘
```

### 5.4 代码结构（新增/修改）

```
core/src/main/java/org/example/core/rag/agentic/
├── AgenticRagFlow.java              # 新增: AbstractRagFlow 子类
├── orchestrator/
│   └── AgentOrchestrator.java       # 新增: Agent 主循环引擎
├── tool/
│   ├── ToolRegistry.java            # 新增: 工具注册表
│   ├── AgentTool.java               # 新增: 工具接口
│   ├── VectorSearchTool.java        # 新增: 包装 HybirdContentRetriever
│   ├── Bm25SearchTool.java          # 新增: 包装 Bm25Indexer
│   ├── ExternalSearchTool.java      # 新增: 包装 ExternalSearchService
│   ├── MemoryQueryTool.java         # 新增: 包装记忆管理器
│   └── SqlQueryTool.java            # 新增: Text-to-SQL
├── quality/
│   ├── SufficientContextAgent.java  # 新增: 上下文完备性检查
│   ├── SelfReflection.java          # 新增: 自反思
│   ├── CorrectiveRepair.java        # 新增: 纠错
│   └── LlmJudge.java                # 新增: 运行时质量评估
└── state/
    ├── AgentState.java              # 新增: Agent 状态模型
    └── TrajectoryRecorder.java      # 新增: 轨迹记录
```

### 5.5 与现有代码的关系

```java
// ── 新 AgenticRagFlow 继承现有 AbstractRagFlow ──
@Component
public class AgenticRagFlow extends AbstractRagFlow {
    private final AgentOrchestrator orchestrator;
    private final QualityPipeline qualityPipeline;

    @Override
    protected void configurePipeline(RagPipeline pipeline) {
        // Agentic 模式不使用固定 Pipeline
        // pipeline 在这里是空实现
    }

    @Override
    public RagAnswer executeRag(String question, String userId, String source) {
        // Agent 主循环取代 Pipeline 执行
        AgentState state = orchestrator.execute(question, userId);
        return buildAnswer(state);
    }
}

// ── 路由决策 ──
// KnowledgeQAService 增加路由逻辑
@Component
public class KnowledgeQAService {
    public RagAnswer ask(String question, String userId, String source) {
        ComplexityLevelEnum complexity = classifyComplexity(question);
        if (complexity == ComplexityLevelEnum.SIMPLE && !forceAgentic) {
            return basicRagFlow.executeRag(question, userId, source);  // 快速路径
        }
        return agenticRagFlow.executeRag(question, userId, source);   // Agent 路径
    }
}
```

---

## 6. 需求细化与决策记录

### 6.1 已确定的架构决策

| 决策 ID | 决策项 | 结论 | 理由 |
|---------|--------|------|------|
| ADR-001 | Agent 框架 | **Spring AI Alibaba** 1.1.2.3（现有依赖） | 零新增依赖，兼容性最佳，内置 Agent 三层架构 |
| ADR-002 | Workflow 保留策略 | **并行共存 → 渐进替代** | 风险可控，可灰度 |
| ADR-003 | 路由策略 | **规则 + LLM 分类** | 简单查询零额外成本，复杂查询准确分类 |
| ADR-004 | Tool 粒度 | **混合粒度** | Agent 可精确控制也可快捷调用 |
| ADR-005 | 循环上限 | 主循环 max 5，Context 重检索 max 3，Repair max 2 | 防止无限循环 |
| ADR-006 | Agent 循环实现 | **自研 ReactAgent 循环**（基于 Spring AI @Tool + ChatClient） | Spring AI Alibaba 的 ReactAgent 还需验证；先自研确保可控 |
| ADR-007 | 状态管理 | **自研 AgentState + 轨迹 JSON 持久化** | 避免引入额外框架，满足审计需求即可 |
| ADR-008 | 质量保障 | **ContextAgent + Reflection + Repair + Judge 四阶段** | 分层保障，各有侧重 |

### 6.2 功能需求优先级（修订版）

| 需求 ID | 名称 | P0/P1/P2 | 说明 |
|---------|------|:--------:|------|
| FR-1 | Agent 主循环引擎 | P0 | 核心 ReactAgent 循环，基于 ChatClient + @Tool |
| FR-1.1 | 查询分解 | P1 | 复杂问题拆子查询 |
| FR-1.2 | 工具路由 | P0 | 选择合适工具执行 |
| FR-1.3 | 并行执行 | P0 | 无依赖子查询并发 |
| FR-1.4 | 结果综合 | P0 | 多源结果合并 |
| FR-1.5 | 循环终止 | P0 | 完备终止 / 超时 / 超限降级 |
| FR-2 | VectorSearchTool | P0 | 包装 HybirdContentRetriever |
| FR-3 | BM25SearchTool | P0 | 包装 Bm25Indexer |
| FR-4 | ExternalSearchTool | P1 | 包装 ExternalSearchService |
| FR-5 | SqlQueryTool | P1 | Text-to-SQL |
| FR-6 | MemoryQueryTool | P1 | 包装记忆管理器 |
| FR-7 | ToolRegistry | P0 | 工具注册 + 描述管理 + LLM 提示格式化 |
| FR-8 | SufficientContextAgent | P0 | 完备性检查 + 缺失分析 + 重检索指令 |
| FR-9 | Self-Reflection | P0 | 引用/覆盖/矛盾检查 |
| FR-10 | CorrectiveRepair | P0 | 补充检索 + 修复 + 优雅降级 |
| FR-11 | LLM Judge | P1 | 运行时质量评分 |
| FR-12 | Query Transformation | P1 | Decomposition + Step-Back |
| FR-13 | 路由策略 | P0 | 规则 + LLM 分类 → Workflow 或 Agent |

### 6.3 非功能需求优先级（修订版）

| 需求 ID | 名称 | P0/P1/P2 | 目标 |
|---------|------|:--------:|------|
| NFR-1 | Agent 轨迹持久化 | P0 | 完整步骤记录 |
| NFR-2 | 简单查询延迟 | P0 | p95 ≤ 3s |
| NFR-3 | 复杂查询延迟 | P0 | p95 ≤ 8s |
| NFR-4 | 循环控制 | P0 | 硬性上限 |
| NFR-5 | Prometheus Agent 指标 | P1 | 7 个新指标 |
| NFR-6 | 多级缓存 | P1 | Embedding/检索/决策 三级 |
| NFR-7 | 安全约束 | P1 | SQL 只读 / 敏感过滤 |

### 6.4 实施路线图（修订版）

```
Phase 1 (P0, ~14 人日)         Phase 2 (P0, ~10 人日)        Phase 3 (P1+P2, ~8 人日)
┌──────────────────────┐      ┌──────────────────────┐      ┌──────────────────────┐
│ AgentOrchestrator    │      │ SufficientContext    │      │ LLM Judge            │
│ 核心循环引擎 (5d)     │      │ Agent (3d)            │      │ 运行时评估 (2d)        │
│ ToolRegistry (1d)    │      │ Self-Reflection (2d) │      │ SqlQueryTool (2d)    │
│ VectorSearchTool (1d)│──▶   │ CorrectiveRepair(2d) │──▶   │ MemoryQueryTool(1d)  │
│ BM25SearchTool (0.5d)│      │ Agent 指标 (1d)       │      │ 查询分解 +           │
│ 轨迹持久化 (2d)       │      │ 日志增强 (0.5d)       │      │ Step-Back (2d)       │
│ 路由策略 (1d)         │      │ 循环控制完善 (1d)      │      │ 多级缓存 (1d)        │
│ AgenticRagFlow框架    │      │                      │      │                      │
│ + API 端点 (2d)       │      │                      │      │                      │
│ 并行执行 (1d)         │      │                      │      │                      │
└──────────────────────┘      └──────────────────────┘      └──────────────────────┘
```

---

## 7. 需求确认清单

### 7.1 需要确认的决策项

| 序号 | 决策项 | 建议 | 请确认 |
|------|--------|------|--------|
| 1 | **框架选型** | 使用 Spring AI Alibaba 1.1.2.3（已有），不引入 LangChain4j | [ ] |
| 2 | **Workflow 保留** | 先并行共存，稳定后淘汰 | [ ] |
| 3 | **路由策略** | 规则 + LLM 分类，SIMPLE 走 Workflow | [ ] |
| 4 | **Tool 粒度** | 混合粒度（独立工具 + 快捷工具） | [ ] |
| 5 | **循环上限** | 主循环 5 次，重检索 3 次，修复 2 次 | [ ] |
| 6 | **ReactAgent 实现** | 基于 Spring AI @Tool + ChatClient 自研循环 | [ ] |
| 7 | **第一阶段范围** | P0 全部实现（~14 人日） | [ ] |
| 8 | **第二阶段范围** | P0 质量保障（~10 人日） | [ ] |

### 7.2 待讨论的技术问题

1. **Q: Spring AI Alibaba 的 ReactAgent 是否可直接复用？**
   - A: 需要验证。目前 Spring AI Alibaba 1.1.2.3 的 ReactAgent 文档较少，可能需要自研循环引擎。第一阶段先自研确保可控。

2. **Q: 轨迹数据量级评估？**
   - A: 按日均 10 万次 Agent 调用、每次轨迹 5KB 估算 → 日增 500MB，30 天约 15GB。MySQL JSON 存储 + 定时归档可承受。

3. **Q: 现有 CRAG 是否保留？**
   - A: CRAG 的 `RetrievalEvaluator` 可复用到 Self-Reflection 阶段（评估检索质量）。CRAG 的 `ComplexRAGHandler`（重查询/外部搜索）可拆为 Agent 工具。

---

## 8. 附录：关键实现细节

### 8.1 自研 Agent 循环引擎参考代码

```java
@Component
public class AgentOrchestrator {
    private final ChatClient chatClient;
    private final ToolRegistry toolRegistry;
    private final AgentConfig config;
    private final TrajectoryRecorder recorder;

    public AgentState execute(String query, String userId) {
        AgentState state = new AgentState(query, userId);

        for (int loop = 0; loop < config.getMaxLoops(); loop++) {
            state.incrementLoop();

            // 1. LLM 决策：下一步做什么
            AgentDecision decision = llmDecide(state);

            // 2. 执行决策
            switch (decision.getType()) {
                case CALL_TOOL:
                    ToolResult result = executeTool(decision.getToolCall());
                    state.addToolResult(result);
                    break;
                case GENERATE:
                    state.setDraftAnswer(generateDraft(state));
                    QualityVerdict verdict = runQualityPipeline(state);
                    if (verdict.isPassed()) {
                        state.setFinalAnswer(state.getDraftAnswer());
                        state.setQualityScores(verdict.getScores());
                        recorder.record(state);
                        return state;
                    }
                    break;
                case TERMINATE:
                    state.setFinalAnswer(buildFallbackAnswer(state));
                    recorder.record(state);
                    return state;
            }
        }

        // 超限降级
        state.setFinalAnswer(buildFallbackAnswer(state));
        recorder.record(state);
        return state;
    }

    private AgentDecision llmDecide(AgentState state) {
        String contextDescription = buildContextForLLM(state);
        List<ToolSpecification> tools = toolRegistry.getToolSpecifications();

        // 调用 LLM 决定下一步
        ChatResponse response = chatClient.prompt()
            .system("你是一个智能知识库助手。根据当前上下文，
                    决定是调用工具检索更多信息、生成最终答案、还是终止。")
            .user(contextDescription)
            .tools(tools)
            .call();

        return parseDecision(response);
    }
}
```

### 8.2 ToolRegistry 设计

```java
@Component
public class ToolRegistry {
    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public void register(AgentTool tool) {
        tools.put(tool.getName(), tool);
    }

    public List<ToolSpecification> getToolSpecifications() {
        return tools.values().stream()
            .map(this::toToolSpecification)
            .collect(Collectors.toList());
    }

    private ToolSpecification toToolSpecification(AgentTool tool) {
        return ToolSpecification.builder()
            .name(tool.getName())
            .description(tool.getDescription())
            .inputSchema(tool.getJsonSchema())  // 用 Jackson Schema 生成
            .build();
    }

    public String getToolDescriptionsForPrompt() {
        // 格式化为 LLM 友好的工具描述文本
        return tools.values().stream()
            .map(t -> String.format("### %s\n%s\n参数: %s\n",
                t.getName(), t.getDescription(), t.getJsonSchema()))
            .collect(Collectors.joining("\n"));
    }
}

// 工具接口
public interface AgentTool {
    String getName();
    String getDescription();
    Map<String, Object> getJsonSchema();  // JSON Schema
    Object execute(Map<String, Object> params);
    boolean isAvailable();  // 工具是否可用
}
```

### 8.3 AgentState 与轨迹

```java
@Data
public class AgentState {
    private String query;
    private String userId;
    private int loopCount;
    private List<SubQuery> subQueries;
    private List<ToolCallRecord> toolCalls = new ArrayList<>();
    private String synthesizedContext;
    private String draftAnswer;
    private String finalAnswer;
    private QualityScores qualityScores;
    private List<StepRecord> trajectory = new ArrayList<>();
    private long startTime = System.currentTimeMillis();

    public boolean isTimeout(long maxMs) {
        return System.currentTimeMillis() - startTime > maxMs;
    }
}

@Data
public class StepRecord {
    private int stepNumber;
    private int loopNumber;
    private String type;  // DECOMPOSE / TOOL_CALL / SYNTHESIZE / CHECK / GENERATE / REFLECT / REPAIR
    private String description;
    private Object input;
    private Object output;
    private long durationMs;
    private String toolName;  // 仅 TOOL_CALL 类型
}
```

### 8.4 路由策略实现

```java
@Component
public class QueryRouter {
    private final RagFlow basicRagFlow;
    private final RagFlow advancedRagFlow;
    private final AgenticRagFlow agenticRagFlow;
    private final RagOrchestrator orchestrator;

    /**
     * 路由逻辑:
     * SIMPLE → Workflow (AdvancedRagFlow)
     * MODERATE/COMPLEX → AgenticRagFlow
     * forceAgentic=true → 强制走 Agent
     * forceWorkflow=true → 强制走 Workflow
     */
    public RagAnswer route(AskRequest request) {
        if (request.isForceWorkflow()) {
            return advancedRagFlow.executeRag(...);
        }
        if (request.isForceAgentic()) {
            return agenticRagFlow.executeRag(...);
        }

        // 规则快速分类
        ComplexityLevelEnum complexity = classifyByRule(request.getQuestion());

        // 边界情况用 LLM 分类
        if (complexity == ComplexityLevelEnum.AMBIGUOUS) {
            complexity = llmClassify(request.getQuestion());
        }

        if (complexity == ComplexityLevelEnum.SIMPLE) {
            return advancedRagFlow.executeRag(...);
        }
        return agenticRagFlow.executeRag(...);
    }
}
```

### 8.5 现有组件 → 工具映射

| 现有类 | 包装为 | 方法映射 | 改动量 |
|--------|--------|---------|--------|
| `HybirdContentRetriever` | `VectorSearchTool` | `retrieve(query, topK)` → `execute(params)` | 轻量包装 |
| `Bm25Indexer` | `BM25SearchTool` | `search(query, topK)` → `execute(params)` | 轻量包装 |
| `ExternalSearchService` | `ExternalSearchTool` | `search(query)` → `execute(params)` | 轻量包装 |
| `ShortTermMemoryManager` | `MemoryQueryTool` | `getHistory(userId)` → `execute(params)` | 轻量包装 |
| `LongTermMemoryManager` | `MemoryQueryTool` | `getRelevantMemories(userId, query)` → 合并 | 轻量包装 |
| `CacheService` | 基础设施 | Agent 决策缓存（新增方法） | 部分修改 |
| `RagMetrics` | 基础设施 | 新增 7 个 Agent 指标 | 部分修改 |
| `ResilienceHelper` | 基础设施 | 工具调用复用 | 不修改 |

---

## 文档结束

**下一步**: 请确认 §7 中的决策项，确认后进入详细设计阶段。
