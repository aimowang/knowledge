# Agentic RAG 开发任务分解

**文档版本**: v1.0  
**编写日期**: 2026-06-14  
**项目名称**: RAG 智能知识库问答系统  
**框架选型**: AgentScope-Java 2.0.0-RC1 (HarnessAgent) + Spring AI Alibaba 1.1.2.3  
**总预估工作量**: ~32 人日（不含 PoC）

---

## 目录

1. [任务概览](#1-任务概览)
2. [Phase 0：技术验证（PoC）](#2-phase-0技术验证poc)
3. [Phase 1：核心 Agent 循环（P0）](#3-phase-1核心-agent-循环p0)
4. [Phase 2：质量保障（P0）](#4-phase-2质量保障p0)
5. [Phase 3：增强与优化（P1+P2）](#5-phase-3增强与优化p1p2)
6. [任务依赖关系](#6-任务依赖关系)
7. [命名规范与编码约定](#7-命名规范与编码约定)
8. [验收标准总表](#8-验收标准总表)
9. [风险清单](#9-风险清单)

---

## 1. 任务概览

### 1.1 阶段总览

| 阶段 | 名称 | 优先级 | 工作量 | 里程碑 |
|------|------|--------|--------|--------|
| Phase 0 | 技术验证（PoC） | 前置 | 2 人日 | HarnessAgent 工作区 + RuntimeContext + 工具注册集成验证通过 |
| Phase 1 | 核心 Agent 循环 | P0 | 14 人日 | 可运行的 Agentic RAG 原型 |
| Phase 2 | 质量保障 | P0 | 9.5 人日 | 具备完备性检查 + 自反思纠错能力 |
| Phase 3 | 增强与优化 | P1+P2 | 10.5 人日 | 完整生产级 Agentic RAG（含沙箱/子Agent/Hook/记忆） |

### 1.2 工作量分布

```
Phase 0: ██░░░░░░░░░░░░░░░░░░  2人日   (6%)
Phase 1: ██████████████░░░░░░ 14人日   (39%)
Phase 2: █████████░░░░░░░░░░░  9.5人日  (26%)
Phase 3: ██████████░░░░░░░░░░ 10.5人日  (29%)
                                ───────
                               总 36人日
```

### 1.3 甘特图

```
任务                      | 第1周 | 第2周 | 第3周 | 第4周 |
──────────────────────────┼───────┼───────┼───────┼───────┤
Phase 0: PoC 验证         | ██    |       |       |       |
Phase 1:                  |       |       |       |       |
  T1.1 依赖与配置          |  ██   |       |       |       |
  T1.2 AgentTool 接口     |   █   |       |       |       |
  T1.3 VectorSearchTool   |   █   |       |       |       |
  T1.4 BM25SearchTool     |   █   |       |       |       |
  T1.5 ToolRegistry       |    █  |       |       |       |
  T1.6 AgentState         |    █  |       |       |       |
  T1.7 AgentOrchestrator  |    ██ |       |       |       |
  T1.8 AgenticRagFlow     |     █ |       |       |       |
  T1.9 API 端点           |     █ |       |       |       |
  T1.10 路由策略          |      █|       |       |       |
  T1.11 轨迹记录          |      █|       |       |       |
  T1.12 ExternalSearch    |      █|       |       |       |
Phase 2:                  |       |       |       |       |
  T2.1 ContextAgent       |       | ██    |       |       |
  T2.2 SelfReflection     |       |  ██   |       |       |
  T2.3 CorrectiveRepair   |       |   ██  |       |       |
  T2.4 Prometheus 指标    |       |    █  |       |       |
  T2.5 日志增强           |       |    █  |       |       |
  T2.6 沙箱配置           |       |     █ |       |       |
Phase 3:                  |       |       |       |       |
  T3.1 LLM Judge          |       |       | ██    |       |
  T3.2 SqlQueryTool       |       |       |  ██   |       |
  T3.3 MemoryQueryTool    |       |       |   █   |       |
  T3.4 查询分解           |       |       |   ██  |       |
  T3.5 Step-Back 查询     |       |       |    █  |       |
  T3.6 多级缓存           |       |       |    ██ |       |
  T3.7 转换门控           |       |       |     █ |       |
  T3.8 子 Agent 编排      |       |       |     █ | █     |
```

---

## 2. Phase 0：技术验证（PoC）

### P0-1 AgentScope-Java 集成验证

| 字段 | 内容 |
|------|------|
| **任务ID** | P0-1 |
| **任务名称** | AgentScope-Java + Spring AI Alibaba 集成 PoC |
| **预估工时** | 2 人日 |
| **负责人** | 架构师 / 技术负责人 |
| **前置任务** | 无 |

**目标**:
验证 AgentScope-Java HarnessAgent（含工作区、Hook 管道、RuntimeContext）能否与 Spring AI Alibaba 顺利集成。

**验证项**:

| 验证点 | 验收标准 |
|--------|---------|
| 工作区初始化 | workspace/ 目录正确创建（AGENTS.md, MEMORY.md, skills/, subagents/） |
| AGENTS.md 自动注入 | AGENTS.md 内容作为 System Prompt 注入 call() |
| DashScopeChatModel 初始化 | 使用现有 DashScope API Key 成功创建 |
| RuntimeContext 多租户 | 不同 userId/traceId 正确传递，会话目录自动隔离 |
| 工具注册 (.tool()) | Spring Bean 通过 `.tool()` 注册为可用工具 |
| Hook 管道执行 | WorkspaceContextHook 正确注入 AGENTS.md；SessionPersistenceHook 正确持久化 |
| 传递依赖冲突检查 | `mvn dependency:tree` 无版本冲突 |
| ReAct 推理循环 | HarnessAgent 完成"工具调用→观察→回答"的完整循环 |
| 会话持久化 | 调用后 workspace/agents/<id>/sessions/ 正确生成 JSONL |
| 双层记忆初始化 | MEMORY.md 自动创建，调用后每日流水账写入 memory/YYYY-MM-DD.md |

**输出**:
1. PoC 验证报告（包含工作区、Hook、RuntimeContext 的验证结果）
2. 传递依赖树（`mvn dependency:tree`）
3. AGENTS.md 初始模板文件
4. 验证 Demo 代码（包含完整 call() 流程 + RuntimeContext）

**如果 PoC 失败**（HarnessAgent 集成存在重大兼容性问题）:
- 降级使用裸 ReActAgent（需手动管理工作区和记忆，增加 3 人日）
- 或采用自研 Agent 循环引擎（基于 ChatClient + @Tool，增加 3-5 人日）

---

## 3. Phase 1：核心 Agent 循环（P0）

### T1.1 依赖管理与基础配置

| 字段 | 内容 |
|------|------|
| **任务ID** | T1.1 |
| **任务名称** | 新增 AgentScope-Java 依赖与基础配置 |
| **预估工时** | 0.5 人日 |
| **前置任务** | P0-1 |

**交付物**:
1. `core/pom.xml` 新增 `agentscope-harness:2.0.0-RC1` 依赖
2. `application-dev.yml` 新增 `agentic-rag` 配置段（含 workspace 配置）
3. `AgenticRagConfig.java` — Spring @Configuration 配置类（含 HarnessAgent Bean）
4. `AgentConfig.java` — Agent 配置模型类（含 workspace 路径、Hook 配置）
5. AGENTS.md — HarnessAgent 工作区人格定义文件

**验收标准**:
- [ ] Maven 编译通过，无依赖冲突
- [ ] `agentic-rag` 配置段可被 Spring 加载并注入
- [ ] `AgenticRagConfig` Bean 初始化成功
- [ ] AgentScope DashScopeChatModel Bean 初始化成功（验证期使用 Mock）

---

### T1.2 AgentTool 接口和 ToolResult 模型

| 字段 | 内容 |
|------|------|
| **任务ID** | T1.2 |
| **任务名称** | 定义 AgentTool 接口和 ToolResult 模型 |
| **预估工时** | 0.5 人日 |
| **前置任务** | T1.1 |

**交付物**:
1. `AgentTool.java` — 工具接口（getName, getDescription, getParametersSchema, execute, isAvailable）
2. `ToolResult.java` — 工具执行结果封装（success, data, errorMessage, durationMs）

**验收标准**:
- [ ] 接口设计支持 AgentScope Toolkit 适配
- [ ] 参数 Schema 使用 JSON Schema 格式（Map 描述）
- [ ] 工具执行结果包含成功/失败状态、数据、错误信息和耗时
- [ ] 接口包含默认 `isAvailable()` 实现返回 true

---

### T1.3 VectorSearchTool

| 字段 | 内容 |
|------|------|
| **任务ID** | T1.3 |
| **任务名称** | 实现 VectorSearchTool（包装 HybirdContentRetriever） |
| **预估工时** | 1 人日 |
| **前置任务** | T1.2 |

**交付物**:
1. `VectorSearchTool.java` — 实现 AgentTool 接口

**工具定义**:
```json
{
  "name": "vector_search",
  "description": "基于向量相似度和 BM25 关键词混合检索知识库文档。适用于语义搜索、开放域问答、中文/英文文档检索",
  "parameters": {
    "query": {"type": "string", "required": true, "description": "检索查询文本"},
    "top_k": {"type": "integer", "required": false, "default": 5, "description": "返回文档数量"},
    "source": {"type": "string", "required": false, "description": "按来源过滤（文件名）"}
  }
}
```

**实现要点**:
- 注入 `HybirdContentRetriever`（`@Primary` 混合检索器）
- `execute()` 调用 `retriever.retrieve(query, topK, threshold, source)`
- 返回 `ToolResult.success(docs)` 或 `ToolResult.failure(error)`

**验收标准**:
- [ ] 单元测试通过：模拟 HybirdContentRetriever 返回文档，验证工具正确包装
- [ ] 参数缺省值正确处理（top_k 默认为 5，source 为 null）
- [ ] 异常处理：检索失败时返回 failure 而非抛异常

---

### T1.4 BM25SearchTool

| 字段 | 内容 |
|------|------|
| **任务ID** | T1.4 |
| **任务名称** | 实现 BM25SearchTool（包装 Bm25Indexer） |
| **预估工时** | 0.5 人日 |
| **前置任务** | T1.2 |

**交付物**:
1. `BM25SearchTool.java` — 实现 AgentTool 接口

**工具定义**:
```json
{
  "name": "bm25_search",
  "description": "基于 BM25 算法的关键词精确搜索。适用于专业术语、代码片段、配置项、型号等精确匹配场景",
  "parameters": {
    "query": {"type": "string", "required": true, "description": "关键词查询"},
    "top_k": {"type": "integer", "required": false, "default": 5, "description": "返回文档数量"}
  }
}
```

**验收标准**:
- [ ] 单元测试通过
- [ ] Bm25Indexer 的 ScoredDocument 正确转为 Document 列表
- [ ] 空查询或空结果集正确处理

---

### T1.5 ToolRegistry

| 字段 | 内容 |
|------|------|
| **任务ID** | T1.5 |
| **任务名称** | 实现 ToolRegistry 工具注册表（HarnessAgent 适配） |
| **预估工时** | 1 人日 |
| **前置任务** | T1.2, T1.3, T1.4 |

**交付物**:
1. `ToolRegistry.java` — 工具注册表（收集 AgentTool → 供给 HarnessAgent .tool() 注册）

**说明**:
HarnessAgent 通过 builder 的 `.tool()` 方法直接注册工具（区别于 ReActAgent 的 Toolkit 接口），
因此 ToolRegistry 的作用是 **统一收集 Spring 容器中的 AgentTool Bean**，批量供给 HarnessAgent 注册。

```java
@Component
public class ToolRegistry {
    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public void register(AgentTool tool) { tools.put(tool.getName(), tool); }
    public void registerAll(List<AgentTool> tools) { tools.forEach(this::register); }
    public AgentTool getTool(String name) { return tools.get(name); }
    public List<AgentTool> getAllTools() { return tools.values().stream()
        .filter(AgentTool::isAvailable).collect(Collectors.toList()); }
    public String getToolDescriptionsForPrompt() { /* 格式化工具描述文本 */ }
}
```

**验收标准**:
- [ ] 能收集所有 AgentTool Spring Bean
- [ ] HarnessAgent 通过 `.tools(toolRegistry.getAllTools().toArray(...))` 正确注册
- [ ] `getAllTools()` 过滤不可用工具
- [ ] 单元测试覆盖：注册、获取、过滤

---

### T1.6 AgentState 和 StepRecord 模型

| 字段 | 内容 |
|------|------|
| **任务ID** | T1.6 |
| **任务名称** | 实现 AgentState 状态模型和 StepRecord 步骤记录模型 |
| **预估工时** | 1 人日 |
| **前置任务** | T1.1 |

**交付物**:
1. `AgentState.java` — Agent 状态模型（输入/规划/执行/质量/输出/轨迹）
2. `StepRecord.java` — 单步记录模型
3. `AgentDecision.java` — Agent 决策结果模型
4. `QualityVerdict.java` — 质量判定模型

**注意**：
- AgentState 管理**业务状态**（查询、工具结果、答案草稿等）
- HarnessAgent 的 RuntimeContext 管理**运行时上下文**（userId/traceId/sessionId）
- 两者分工明确：AgentState 专注于 RAG 问答流程的状态管理

**验收标准**:
- [ ] AgentState 包含所有必要字段（输入、规划、执行、质量、输出、轨迹）
- [ ] `isTimeout()` 方法正确判断超时
- [ ] `mergeContext()` 正确合并补充检索结果
- [ ] 线程安全设计（各阶段无共享可变状态）
- [ ] 单元测试覆盖：状态流转、超时判断、上下文合并

---

### T1.7 AgentOrchestrator 核心引擎

| 字段 | 内容 |
|------|------|
| **任务ID** | T1.7 |
| **任务名称** | 实现 AgentOrchestrator 主循环引擎（基于 HarnessAgent） |
| **预估工时** | 4 人日 |
| **前置任务** | T1.5, T1.6 |

**交付物**:
1. `AgentOrchestrator.java` — Agent 主循环引擎（基于 HarnessAgent + 工作区 + RuntimeContext）

**核心流程**:
```
AgentOrchestrator.execute(query, userId)
  │
  ├─ 1. 初始化 AgentState
  ├─ 2. 创建 RuntimeContext (userId + traceId + sessionId)
  ├─ 3. HarnessAgent 自主推理
  │     ┌─────────────────────────────────────────┐
  │     │ HarnessAgent.call(msg, ctx).block()      │
  │     │                                         │
  │     │ 内部自动执行:                            │
  │     │  AGENTS.md → 注入 System Prompt          │
  │     │  MEMORY.md → 加载长期记忆                │
  │     │  ReAct 推理 → 工具调用 → 观察 → 循环     │
  │     │  会话 → 自动写入 sessions/ JSONL         │
  │     └─────────────────────────────────────────┘
  ├─ 4. SufficientContextAgent 检查 (Phase 2 集成)
  ├─ 5. 生成答案草稿 (调用 ChatClient)
  ├─ 6. Self-Reflection + Corrective Repair (Phase 2 集成)
  ├─ 7. LLM Judge (Phase 3 集成)
  ├─ 8. 输出最终答案
  └─ 9. 记录轨迹到 agent_trajectories 表
```

**实现要点**:
- Phase 1 实现完整的执行骨架和 **HarnessAgent** 集成
- HarnessAgent 通过 `HarnessAgent.builder().tool().hook().workspace()` 构建
- 每次 call() 传入 `RuntimeContext` 传递多租户上下文
- Phase 2/3 的模块在 Phase 1 使用**空实现 / 跳过逻辑**，通过功能开关控制
- 循环控制：maxLoops 硬性上限 + 超时熔断 + 异常容错
- 降级策略：超时/失败时返回当前已找到的局部信息
- 自定义 Hook 预留（QualityCheckHook, MetricsCollectHook）

**验收标准**:
- [ ] HarnessAgent 可正常初始化和运行（工作区 + AGENTS.md + DashScopeChatModel）
- [ ] RuntimeContext 正确传递 userId/traceId
- [ ] 基础查询（单轮工具调用即可回答）可在 3s 内完成
- [ ] 循环控制正确：达到 maxLoops 后终止并返回当前结果
- [ ] 超时熔断生效：超过 maxTimeoutMs 后返回降级答案
- [ ] 异常处理：HarnessAgent 异常时返回优雅降级消息
- [ ] 集成测试：从工具查询到返回答案的完整 HarnessAgent 流程
- [ ] AGENTS.md 作为 System Prompt 正确注入

---

### T1.8 AgenticRagFlow

| 字段 | 内容 |
|------|------|
| **任务ID** | T1.8 |
| **任务名称** | 实现 AgenticRagFlow（AbstractRagFlow 子类） |
| **预估工时** | 1 人日 |
| **前置任务** | T1.7 |

**交付物**:
1. `AgenticRagFlow.java` — 继承 AbstractRagFlow，作为 Agentic RAG 的入口

**实现要点**:
- `configurePipeline()` 保持空实现（Agentic 模式不使用 Pipeline）
- `configureOrchestrator()` 保持空实现（使用自身状态管理）
- `executeRag()` 委托给 `AgentOrchestrator.execute()`
- `convertToRagAnswer()` 将 AgentState 转为 RagAnswer
- 通过 `@ConditionalOnProperty(name = "agentic-rag.enabled")` 控制启用

**验收标准**:
- [ ] 继承 AbstractRagFlow 并正确重写抽象方法
- [ ] `executeRag()` 返回的 RagAnswer 包含答案、来源和 agentic 元数据
- [ ] `@ConditionalOnProperty` 开关生效
- [ ] 集成测试：从 Controller 到 AgentOrchestrator 的完整调用链

---

### T1.9 API 端点

| 字段 | 内容 |
|------|------|
| **任务ID** | T1.9 |
| **任务名称** | 新增 /api/qa/ask/agent 和 /api/qa/trajectory 端点 |
| **预估工时** | 1 人日 |
| **前置任务** | T1.8 |

**交付物**:
1. 修改 `KnowledgeQAController.java` — 新增端点
2. 修改 `KnowledgeQAService.java` — 新增 Agentic 路由逻辑
3. 新增请求/响应 DTO（`AgenticAskRequest`, `AgenticAskResponse`）

**API 定义**:

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/qa/ask/agent` | Agentic RAG 问答（支持自定义配置） |
| GET | `/api/qa/trajectory/{trajectoryId}` | 查询 Agent 执行轨迹 |

**验收标准**:
- [ ] `/api/qa/ask/agent` 接受请求并返回正确格式响应
- [ ] 响应中包含 `trajectoryId`、`loopCount` 等 Agentic 元数据
- [ ] `/api/qa/trajectory/{id}` 返回对应轨迹的完整 JSON
- [ ] 不存在的轨迹 ID 返回 404
- [ ] POST `/api/qa/ask` 保持完全不变（向后兼容）
- [ ] Swagger 文档正确生成

---

### T1.10 路由策略

| 字段 | 内容 |
|------|------|
| **任务ID** | T1.10 |
| **任务名称** | 实现查询路由策略（规则 + LLM 分类） |
| **预估工时** | 1 人日 |
| **前置任务** | T1.9 |

**交付物**:
1. `QueryRouter.java` — 查询路由（规则快速分类 + LLM 兜底分类）

**路由逻辑**:
```
ask() → forceAgentic? → 直接走 Agentic
      → forceWorkflow? → 直接走 Workflow
      → 规则分类 → SIMPLE? → Workflow
                 → AMBIGUOUS? → LLM 分类
                              → SIMPLE → Workflow
                              → MODERATE/COMPLEX → Agentic
```

**验收标准**:
- [ ] 规则分类正确：短查询（<20字符）无复杂关键词 → SIMPLE
- [ ] 规则分类正确：含"比较"/"对比"等关键词 → AMBIGUOUS
- [ ] LLM 分类兜底生效：AMBIGUOUS 时调用 LLM
- [ ] forceAgentic / forceWorkflow 开关优先
- [ ] 单元测试覆盖：全部路由路径
- [ ] 与现有 `ComplexityLevelEnum` 和 `queryComplexityClassifier` 复用

---

### T1.11 轨迹持久化

| 字段 | 内容 |
|------|------|
| **任务ID** | T1.11 |
| **任务名称** | 实现 Agent 执行轨迹持久化 |
| **预估工时** | 1 人日（比 ReActAgent 方案节省 0.5d，因 HarnessAgent 已自动处理会话持久化） |
| **前置任务** | T1.6 |

**与 HarnessAgent 的关系**:
- HarnessAgent **自动**通过 `SessionPersistenceHook` 将每次 call() 的对话写入 `workspace/agents/<sessionId>/sessions/` JSONL
- 本任务记录的是**业务级轨迹**（AgentState 的快照 + 质量评分），用于 API 查询和审计
- 两者互补：HarnessAgent 处理原始的会话历史，T1.11 处理结构化的业务轨迹

**交付物**:
1. `TrajectoryService.java` — 轨迹服务（读取 HarnessAgent session + 写入业务轨迹）
2. `TrajectoryRepository.java` — JPA Repository
3. `TrajectoryEntity.java` — JPA 实体
4. 数据库迁移脚本（新增 `agent_trajectories` 表 + `rag_evaluations` 扩展）

**数据库变更**:
```sql
-- 新增 agent_trajectories 表
CREATE TABLE agent_trajectories (...);  -- 参见开发规格 §9.1

-- 扩展 rag_evaluations
ALTER TABLE rag_evaluations ADD COLUMN trajectory_id VARCHAR(36) NULL;
```

**验收标准**:
- [ ] Agent 执行完成后轨迹正确持久化到 MySQL 的 agent_trajectories 表
- [ ] 轨迹 JSON 包含完整的业务步骤列表（查询、工具调用、质量评分等）
- [ ] `replay(trajectoryId)` 可正确反序列化重建 AgentState
- [ ] 轨迹查询 API 返回正确格式
- [ ] `rag_evaluations.trajectory_id` 正确关联
- [ ] HarnessAgent workspace session 文件（JSONL）作为原始数据补充
- [ ] 数据库迁移脚本可重复执行（幂等）

---

### T1.12 ExternalSearchTool

| 字段 | 内容 |
|------|------|
| **任务ID** | T1.12 |
| **任务名称** | 实现 ExternalSearchTool（包装 ExternalSearchService） |
| **预估工时** | 0.5 人日 |
| **前置任务** | T1.2 |

**交付物**:
1. `ExternalSearchTool.java` — 实现 AgentTool 接口

**工具定义**:
```json
{
  "name": "external_search",
  "description": "搜索互联网获取实时信息。适用于时效性强的查询、内部知识库未覆盖的内容、最新新闻/技术动态",
  "parameters": {
    "query": {"type": "string", "required": true, "description": "搜索查询"},
    "top_k": {"type": "integer", "required": false, "default": 3, "description": "返回结果数量"}
  }
}
```

**实现要点**:
- 调用 `ExternalSearchService.search(query)`
- 异常时返回空列表 + 错误标记（静默降级）
- 通过 `agentic-rag.tool.external-search.enabled` 配置开关

**验收标准**:
- [ ] 单元测试通过
- [ ] 异常情况返回 ToolResult.failure 而非抛异常
- [ ] 配置开关控制启用/禁用

---

## 4. Phase 2：质量保障（P0）

### T2.1 SufficientContextAgent

| 字段 | 内容 |
|------|------|
| **任务ID** | T2.1 |
| **任务名称** | 实现 SufficientContextAgent 上下文完备性检查 |
| **预估工时** | 3 人日 |
| **前置任务** | T1.7 |

**交付物**:
1. `SufficientContextAgent.java` — 上下文完备性检查
2. `ContextVerdict.java` — 完备性判定结果模型

**实现要点**:
- FR-8.1 检索片段检查：LLM 判断文档是否包含回答所需的关键信息
- FR-8.2 中间草稿检查（可选，P1 增强，本次实现基本版本）
- FR-8.3 缺失分析：输出具体缺失信息和补充检索建议
- FR-8.4 循环控制：最多 3 轮，连续 2 轮无改善提前终止

**验收标准**:
- [ ] 空文档时正确返回"不完备"
- [ ] 文档充分时正确返回"完备"
- [ ] 文档不充分时返回缺失信息描述和建议搜索词
- [ ] 循环控制正确：达到 maxRetries 后终止
- [ ] 连续 2 轮无改善时提前终止
- [ ] 与 AgentOrchestrator 集成正确
- [ ] 单元测试覆盖：完备/不完备/空文档/循环控制

---

### T2.2 SelfReflection

| 字段 | 内容 |
|------|------|
| **任务ID** | T2.2 |
| **任务名称** | 实现 SelfReflection 自反思 |
| **预估工时** | 2 人日 |
| **前置任务** | T1.7 |

**交付物**:
1. `SelfReflection.java` — 自反思模块
2. `ReflectionReport.java` — 反思报告模型

**实现要点**:
- FR-9.1 引用缺失检查：逐句检查关键主张是否有 `[N]` 引用
- FR-9.2 子查询覆盖检查：对比子查询和最终答案
- FR-9.3 矛盾检测：检测内部矛盾和答案与材料的矛盾

**验收标准**:
- [ ] 引用缺失检查：无引用的答案正确标记缺失声明
- [ ] 子查询覆盖检查：正确标记未覆盖的子问题
- [ ] 矛盾检测：能检测自相矛盾的内容
- [ ] 完美答案（有引用、全覆盖、无矛盾）正确返回 hasIssues=false
- [ ] 与 AgentOrchestrator 集成正确
- [ ] 单元测试覆盖：各种场景

---

### T2.3 CorrectiveRepair

| 字段 | 内容 |
|------|------|
| **任务ID** | T2.3 |
| **任务名称** | 实现 CorrectiveRepair 纠错 |
| **预估工时** | 2 人日 |
| **前置任务** | T2.2 |

**交付物**:
1. `CorrectiveRepair.java` — 纠错模块

**实现要点**:
- FR-10.1 有界重试：缺失引用→补充检索，未覆盖子问题→专项检索，矛盾→重评估
- FR-10.2 修复验证：修复后再次执行 SelfReflection 确认
- FR-10.3 优雅降级：修复仍失败时声明不确定性

**验收标准**:
- [ ] 缺失引用时：提取关键词，调用补充检索，重新生成
- [ ] 未覆盖子问题时：对缺失子问题发起专项检索
- [ ] 矛盾时：重新评估双方证据强度
- [ ] 修复后验证：再次执行 SelfReflection 确认问题已修复
- [ ] 最大重试：2 次后停止
- [ ] 优雅降级：修复失败时使用"当前知识库信息不足以给出确定答案"格式
- [ ] 集成测试：与 SelfReflection 配合的完整修复流程

---

### T2.4 Agent Prometheus 指标

| 字段 | 内容 |
|------|------|
| **任务ID** | T2.4 |
| **任务名称** | 新增 Agent Prometheus 指标 |
| **预估工时** | 1 人日 |
| **前置任务** | T1.7 |

**交付物**:
1. 修改 `RagMetrics.java` — 新增 7 个 Agent 指标

**新增指标**:

| 指标名 | 类型 | Label | 说明 |
|--------|------|-------|------|
| `agent_decision_total` | Counter | - | Agent 决策总次数 |
| `agent_tool_call_total` | Counter | `tool` | 各工具调用次数 |
| `agent_tool_duration_seconds` | Histogram | `tool` | 各工具调用耗时分布 |
| `agent_loop_count` | Histogram | - | Agent 循环轮次分布 |
| `agent_context_complete` | Gauge | - | 上下文完备性评分 |
| `agent_retry_total` | Counter | `reason` | 重试次数及原因 |
| `agent_reflection_issues` | Gauge | - | 自反思发现的问题数 |

**验收标准**:
- [ ] 指标在 Prometheus `/actuator/prometheus` 端点正确暴露
- [ ] agent_tool_call_total 带 tool label
- [ ] 现有指标不受影响（`rag_cache_hit_total` 等保持不变）
- [ ] 指标命名符合 Prometheus 命名规范

---

### T2.5 Agent 日志增强

| 字段 | 内容 |
|------|------|
| **任务ID** | T2.5 |
| **任务名称** | Agent 日志增强 |
| **预估工时** | 0.5 人日 |
| **前置任务** | T1.7 |

**交付物**:
1. 修改 `AgentOrchestrator.java` — 添加结构化日志
2. 日志配置更新

**日志要求**:
| 日志点 | 日志级别 | 内容 |
|--------|---------|------|
| Agent 开始执行 | INFO | 查询、用户ID、配置参数 |
| Agent 决策 | DEBUG | 决策类型、输入、输出 |
| 工具调用 | INFO | 工具名、参数、耗时、结果摘要 |
| LLM 调用 | DEBUG | Prompt 摘要、响应摘要 |
| 循环轮次 | INFO | 当前轮次、已耗时 |
| 质量检查 | INFO | 检查结果、评分 |
| Agent 完成 | INFO | 总耗时、循环次数、状态 |
| Agent 异常 | WARN/ERROR | 异常类型、错误信息 |

**验收标准**:
- [ ] 日志为 JSON 格式（与 ELK 兼容）
- [ ] 关键决策点有 INFO 日志
- [ ] DEBUG 日志包含足够细节用于问题排查
- [ ] 日志级别可通过配置切换（开发 DEBUG，生产 INFO）
- [ ] 与现有的 Logstash Logback Encoder 配置兼容

---

### T2.6 HarnessAgent 沙箱集成

| 字段 | 内容 |
|------|------|
| **任务ID** | T2.6 |
| **任务名称** | 集成 HarnessAgent 沙箱隔离 |
| **预估工时** | 1 人日 |
| **前置任务** | T1.7 |

**说明**:
HarnessAgent 内置 gVisor/Docker 沙箱能力。本任务为**可选增强**（默认关闭），按需为不可信工具（external_search, sql_query）启用沙箱隔离。

**交付物**:
1. `application-dev.yml` 新增 `agentic-rag.sandbox` 配置段
2. `AgentConfig.java` 新增 `SandboxConfig` 内部配置类
3. 按需修改 `HarnessAgent` 构建逻辑（增加 `.sandbox()` 配置）

**实现要点**:
- 默认关闭（`enabled: false`），NONE 模式
- 内部可信工具（vector_search, bm25_search）不启用沙箱
- 外部工具（external_search, sql_query）可选择启用 gVisor 沙箱
- 沙箱配置不影响 Phase 1 核心功能

**验收标准**:
- [ ] 沙箱关闭时功能完全不受影响
- [ ] 启用沙箱时 external_search 在隔离环境中执行
- [ ] 沙箱内存/CPU/网络限制配置生效
- [ ] 单元测试覆盖

---

## 5. Phase 3：增强与优化（P1+P2）

### T3.1 LLM Judge

| 字段 | 内容 |
|------|------|
| **任务ID** | T3.1 |
| **任务名称** | 实现 LLM Judge 运行时质量评估 |
| **预估工时** | 2 人日 |
| **前置任务** | T2.3 |

**交付物**:
1. `LlmJudge.java` — 运行时质量评估器
2. `QualityScores.java` — 质量评分模型

**评估指标**:
- Faithfulness（忠实度）: 0~1
- Answer Relevancy（相关性）: 0~1
- Citation Grounding（引用完整性）: 0~1

**验收标准**:
- [ ] 三个指标正确评分（0~1 范围）
- [ ] 阈值配置生效（`agentic-rag.quality.llm-judge.thresholds`）
- [ ] 低于阈值时触发重生成（最多 2 次）
- [ ] 评分记录写入 `rag_evaluations` 表，关联 `trajectory_id`
- [ ] 与现有 EvaluationManager 集成
- [ ] 单元测试覆盖

---

### T3.2 SqlQueryTool

| 字段 | 内容 |
|------|------|
| **任务ID** | T3.2 |
| **任务名称** | 实现 SqlQueryTool Text-to-SQL |
| **预估工时** | 2 人日 |
| **前置任务** | T1.2 |

**交付物**:
1. `SqlQueryTool.java` — Text-to-SQL 工具

**安全约束**:
- 只允许 SELECT 查询（语法校验，非 SELECT 直接拒绝）
- 查询超时 5s
- 行数限制 1000 行

**实现流程**:
```
Agent 调用 sql_query(query, table_hint)
  → 拼接 Schema 信息 + 用户查询 → LLM 生成 SQL
  → SQL 校验（只读 SELECT 白名单）
  → JDBC 执行查询（5s 超时，1000 行限制）
  → 结果转为结构化文本返回
```

**验收标准**:
- [ ] 自然语言查询可正确生成 SQL
- [ ] SELECT 校验正确（非 SELECT 被拒绝）
- [ ] 超时控制（5s）
- [ ] 行数限制（1000）
- [ ] 表 Schema 信息正确注入 Prompt
- [ ] 异常处理：数据库连接失败、SQL 语法错误等

---

### T3.3 MemoryQueryTool

| 字段 | 内容 |
|------|------|
| **任务ID** | T3.3 |
| **任务名称** | 实现 MemoryQueryTool（包装记忆管理器） |
| **预估工时** | 1 人日 |
| **前置任务** | T1.2 |

**交付物**:
1. `MemoryQueryTool.java` — 记忆查询工具

**工具定义**:
```json
{
  "name": "memory_query",
  "description": "查询用户的会话历史和长期记忆（偏好、事实、上下文）。适用于个性化回答和需要了解用户背景",
  "parameters": {
    "query": {"type": "string", "required": true, "description": "要查询的记忆内容"}
  }
}
```

**实现要点**:
- 调用 `ShortTermMemoryManager.getHistory(userId)` + `LongTermMemoryManager.getRelevantMemories(userId, query)`
- 合并短期和长期记忆结果
- userId 从 AgentState 中获取（工具调用时的上下文注入）

**验收标准**:
- [ ] 正确合并短期和长期记忆结果
- [ ] 结果格式化为文本返回
- [ ] 无记忆时返回空结果（非错误）
- [ ] 单元测试覆盖

---

### T3.4 查询分解

| 字段 | 内容 |
|------|------|
| **任务ID** | T3.4 |
| **任务名称** | 实现查询分解（Query Decomposition） |
| **预估工时** | 1.5 人日 |
| **前置任务** | T1.7 |

**交付物**:
1. 修改 `AgentOrchestrator.java` — 集成查询分解逻辑
2. `SubQuery.java` — 子查询模型（带依赖关系）

**实现要点**:
- FR-12.1: LLM 将复合问题拆解为原子子问题 + 依赖关系 DAG
- FR-12.3: 为每个子查询生成 2-3 个同义变体（Multi-Query）
- 无依赖的子查询可并行执行
- 依赖的子查询顺序执行

**子查询模型**:
```java
public class SubQuery {
    private String id;          // 子查询 ID
    private String query;       // 子查询文本
    private List<String> dependsOn;  // 依赖的子查询 ID 列表
    private List<String> variants;   // 同义变体
}
```

**验收标准**:
- [ ] 复合问题正确分解为原子子问题
- [ ] 依赖关系 DAG 正确识别
- [ ] 无依赖子查询可并行执行
- [ ] 简单问题不分解（避免过度处理）
- [ ] 单元测试覆盖

---

### T3.5 Step-Back 查询

| 字段 | 内容 |
|------|------|
| **任务ID** | T3.5 |
| **任务名称** | 实现 Step-Back Query 后退式查询 |
| **预估工时** | 1 人日 |
| **前置任务** | T1.7 |

**实现要点**:
- FR-12.2: 检索结果为空或得分低时，生成更宽泛的检索词
- 触发条件：检索结果 < 阈值（如 topK 结果的平均分 < 0.5）
- Step-Back 示例：`"Spring Boot 3.5 @EnableWebMvc 变更"` → `"Spring Boot 3.5 Web MVC 变更"`

**验收标准**:
- [ ] 检索结果为空时自动触发 Step-Back
- [ ] Step-Back 查询正确生成更宽泛的检索词
- [ ] Step-Back 后检索有结果时继续流程
- [ ] Step-Back 仍无结果时优雅降级
- [ ] 单元测试覆盖

---

### T3.6 多级缓存

| 字段 | 内容 |
|------|------|
| **任务ID** | T3.6 |
| **任务名称** | 实现 Agent 多级缓存 |
| **预估工时** | 1.5 人日 |
| **前置任务** | T1.7 |

**缓存层级** (NFR-3):

| 层级 | 缓存内容 | TTL | 存储 | 复用 |
|------|---------|-----|------|------|
| L1 | Embedding 缓存 | 10min | Caffeine | 新增 |
| L2 | 检索结果缓存 | 5min | Caffeine | 新增 |
| L3 | Agent 决策缓存 | 30min | Caffeine | 新增 |
| L4 | 最终答案缓存 | 1h | Redis | 复用 CacheService |

**实现要点**:
- Embedding 缓存：查询→Embedding 向量的映射（避免重复调用 Embedding API）
- 检索结果缓存：查询→检索结果列表（避免重复检索）
- Agent 决策缓存：相似查询→规划结果（避免重复的 LLM 决策调用）
- 最终答案缓存：复用现有 `CacheService` 的 Q&A 缓存

**验收标准**:
- [ ] 各层级缓存正确命中/未命中
- [ ] TTL 过期后自动失效
- [ ] 缓存命中时正确跳过对应阶段
- [ ] 与现有 CacheService 兼容
- [ ] 缓存指标（命中率）正确上报
- [ ] 单元测试覆盖

---

### T3.7 转换门控

| 字段 | 内容 |
|------|------|
| **任务ID** | T3.7 |
| **任务名称** | 实现查询转换门控 |
| **预估工时** | 0.5 人日 |
| **前置任务** | T3.4, T3.5 |

**实现要点**:
- FR-12.4: 判断是否需要深度查询转换
- 跳过条件：简单事实性问题（长度 < 20 字符、无疑问词）直接检索
- 目的：避免对简单问题进行过度处理，节省 Token 和延迟

**验收标准**:
- [ ] 简单问题正确跳过查询分解和 Step-Back
- [ ] 复杂问题正确进行查询分解
- [ ] 门控逻辑与现有 ComplexityLevelEnum 复用
- [ ] 单元测试覆盖

---

### T3.8 子 Agent 编排

| 字段 | 内容 |
|------|------|
| **任务ID** | T3.8 |
| **任务名称** | 实现子 Agent 编排（SubAgent） |
| **预估工时** | 1.5 人日 |
| **前置任务** | T1.7, T3.2, T3.3 |

**说明**:
利用 HarnessAgent 的 SubagentsHook + workspace/subagents/ 声明式配置，为复杂任务场景创建子 Agent。

**交付物**:
1. `workspace/knowledge-agent/subagents/data-analyst.json` — 数据分析子 Agent
2. `workspace/knowledge-agent/subagents/web-researcher.json` — 互联网搜索子 Agent
3. 子 Agent 工作区初始化脚本
4. 集成测试：AgentOrchestrator 中调用子 Agent

**子 Agent 定义**:
| 子 Agent | 工具 | 说明 |
|---------|------|------|
| `data_analyst` | sql_query | 数据查询与统计 |
| `web_researcher` | external_search | 互联网信息搜集 |

**验收标准**:
- [ ] SubagentsHook 启动时自动发现 subagents/ 目录并注册
- [ ] 父 Agent 可调用子 Agent 完成任务
- [ ] 子 Agent 执行结果正确返回给父 Agent
- [ ] 子 Agent 有独立的工作区和记忆隔离
- [ ] 子 Agent 超时/失败时优雅降级

---

### T3.9 Hook 管道配置

| 字段 | 内容 |
|------|------|
| **任务ID** | T3.9 |
| **任务名称** | 配置 HarnessAgent 内置 Hook 管道 |
| **预估工时** | 0.5 人日 |
| **前置任务** | T1.7 |

**说明**:
配置 HarnessAgent 的内置 Hook（CompactionHook、TokenCounterHook、SafetyHook 等），确保生产环境的稳定性和安全性。

**交付物**:
1. `application-dev.yml` 新增 `agentic-rag.hook.*` 配置段

**配置项**:
| Hook | 启用 | 关键配置 |
|------|:----:|---------|
| CompactionHook | ✅ | max-history-length: 50, max-tokens: 4096, strategy: semantic |
| TokenCounterHook | ✅ | max-prompt-tokens: 8192, max-response-tokens: 2048 |
| RateLimitHook | ❌ | 默认关闭（由 Bucket4j 限流替代） |
| SafetyHook | ❌ | 默认关闭（按需启用） |
| MetricsHook | ✅ | 指标采集（配合 T2.4 Prometheus 指标） |

**验收标准**:
- [ ] CompactionHook 在历史超过阈值时正确触发压缩
- [ ] TokenCounterHook 正确限制 Prompt/Response Token
- [ ] Hook 配置通过 yml 可动态调整
- [ ] 关闭的 Hook 不影响性能

---

### T3.10 双层记忆集成

| 字段 | 内容 |
|------|------|
| **任务ID** | T3.10 |
| **任务名称** | 集成 HarnessAgent 双层记忆与现有记忆管理器 |
| **预估工时** | 1 人日 |
| **前置任务** | T1.7 |

**说明**:
HarnessAgent 自动维护双层记忆（每日流水账 + MEMORY.md 精炼）。本任务确保其与现有的 LongTermMemoryManager（MySQL 持久化）互补共存。

**交付物**:
1. `application-dev.yml` 新增 `agentic-rag.workspace.memory` 配置段
2. 记忆集成说明文档（两种记忆方案的分工策略）

**集成策略**:
| 场景 | 负责任务 |
|------|---------|
| 会话内上下文 | HarnessAgent 双层记忆（自动注入 MEMORY.md） |
| 跨会话长期记忆 | 现有 LongTermMemoryManager（MySQL 持久化 + 向量检索） |
| 记忆提取 | MemoryExtractor（现有） | HarnessAgent Consolidator |
| 记忆查询 | MemoryQueryTool（T3.3）同时查询两种记忆源 |

**验收标准**:
- [ ] HarnessAgent 双层记忆自动维护（无需手动编码）
- [ ] 现有 LongTermMemoryManager 不受影响
- [ ] MEMORY.md 内容在 call() 时正确注入上下文
- [ ] MemoryQueryTool 能同时从两种记忆源查询结果
- [ ] 精炼间隔和重要性阈值配置生效

---

## 6. 任务依赖关系

### 6.1 依赖图

```
Phase 0
  │
  P0-1 (PoC 验证)
  │
  ▼
Phase 1
  │
  T1.1 (依赖与配置) ──────────────────────────────────────────┐
  │                                                            │
  ├─ T1.2 (AgentTool 接口) ──┐                                 │
  │   ├── T1.3 (VectorSearch) │                                │
  │   ├── T1.4 (BM25Search)   │                                │
  │   └── T1.12 (External)    │                                │
  │                            ▼                               │
  │                          T1.5 (ToolRegistry) ──┐          │
  │                                                 ▼          │
  │                                    T1.7 (AgentOrchestrator)│
  │                                                 ▲          │
  ├─ T1.6 (AgentState) ─────────────────────────────┘          │
  │                                                             │
  ├─ T1.8 (AgenticRagFlow) ←──── T1.7                          │
  ├─ T1.9 (API 端点) ←────── T1.8                              │
  ├─ T1.10 (路由策略) ←─── T1.9                                │
  └─ T1.11 (轨迹持久化) ←── T1.6                               │
                                                                │
Phase 2                        Phase 1 依赖                    │
  │                                                             │
  T2.1 (ContextAgent) ←──── T1.7 ──────────────────────────────┘
  T2.2 (SelfReflection) ←─ T1.7
  T2.3 (CorrectiveRepair) ← T2.2
  T2.4 (Prometheus 指标) ← T1.7
  T2.5 (日志增强) ←────── T1.7

Phase 3
  T3.1 (LLM Judge) ←──── T2.3
  T3.2 (SqlQueryTool) ←── T1.2
  T3.3 (MemoryQueryTool) ← T1.2
  T3.4 (查询分解) ←────── T1.7
  T3.5 (Step-Back) ←───── T1.7
  T3.6 (多级缓存) ←────── T1.7
  T3.7 (转换门控) ←────── T3.4, T3.5
  T3.8 (子 Agent 编排) ←── T1.7, T3.2, T3.3
  T3.9 (Hook 管道配置) ←── T1.7
  T3.10 (双层记忆集成) ←── T1.7
```

### 6.2 关键路径

```
Phase 0 → T1.1 → T1.2 → T1.5 → T1.7 → T1.8 → T1.9 → Phase 2 关键路径
                              ↓
                            T1.6
```

关键路径长度：P0-1(2d) + T1.1(0.5d) + T1.2(0.5d) + T1.5(1d) + T1.7(4d) + T1.8(1d) + T1.9(1d) = **10 人日**

即：从项目启动到可调用的 API 端点，最短需要 **10 人日**。

---

## 7. 命名规范与编码约定

### 7.1 包名

```
core/src/main/java/org/example/core/rag/agentic/   # 新增代码全部在此包下
```

### 7.2 类名

| 模式 | 示例 | 说明 |
|------|------|------|
| `XxxTool` | `VectorSearchTool` | 工具实现类 |
| `XxxState` | `AgentState` | 状态模型 |
| `XxxConfig` | `AgentConfig` | 配置模型 |
| `XxxRecorder` | `TrajectoryRecorder` | 记录器 |
| `XxxRegistry` | `ToolRegistry` | 注册表 |
| `XxxVerdict` / `XxxReport` | `ContextVerdict` | 判定/报告模型 |

### 7.3 工具名称（LLM 可见）

- 统一使用 `snake_case`：`vector_search`, `bm25_search`, `external_search`
- 名称应简短直观（2~3 个单词）
- Description 应包含：适用场景、限制、返回格式

### 7.4 配置前缀

```
agentic-rag.xxx.yyy
```

### 7.5 API 路径

```
POST /api/qa/ask/agent              # Agentic RAG 问答
GET  /api/qa/trajectory/{id}        # 轨迹查询
```

### 7.6 数据库命名

```
表名:     agent_trajectories
轨迹 ID:  traj_yyyyMMdd_xxx (如 traj_20260614_abc123)
```

---

## 8. 验收标准总表

### 8.1 Phase 1 验收

| # | 验收项 | 验证方式 |
|---|--------|---------|
| 1.1 | Agent 能接收查询并返回答案 | 集成测试 |
| 1.2 | 至少 2 个工具（vector_search + bm25_search）可被 Agent 调用 | 集成测试 |
| 1.3 | 简单查询（<20字符）路由到 Workflow RAG | 单元测试 |
| 1.4 | 复杂查询（含"比较"）路由到 Agentic RAG | 单元测试 |
| 1.5 | 循环上限生效：超过 maxLoops 后终止 | 集成测试 |
| 1.6 | 超时熔断：超过 maxTimeoutMs 后返回降级答案 | 集成测试 |
| 1.7 | `/api/qa/ask/agent` 端点正常响应 | API 测试 |
| 1.8 | 轨迹数据持久化到 `agent_trajectories` 表 | 集成测试 |
| 1.9 | 现有 Workflow RAG 完全不受影响 | 回归测试 |
| 1.10 | HarnessAgent 工作区 + AGENTS.md 正确驱动 Agent 行为 | 集成测试 |

### 8.2 Phase 2 验收

| # | 验收项 | 验证方式 |
|---|--------|---------|
| 2.1 | SufficientContextAgent 正确判断上下文完备性 | 单元测试 |
| 2.2 | 信息不足时 Agent 自动补充检索 | 集成测试 |
| 2.3 | SelfReflection 正确发现引用缺失/子查询未覆盖/矛盾 | 单元测试 |
| 2.4 | CorrectiveRepair 正确修复问题并验证 | 集成测试 |
| 2.5 | 修复失败时优雅降级（声明不确定性） | 集成测试 |
| 2.6 | 7 个 Prometheus 指标正确暴露 | 集成测试 |
| 2.7 | Agent 关键步骤有 JSON 格式 INFO 日志 | 日志检查 |
| 2.8 | 沙箱配置启用/关闭不影响核心功能 | 集成测试 |

### 8.3 Phase 3 验收

| # | 验收项 | 验证方式 |
|---|--------|---------|
| 3.1 | LLM Judge 正确评分 | 单元测试 |
| 3.2 | 评分低于阈值时触发重生成或降级 | 集成测试 |
| 3.3 | Text-to-SQL 正确执行 SELECT 查询 | 集成测试 |
| 3.4 | SQL 安全约束生效（非 SELECT 拒绝） | 单元测试 |
| 3.5 | MemoryQueryTool 返回记忆内容 | 单元测试 |
| 3.6 | 复合问题正确分解为子查询 | 集成测试 |
| 3.7 | 检索结果为空时触发 Step-Back | 集成测试 |
| 3.8 | 多级缓存命中/未命中正确 | 单元测试 |
| 3.9 | 简单问题跳过查询转换 | 单元测试 |
| 3.10 | 沙箱隔离生效（启用时 external_search 在沙箱中执行） | 集成测试 |
| 3.11 | 子 Agent 可被父 Agent 调用并返回结果 | 集成测试 |
| 3.12 | CompactionHook 在历史超阈值时自动压缩 | 集成测试 |
| 3.13 | HarnessAgent 双层记忆自动维护，与现有 LongTermMemoryManager 共存 | 集成测试 |
| (P2+) | 分布式文件系统适配（AbstractFilesystem RemoteFilesystem） | 未来迭代 |

---

## 9. 风险清单

### 9.1 技术风险

| 风险 | 概率 | 影响 | 缓解措施 |
|------|:----:|:----:|---------|
| AgentScope-Harness 与 Spring Boot 3.5 版本冲突 | 中 | 高 | Phase 0 PoC 先行验证；锁定 2.0.0-RC1 |
| HarnessAgent API 不稳定 | 高 | 中 | 只使用 HarnessAgent 核心 API（builder/call/RuntimeContext），不依赖边缘功能 |
| AgentScope 文档不足，部分能力需看源码 | 中 | 低 | DeepWiki 官方文档已覆盖 HarnessAgent 核心能力 |
| LLM 决策不稳定（Agent 规划质量波动） | 中 | 中 | 决策缓存 + 兜底路由（默认走 vector_search） |

### 9.2 工程风险

| 风险 | 概率 | 影响 | 缓解措施 |
|------|:----:|:----:|---------|
| Token 消耗大幅增加 | 高 | 中 | 转换门控 + 缓存 + 简单查询直接走 Workflow |
| 循环失控导致高延迟 | 低 | 高 | 硬性循环上限（5次）+ 总步骤上限（20）+ 超时熔断（30s） |
| Agent 调用错误工具 | 低 | 低 | 工具描述完善 + 结果有效性检查 |
| 与现有 Workflow RAG 并行维护 | 中 | 中 | 共享 Tool 实现，Agent 和 Pipeline 共用同一检索逻辑 |
| 工作区磁盘空间增长 | 中 | 低 | workspace/ 定期清理 + 归档策略（agent_trajectories 表保留 30 天） |
| AGENTS.md 配置漂移 | 低 | 中 | 版本控制 AGENTS.md + 配置校验 |

### 9.3 决策触发条件

| 触发条件 | 决策 | 执行动作 |
|---------|------|---------|
| HarnessAgent PoC 验证失败 | 启动备用方案 | 降级到裸 ReActAgent（增 3 人日）或自研循环引擎（增 3-5 人日） |
| Phase 1 延迟 > 20 人日 | 裁剪 Phase 1 范围 | 延迟 ExternalSearchTool 到 Phase 3 |
| Phase 2 延迟 > 14 人日 | 裁剪 Phase 2 范围 | 延迟 Prometheus 指标和日志增强到 Phase 3（节省 1.5 人日） |
| Phase 3 评估不满足质量目标 | 延长 Phase 3 | 增加迭代优化轮次 |

---

## 附录 A：开发顺序建议

基于依赖关系和风险考虑，建议的开发和集成顺序：

```
第 1 步：Phase 0 PoC（P0-1）→ 验证 AgentScope 集成可行性
第 2 步：Phase 1 核心（T1.1 → T1.2 → T1.5 → T1.6 → T1.7）
         → 快速搭建 Agent 主循环骨架
第 3 步：Phase 1 工具（T1.3 → T1.4）→ 注册基础检索工具
第 4 步：Phase 1 集成（T1.8 → T1.9 → T1.10 → T1.11）
         → 完整的 Agentic RAG API
第 5 步：Phase 2 质量（T2.1 → T2.2 → T2.3）
         → 质量保障闭环
第 6 步：Phase 1 补丁（T1.12）+ Phase 2 可观测（T2.4 → T2.5）
         → 外部搜索和可观测性
第 7 步：Phase 3 核心增强（T3.1 → T3.2 → T3.3 → T3.4 → T3.5 → T3.6 → T3.7）
         → 质量评估 + 扩展工具 + 查询优化
第 8 步：Phase 3 高级特性（T3.8 → T3.9 → T3.10）
         → 子 Agent 编排 + Hook 管道配置 + 双层记忆集成
第 9 步：沙箱可选开启（T2.6）
         → 按需启用 gVisor 沙箱隔离
```

## 附录 B：工作量汇总

| 阶段 | 任务 | 工时（人日） |
|------|------|:-----------:|
| **Phase 0** | P0-1 AgentScope PoC | 2.0 |
| **Phase 1** | T1.1 依赖与配置 | 0.5 |
| | T1.2 AgentTool 接口 | 0.5 |
| | T1.3 VectorSearchTool | 1.0 |
| | T1.4 BM25SearchTool | 0.5 |
| | T1.5 ToolRegistry | 1.0 |
| | T1.6 AgentState 模型 | 1.0 |
| | T1.7 AgentOrchestrator 核心 | 4.0 |
| | T1.8 AgenticRagFlow | 1.0 |
| | T1.9 API 端点 | 1.0 |
| | T1.10 路由策略 | 1.0 |
| | T1.11 轨迹持久化 | 1.5 |
| | T1.12 ExternalSearchTool | 0.5 |
| | *Phase 1 小计* | *14.0* |
| **Phase 2** | T2.1 SufficientContextAgent | 3.0 |
| | T2.2 SelfReflection | 2.0 |
| | T2.3 CorrectiveRepair | 2.0 |
| | T2.4 Prometheus 指标 | 1.0 |
| | T2.5 日志增强 | 0.5 |
| | T2.6 沙箱集成 | 1.0 |
| | *Phase 2 小计* | *9.5* |
| **Phase 3** | T3.1 LLM Judge | 2.0 |
| | T3.2 SqlQueryTool | 2.0 |
| | T3.3 MemoryQueryTool | 1.0 |
| | T3.4 查询分解 | 1.5 |
| | T3.5 Step-Back 查询 | 1.0 |
| | T3.6 多级缓存 | 1.5 |
| | T3.7 转换门控 | 0.5 |
| | T3.8 子 Agent 编排 | 1.5 |
| | T3.9 Hook 管道配置 | 0.5 |
| | T3.10 双层记忆集成 | 1.0 |
| | *Phase 3 小计* | *10.5* |
| **合计** | | **36.0 人日** |

按 4 人团队估算，约 **2 个日历周** 完成 Phase 1，**1.5 个日历周** 完成 Phase 2，**2 个日历周** 完成 Phase 3。总计约 **5.5 个日历周**。

---

## 文档结束
