# 开发进度检查点 — 2026-06-14

## 当前状态：Phase 1 核心实现完成（T1.1 ~ T1.10）
## 下一阶段起点：T1.11 轨迹持久化

---

## 已完成任务

### Phase 1 — 核心 Agent 循环 (P0)

| 任务 | 状态 | 文件路径 |
|------|------|---------|
| **T1.1** AgentConfig + AgenticRagConfig + AGENTS.md + 配置 | ✅ 编译通过 | `core/.../agentic/config/`, `core/.../agentic/agent/AgentConfig.java` |
| **T1.2** AgentTool 接口 + ToolResult 模型 | ✅ 编译通过 | `core/.../agentic/tool/AgentTool.java`, `ToolResult.java` |
| **T1.3** VectorSearchTool | ✅ 编译通过 | `core/.../agentic/tool/VectorSearchTool.java` |
| **T1.4** BM25SearchTool | ✅ 编译通过 | `core/.../agentic/tool/Bm25SearchTool.java` |
| **T1.5** ToolRegistry | ✅ 编译通过 | `core/.../agentic/tool/ToolRegistry.java` |
| **T1.6** AgentState + StepRecord + AgentStatus + Quality 模型 | ✅ 编译通过 | `core/.../agentic/agent/AgentState.java`, `trajectory/StepRecord.java`, `quality/` |
| **T1.7** AgentOrchestrator（HarnessAgent 集成） | ✅ 编译通过 | `core/.../agentic/agent/AgentOrchestrator.java` |
| **T1.8** AgenticRagFlow | ✅ 编译通过 | `core/.../agentic/AgenticRagFlow.java` |
| **T1.9** API 端点（ask/agent + trajectory）+ AgenticAsk DTO | ✅ 编译通过 | `api/.../KnowledgeQAController.java`, `model/AgenticAskRequest.java` |
| **T1.10** QueryRouter（规则+LLM 分类路由） | ✅ 编译通过 | `core/.../agentic/router/QueryRouter.java` |

### 基础设施

| 项目 | 状态 | 说明 |
|------|------|------|
| `agentscope-harness:2.0.0-RC3` 依赖 | ✅ 已在 parent POM + core POM | 版本由 parent 管理 |
| `application-dev.yml` agentic-rag 配置段 | ✅ 已追加 | 完整配置（含 hook/workspace/quality/routing） |
| AGENTS.md 工作区人格文件 | ✅ 已创建 | `workspace/knowledge-agent/AGENTS.md` |
| package 目录结构 | ✅ 已创建 | agentic/agent/, tool/, quality/, trajectory/, router/, config/ |
| 整体编译 | ✅ `mvn compile` 通过 | 所有模块 SUCCESS |

---

## 待完成任务

### Phase 1 剩余

| 任务 | 预估 | 前置 | 说明 |
|------|:----:|------|------|
| **T1.11** 轨迹持久化 | 1 人日 | T1.6 | 完善 TrajectoryRepository + TrajectoryEntity + 数据库表 |
| **T1.12** ExternalSearchTool | 0.5 人日 | T1.2 | 包装 ExternalSearchService |

### Phase 2 — 质量保障 (P0)

| 任务 | 预估 | 前置 |
|------|:----:|------|
| **T2.1** SufficientContextAgent（LLM 完备性检查） | 3 人日 | T1.7 |
| **T2.2** SelfReflection（自反思） | 2 人日 | T1.7 |
| **T2.3** CorrectiveRepair（纠错） | 2 人日 | T2.2 |
| **T2.4** Agent Prometheus 指标（7 个新指标） | 1 人日 | T1.7 |
| **T2.5** Agent 日志增强 | 0.5 人日 | T1.7 |
| **T2.6** 沙箱集成（可选） | 1 人日 | T1.7 |

### Phase 3 — 增强与优化 (P1+P2)

| 任务 | 预估 | 前置 |
|------|:----:|------|
| **T3.1** LLM Judge | 2 人日 | T2.3 |
| **T3.2** SqlQueryTool | 2 人日 | T1.2 |
| **T3.3** MemoryQueryTool | 1 人日 | T1.2 |
| **T3.4** 查询分解（Query Decomposition） | 1.5 人日 | T1.7 |
| **T3.5** Step-Back 查询 | 1 人日 | T1.7 |
| **T3.6** 多级缓存 | 1.5 人日 | T1.7 |
| **T3.7** 转换门控 | 0.5 人日 | T3.4, T3.5 |
| **T3.8** 子 Agent 编排 | 1.5 人日 | T1.7 |
| **T3.9** Hook 管道配置 | 0.5 人日 | T1.7 |
| **T3.10** 双层记忆集成 | 1 人日 | T1.7 |

---

## 关键技术决策

### HarnessAgent API（实际 API vs 设计文档）

- **包名**: `io.agentscope.harness.agent.HarnessAgent`（非 `com.alibaba.agentscope`）
- **Builder**: `.model(Model)` DashScopeChatModel 在 `io.agentscope.core.model` 包
- **Hook**: `io.agentscope.core.hook.Hook`（已标记 deprecated，推荐使用 Middleware，但 Phase 1 仍使用 Hook）
- **Toolkit**: `io.agentscope.core.tool.Toolkit`（HarnessAgent 未直接暴露 `.tool()`，需通过 builder 字段设置）
- **compactionConfig/memoryConfig**: 包级私有字段，无公开 setter，使用默认值
- **RuntimeContext**: `RuntimeContext.builder().userId().sessionId().put().build()`

### 工具注册方式

- 自定义 `AgentTool` 接口（非 AgentScope 原生 AgentTool）
- `ToolRegistry` 收集后直接通过 HarnessAgent builder 传入
- 未来可以改为直接注入到 HarnessAgent 的 Toolkit

### 代码结构

```
core/src/main/java/org/example/core/rag/agentic/
├── AgenticRagFlow.java              # AbstractRagFlow 子类
├── agent/
│   ├── AgentOrchestrator.java       # 主循环引擎（HarnessAgent）
│   ├── AgentConfig.java             # 配置模型
│   ├── AgentState.java              # 状态模型
│   ├── AgentStatus.java             # 枚举
│   └── AgentDecisionType.java       # 枚举
├── tool/
│   ├── AgentTool.java               # 接口
│   ├── ToolRegistry.java            # 注册表
│   ├── ToolResult.java             # 结果封装
│   ├── VectorSearchTool.java        # 向量检索
│   └── Bm25SearchTool.java          # BM25 关键词
├── quality/
│   ├── SufficientContextAgent.java  # 基础实现（Phase 2 完善）
│   ├── ContextVerdict.java
│   ├── ReflectionReport.java
│   ├── QualityScores.java
│   └── QualityThresholds.java
├── trajectory/
│   ├── StepRecord.java
│   └── TrajectoryRecorder.java     # 基础实现（T1.11 完善）
├── router/
│   └── QueryRouter.java             # 路由
└── config/
    └── AgenticRagConfig.java        # Spring 配置
```

---

## 已知问题

1. Hook API deprecated — HarnessAgent 推荐使用 `MiddlewareBase`，后续需迁移
2. `compactionConfig`/`memoryConfig` 无法通过 builder 设置 — 使用 HarnessAgent 默认值
3. TrajectoryRecorder 仅为日志记录，尚未写入数据库
4. RagAnswer 的 `sources` 为 `List<String>` 而非 `List<Document>` — AgenticRagFlow 的文档转字符串逻辑
5. 部分 Phase 2/3 的 quality 组件为 stub（SufficientContextAgent 使用简单字符长度判断）

---

## 构建命令

```bash
# 编译
mvn compile

# 打包
mvn package -DskipTests

# 运行（需设置 DASHSCOPE_API_KEY）
export DASHSCOPE_API_KEY=sk-xxx
mvn spring-boot:run -pl starter
```
