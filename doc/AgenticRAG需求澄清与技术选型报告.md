# Agentic RAG 项目 — 需求澄清与技术选型报告

**文档版本**: v1.1  
**编写日期**: 2026-06-14  
**项目名称**: RAG 智能知识库问答系统  
**文档类型**: 需求澄清 + 技术选型

---

## 修订记录

| 版本 | 日期 | 修订内容 |
|------|------|---------|
| v1.0 | 2026-06-14 | 初稿：需求澄清 + 框架对比 + 技术选型 |
| v1.1 | 2026-06-14 | 新增 AgentScope-Java 框架对比，更新选型推荐为 Spring AI Alibaba + AgentScope-Java 双框架方案 |

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
| **Spring AI Alibaba** | 1.1.2.3 (2026.02) | 企业级 AI Agent 全栈 | 9.9k+ | Alibaba |
| **AgentScope-Java** | 1.0.11 / 2.0.0-RC1 (2026.06) | 企业级多智能体引擎 | 14k+ | Alibaba 通义实验室 |
| **LangChain4j** | 1.13.0 (2026.04) | 全能型 LLM 框架 | 13k+ | 社区 + LangChain |
| **LangGraph4j** | 1.7.10 | 状态机工作流引擎 | - | 社区 |

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

| 能力 | Spring AI 1.1.7 | Spring AI Alibaba | AgentScope-Java | LangChain4j | LangGraph4j |
|------|:--------------:|:-----------------:|:--------------:|:-----------:|:-----------:|
| **Tool/Function Calling** | ⚠️ 基础 (@Tool) | ✅ Agent Framework | ✅ 原生 ReAct | ✅ 原生 @Tool | ❌ 需配合 |
| **多步 Agent 循环** | ❌ 需手动 | ✅ Agent Skills | ✅ 内置 ReAct | ✅ Agent + Chain | ✅ StateGraph |
| **自主推理(ReAct)** | ❌ | ⚠️ 部分 | ✅ **原生核心** | ✅ 支持 | ❌ 需配合 |
| **状态管理** | ❌ | ✅ Context | ✅ **完整(ReMe)** | ✅ Memory | ✅ Checkpointer |
| **Supervisor-Worker** | ❌ | ✅ 内置 | ✅ **SubAgent+Spawn** | ✅ 可组合 | ✅ 可组合 |
| **安全沙箱** | ❌ | ⚠️ Sandbox | ✅ **内置(gVisor/Kata)** | ❌ 无 | ❌ 无 |
| **MCP 协议** | ❌ 1.x | ✅ | ✅ | ✅ 实验 | ❌ |
| **Spring 集成** | ✅ **原生** | ✅ **原生** | ⚠️ 独立运行 | ⚠️ Starter | ❌ 独立 |
| **DashScope 支持** | ✅ | ✅ **深度集成** | ✅ 集成 | ⚠️ 实验 | ❌ |
| **多租户隔离** | ❌ | ⚠️ | ✅ **内置(v2.0)** | ❌ | ❌ |
| **Agent 可观测性** | ✅ Actuator | ✅ 内置 | ✅ Studio + OTel | ⚠️ 手动 | ❌ |
| **生产稳定性** | ✅ 成熟 | ✅ 成熟 | ⚠️ 快速演进 | ⚠️ 部分实验 | ⚠️ Beta |
| **学习曲线** | 低 | 中 | 中高 | 中 | 高 |

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

#### 3.4.6 AgentScope-Java — 自主智能体引擎

AgentScope-Java 由 **阿里巴巴通义实验室** 开发，是国内首个面向 Java 生态的企业级多智能体运行时。与 Spring AI Alibaba（同一家公司）定位互补：前者专注 **自主推理 Agent**，后者专注 **Graph 工作流编排**。

**核心架构**:

```
┌─────────────────────────────────────┐
│          Agent 层                    │
│  ReActAgent / HarnessAgent         │
├─────────────────────────────────────┤
│          编排层                      │
│  SubAgent + Spawn / MsgHub         │
├─────────────────────────────────────┤
│        核心服务层                    │
│  模型服务 / Toolkit / 记忆(ReMe)    │
├─────────────────────────────────────┤
│        基础设施层                    │
│  安全沙箱(gVisor) / 分布式 / OTel   │
└─────────────────────────────────────┘
```

**自研 Agent 循环 vs 框架对比**:

| 维度 | 自研（基于 @Tool） | AgentScope-Java |
|------|:----------------:|:---------------:|
| 自主推理 | 需手动实现 ReAct 循环 | **内置** ReAct 引擎（Mono 响应式链） |
| 流式推理 | 需额外实现 | **原生** Flux 流式 Token |
| 错误恢复 | 需手动处理 | **自修正** Parser（格式错误自动重试） |
| 状态管理 | 自研 AgentState | **完整** ReMe 记忆方案 |
| 子 Agent | 不支持 | SubAgent + spawn 动态编排 |
| 安全沙箱 | ❌ | ✅ gVisor/Kata 容器隔离 |
| 可观测性 | 自研轨迹持久化 | **内置** OpenTelemetry + Studio |
| 与现有项目集成 | 直接导入 Spring | 独立配置 / Spring 集成 |

```java
// AgentScope-Java ReActAgent 示例
ReActAgent agent = ReActAgent.builder()
    .name("KnowledgeAssistant")
    .model(DashScopeChatModel.builder()
        .apiKey(System.getenv("DASHSCOPE_API_KEY"))
        .modelName("qwen-max")
        .build())
    .tools(new VectorSearchTool(), new BM25SearchTool())
    .maxSteps(10)          // 自主决策步数上限
    .build();

// 调用 Agent（响应式，支持流式）
Msg response = agent.call(
    Msg.builder().textContent("比较 Spring Boot 和 Quarkus").build()
).block();
```

**关键优势**:
1. **原生 ReAct 引擎**：内置完整的推理→行动→观察循环，无需自研
2. **安全沙箱**：唯一内置容器级工具隔离的 Java Agent 框架
3. **SubAgent 动态编排**：Agent 可在运行时动态 spawn 子 Agent 完成子任务
4. **ReMe 记忆方案**：完整的长短期记忆 + BPE 压缩（减少 75% 存储成本）

**主要风险**:
1. 版本迭代快（从 1.0 到 2.0-RC 仅半年），API 不稳定
2. Spring 集成度不如 Spring AI Alibaba（需额外配置）
3. 文档不完备，部分能力需看源码
4. 和 Spring AI Alibaba 同属阿里，两者关系需厘清（互补而非替代）

### 3.5 综合评分（含 AgentScope-Java）

| 维度 | 权重 | Spring AI 1.x | Spring AI Alibaba | **AgentScope-Java** | LangChain4j | LangGraph4j |
|------|:----:|:------------:|:-----------------:|:------------------:|:-----------:|:-----------:|
| **与本项目兼容性** | 25% | **10/10** | **10/10** | 6/10 | 4/10 | 3/10 |
| **Agent 原生能力** | 25% | 4/10 | 7/10 | **9/10** | **8/10** | 7/10 |
| **生产稳定性** | 15% | **9/10** | **8/10** | 6/10 | 6/10 | 5/10 |
| **Spring 集成** | 10% | **10/10** | **10/10** | 6/10 | 6/10 | 4/10 |
| **DashScope 支持** | 10% | 7/10 | **10/10** | 8/10 | 5/10 | 3/10 |
| **安全/企业特性** | 10% | 5/10 | 6/10 | **9/10** | 4/10 | 5/10 |
| **学习成本** | 5% | **9/10** | 7/10 | 6/10 | 6/10 | 5/10 |
| **加权总分** | **100%** | **7.5** | **8.4** | **7.3** | 5.8 | 4.6 |

---

## 4. 框架选型推荐

### 4.1 评分解读

| 框架 | 加权总分 | 核心优势 | 核心风险 |
|------|:-------:|---------|---------|
| **Spring AI Alibaba** | **8.4** | 兼容性最佳 + Spring 原生 + DashScope 深度集成 | Agent 自主推理能力不如 AgentScope |
| **AgentScope-Java** | **7.3** | Agent 能力最强 + 安全沙箱 + 多租户 | 兼容性低 + 版本不稳定 + 文档不足 |
| Spring AI 1.x | 7.5 | 最稳定 + Spring 原生 vs 无 Agent 能力 | 无法满足 Agentic RAG 需求 |
| LangChain4j | 5.8 | 能力全面 vs 框架冲突 + 集成成本高 | 与现有 Spring AI 冲突 |
| LangGraph4j | 4.6 | 状态图强 vs 不兼容 Spring | 需配合其他框架使用 |

### 4.2 推荐方案：Spring AI Alibaba（主）+ AgentScope-Java（Agent 引擎）

```
┌────────────────────────────────────────────────────────────┐
│                  Spring Boot 3.5 应用                       │
├────────────────────────────────────────────────────────────┤
│  Spring AI Alibaba 1.1.2.3（编排 + 基础设施层）             │
│  ┌────────────────────────────────────────────────────┐   │
│  │  • @AgentSkill 工具定义（包装现有检索组件为 Agent 工具）│   │
│  │  • Graph Core 工作流编排（确定性流程的兜底）          │   │
│  │  • DashScope 通义千问 / Embedding / Rerank 深度集成  │   │
│  │  • MCP Gateway / Nacos / Spring Security/Actuator   │   │
│  └────────────────────────────────────────────────────┘   │
│                                                           │
│  AgentScope-Java（Agent 推理层）                            │
│  ┌────────────────────────────────────────────────────┐   │
│  │  • ReActAgent 引擎（内置推理→行动→观察循环）         │   │
│  │  • SubAgent + spawn 动态子任务编排                  │   │
│  │  • 安全沙箱（gVisor）隔离工具执行                     │   │
│  │  • ReMe 记忆方案 + OpenTelemetry 可观测性            │   │
│  └────────────────────────────────────────────────────┘   │
├────────────────────────────────────────────────────────────┤
│  现有基础设施（VectorSearchTool / BM25Tool / ExternalSearch）│
└────────────────────────────────────────────────────────────┘
```

### 4.3 推荐理由

**为什么选 Spring AI Alibaba 作为主体**（兼容性优先）:

| 维度 | 说明 |
|------|------|
| **零新增依赖** | 已是项目现有依赖，无版本冲突风险 |
| **Spring 原生集成** | Actuator、Security、Micrometer 开箱即用 |
| **DashScope 深度集成** | Qwen-Max / text-embedding-v4 已在生产使用 |
| **工具定义简洁** | @AgentSkill 注解与 Spring Bean 无缝集成 |
| **迁移路径清晰** | 兼容 Spring Boot 3.5，后续可升级 2.0 适配版 |

**为什么叠加 AgentScope-Java**（Agent 能力补全）:

| 维度 | 说明 |
|------|------|
| **避免自研 Agent 循环** | 自研 ReAct 循环（§8.1 参考代码约 200 行）虽可行，但 AgentScope 提供更成熟的反应式引擎（Mono 链 + 流式 + 自修正 Parser） |
| **安全沙箱** | 工具执行安全隔离是 Agentic RAG 的关键需求（防止 prompt injection 越权） |
| **SubAgent 动态编排** | 复杂任务可动态 spawn 子 Agent，比固定 Supervisor 更灵活 |
| **ReMe 记忆方案** | 内置记忆压缩（BPE 75% 缩减），比自研方案更优 |

**两者关系**:

> Spring AI Alibaba 与 AgentScope-Java 同属阿里巴巴，定位**互补**而非替代：
> - Spring AI Alibaba = **确定性编排 + 基础设施**（Graph、Security、Monitoring）
> - AgentScope-Java = **自主推理 + 安全执行**（ReAct、Sandbox、SubAgent）
> - 两者共享 DashScope 模型底座，无兼容性问题

### 4.4 不选择 LangChain4j 的理由

| 原因 | 说明 |
|------|------|
| **框架冲突** | 模型抽象层不兼容，同时引入需两套 ChatModel/EmbeddingModel |
| **DashScope 支持弱** | 通义模型为实验性支持，功能不完整 |
| **导入成本高** | 依赖链长，版本兼容性需要大量测试 |
| **Spring 集成浅** | 无 Actuator/Security 原生集成，需手动适配 |
| **安全沙箱缺失** | 无内置工具隔离机制 |

### 4.5 风险与缓解

| 风险 | 概率 | 影响 | 缓解措施 |
|------|:----:|:----:|---------|
| AgentScope-Java 版本 API 变动 | 高 | 中 | 锁定 1.0.11 稳定版，第一阶段只使用 ReActAgent 核心 API |
| Spring AI Alibaba + AgentScope 版本兼容性 | 中 | 高 | 先 PoC 验证集成可行性，再commit |
| 两套框架增加维护成本 | 中 | 中 | 明确职责边界：Spring AI Alibaba 管编排，AgentScope 管推理 |
| AgentScope 文档不足 | 中 | 低 | 核心 ReActAgent 文档已完善，边缘功能看源码 |

---

## 5. 基于 Spring AI Alibaba + AgentScope-Java 的 Agentic RAG 架构

### 5.1 架构分层

```
┌──────────────────────────────────────────────────────────┐
│                   API Layer                                │
│  KnowledgeQAController (/ask, /ask/agent, /trajectory)   │
└────────────────────────┬─────────────────────────────────┘
                         │
┌────────────────────────▼─────────────────────────────────┐
│             Routing Layer (KnowledgeQAService)             │
│  规则/LLM 分类 → 选择 Workflow / Agentic / Hybrid        │
└──────┬─────────────────────────────────┬─────────────────┘
       │                                 │
       ▼                                 ▼
┌──────────────┐          ┌──────────────────────────────┐
│ Workflow RAG │          │    Agentic RAG (新增)          │
│ (保留, 降级)  │          │                              │
│              │          │  ┌────────────────────────┐  │
│ BasicRagFlow │          │  │  AgentScope-Java        │  │
│ Advanced     │          │  │  ┌────────────────┐    │  │
│              │          │  │  │ ReActAgent     │    │  │
│              │          │  │  │ (推理→行动→观察) │    │  │
└──────────────┘          │  │  │ SubAgent+Spawn │    │  │
                          │  │  └────────────────┘    │  │
                          │  └────────────────────────┘  │
                          │  ┌────────────────────────┐  │
                          │  │ Spring AI Alibaba        │  │
                          │  │  ┌────────────────┐    │  │
                          │  │  │ @AgentSkill     │    │  │
                          │  │  │ 工具定义 + 注册  │    │  │
                          │  │  │ Graph Core 编排  │    │  │
                          │  │  │ DashScope 集成   │    │  │
                          │  │ └────────────────┘    │  │
                          │  └────────────────────────┘  │
                          │  ┌────────────────────────┐  │
                          │  │ Quality Pipeline(自研)  │  │
                          │  │ ContextAgent           │  │
                          │  │ Reflection + Repair    │  │
                          │  │ LLM Judge              │  │
                          │  └────────────────────────┘  │
                          └──────────────────────────────┘
```

### 5.2 Agent 工具清单

工具通过 **Spring AI Alibaba 的 @AgentSkill** 定义（Spring Bean 原生注入），由 **AgentScope-Java 的 ReActAgent** 调用执行。

| 工具名 | 框架 | 实现 | 入参 |
|--------|------|------|------|
| `vector_search` | @AgentSkill | 包装 HybirdContentRetriever | query, top_k, source |
| `bm25_search` | @AgentSkill | 包装 Bm25Indexer | query, top_k |
| `external_search` | @AgentSkill | 包装 ExternalSearchService | query, top_k |
| `memory_query` | @AgentSkill | 包装 ShortTermMemoryManager + LongTermMemoryManager | query |
| `sql_query` | @AgentSkill | Text-to-SQL（新增） | query, table_hint |
| `hybrid_search` | @AgentSkill | 同时调 vector + bm25 并合并（快捷工具） | query, top_k |

工具由 Spring AI Alibaba 管理注册声明周期，AgentScope 的 ReActAgent 通过 ToolRegistry 获取工具描述并调用。

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
│  │ ReactAgent 主循环 (AgentScope-Java ReActAgent)            │    │
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
| ADR-001 | 主框架 | **Spring AI Alibaba** 1.1.2.3 | Spring 原生兼容，DashScope 深度集成，现有依赖 |
| ADR-002 | Agent 引擎 | **AgentScope-Java** 1.0.11（新增依赖） | 内置 ReAct 引擎 + 安全沙箱 + SubAgent，避免自研循环 |
| ADR-003 | Workflow 保留策略 | **并行共存 → 渐进替代** | 风险可控，可灰度 |
| ADR-004 | 路由策略 | **规则 + LLM 分类** | 简单查询零额外成本，复杂查询准确分类 |
| ADR-005 | Tool 粒度 | **混合粒度** | Agent 可精确控制也可快捷调用 |
| ADR-006 | 循环上限 | 主循环 max 5，Context 重检索 max 3，Repair max 2 | 防止无限循环 |
| ADR-007 | Agent 循环实现 | **AgentScope-Java ReActAgent**（替代自研） | 成熟的 ReAct 引擎 + 流式 + 自修正 Parser |
| ADR-008 | 状态管理 | **AgentScope ReMe 记忆 + 轨迹 JSON 持久化** | ReMe 提供 BPE 压缩（75% 存储缩减） |
| ADR-009 | 质量保障 | **ContextAgent + Reflection + Repair + Judge 四阶段**（自研） | 分层保障，各有侧重 |
| ADR-010 | 安全沙箱 | **AgentScope 内置沙箱** | 工具隔离执行，防 Prompt Injection |
| ADR-011 | 工具注册 | **Spring AI Alibaba @AgentSkill** | Spring Bean 原生注入，与现有代码一致 |

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
| 1 | **主框架** | **Spring AI Alibaba** 1.1.2.3（已有依赖） | [ ] |
| 2 | **Agent 引擎** | **AgentScope-Java** 1.0.11（新增依赖） | [ ] |
| 3 | **Workflow 保留** | 先并行共存，稳定后淘汰 | [ ] |
| 4 | **路由策略** | 规则 + LLM 分类，SIMPLE 走 Workflow | [ ] |
| 5 | **Tool 粒度** | 混合粒度（独立工具 + 快捷工具） | [ ] |
| 6 | **循环上限** | 主循环 5 次，重检索 3 次，修复 2 次 | [ ] |
| 7 | **质量保障** | ContextAgent + Reflection + Repair + Judge 四阶段自研 | [ ] |
| 8 | **安全沙箱** | AgentScope 内置沙箱隔离工具执行 | [ ] |
| 9 | **第一阶段范围** | P0 全部实现（~14 人日） | [ ] |
| 10 | **第二阶段范围** | P0 质量保障（~10 人日） | [ ] |

### 7.2 待讨论的技术问题

1. **Q: AgentScope-Java 与 Spring AI Alibaba 的集成可行性？**
   - A: 两者同属阿里巴巴通义实验室，共享 DashScope 模型底座，理论上兼容。需先做 PoC 验证（预计 2 人日）确认：AgentScope 能否直接调用 Spring AI Alibaba 的 ChatClient / 是否需要额外适配层。

2. **Q: 引入 AgentScope-Java 新增的依赖链？**
   - A: 需评估 `agentscope-java` 的传递依赖是否与现有 Spring Boot 3.5 / Milvus / Resilience4j 冲突。建议 PoC 阶段使用 `mvn dependency:tree` 验证。

3. **Q: AgentScope 的 ReActAgent 是否支持工具调用和上下文记忆？**
   - A: 官方文档确认支持 ReAct 推理 + Tool Calling + MsgHub 多 Agent 通信。但具体的 Tool 注册方式、记忆注入方式需 PoC 验证。

4. **Q: 轨迹数据量级评估？**
   - A: 按日均 10 万次 Agent 调用、每次轨迹 5KB 估算 → 日增 500MB，30 天约 15GB。MySQL JSON 存储 + 定时归档可承受。

5. **Q: 现有 CRAG 是否保留？**
   - A: CRAG 的 `RetrievalEvaluator` 可复用到 Self-Reflection 阶段。`ComplexRAGHandler` 可拆为 Agent 工具。

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
