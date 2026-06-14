# Agentic RAG 需求规格说明书

**文档版本**: v1.0  
**编写日期**: 2026-06-14  
**项目名称**: RAG 智能知识库问答系统  
**文档类型**: 需求规格说明书  

---

## 修订记录

| 版本 | 日期 | 修订内容 | 修订人 |
|------|------|---------|--------|
| v1.0 | 2026-06-14 | 初稿 | - |

---

## 目录

1. [引言](#1-引言)
2. [术语定义](#2-术语定义)
3. [当前系统分析](#3-当前系统分析)
4. [总体需求概述](#4-总体需求概述)
5. [功能需求](#5-功能需求)
6. [非功能需求](#6-非功能需求)
7. [系统架构需求](#7-系统架构需求)
8. [接口需求](#8-接口需求)
9. [数据需求](#9-数据需求)
10. [需求优先级与实施路线](#10-需求优先级与实施路线)
11. [附录：架构对比](#11-附录架构对比)

---

## 1. 引言

### 1.1 编写目的
本文档旨在明确 RAG 智能知识库系统从 **Workflow RAG（固定管道）** 升级到 **Agentic RAG（自主代理循环）** 的全部需求，为后续的详细设计、编码实现和测试验证提供依据。

### 1.2 项目背景
当前系统基于 Spring AI 1.1.7 构建，采用管道模式（Pipeline Pattern）实现 RAG 流程：查询清洗 → 增强 → 检索 → CRAG 修正 → 去重 → 过滤 → 重排序 → 压缩 → 生成。该架构是典型的 **Workflow RAG**，各阶段职责固定、执行顺序固定，缺乏自主决策能力。

业界实践表明，Agentic RAG 在多跳准确率上可达 **84.5%**（vs Workflow RAG 的 45.2%），幻觉率可降至 **7.1%**（vs 28.5%）。从单次检索到 Agent 工具使用的转变带来了 **5.9 倍**的提升。

### 1.3 适用范围
- 系统架构师：理解架构升级方向
- 开发工程师：了解具体实现需求
- 测试人员：制定测试用例
- 项目管理者：评估工作量和优先级

---

## 2. 术语定义

| 术语 | 英文 | 说明 |
|------|------|------|
| Agentic RAG | Agentic Retrieval-Augmented Generation | 基于自主代理的检索增强生成，Agent 具备规划、决策、反思能力 |
| Supervisor Agent | Supervisor Agent | 主代理，负责任务规划、工具路由、结果综合 |
| Worker Agent | Worker Agent | 专业代理，负责单一类型检索/操作 |
| Tool | Tool | Agent 可调用的功能单元 |
| Sufficient Context Agent | Sufficient Context Agent | 上下文完备性检查代理，判断检索材料是否足以回答问题 |
| Self-Reflection | Self-Reflection | 自反思机制，检查答案质量和引用完整性 |
| Corrective Repair | Corrective Repair | 纠错机制，有界循环内修复答案缺陷 |
| LLM Judge | LLM Judge | 运行时质量评估器 |
| Query Decomposition | Query Decomposition | 查询分解，将复杂问题拆解为多个子查询 |
| Step-Back Query | Step-Back Query | 后退式查询，为过于具体的问题生成更宽泛的检索 |
| Trajectory | Trajectory | Agent 执行轨迹，记录完整的决策路径 |
| Bounded Loop | Bounded Loop | 有界循环，设置最大轮次防止无限执行 |
| StateGraph | State Graph | 状态图，用于状态管理和执行轨迹持久化 |

---

## 3. 当前系统分析

### 3.1 当前架构（Workflow RAG）

```
输入 → QueryCleaningStage → QueryEnhancementStage
     → MultiQueryGenerationStage → ParallelRetrievalStage
     → RetrievalStage → CragStage → DeduplicationStage
     → FilteringStage → ReRankingStage → CompressionStage
     → GenerationStage → 输出
```

### 3.2 当前局限

| 局限 | 说明 | 影响 |
|------|------|------|
| 固定执行顺序 | 所有查询走相同阶段序列，无法根据查询特点动态调整 | 简单问题过度处理，复杂问题处理不足 |
| 单轮检索 | 检索仅执行一次（或并行若干次），不支持根据结果决定是否再次检索 | 信息不足时无法补充 |
| 无完备性检查 | 没有机制判断检索到的信息是否足以回答问题 | 可能基于不完整信息生成答案 |
| 无自反思 | 生成答案后没有自我检查机制 | 错误或遗漏难以被捕获 |
| 无工具抽象 | 检索逻辑直接编码在管道中，无法被 Agent 灵活调用 | 扩展新检索源需修改管道代码 |
| 无执行轨迹 | 没有持久化的决策路径记录 | 难以审计和调试 |

### 3.3 可复用的现有能力

| 现有组件 | 可复用为 | 说明 |
|---------|---------|------|
| `HybirdContentRetriever` | Vector Search Tool | 混合检索（向量 + BM25）+ RRF 融合 |
| `Bm25Indexer` | BM25 Search Tool | 关键词搜索 |
| `BasicContentRetriever` | Vector Search Tool | 纯向量搜索 |
| `ExternalSearchService` | External Search Tool | Tavily/Bing 外部搜索 |
| `RetrievalEvaluator` | 部分复用为 Quality Gate | CRAG 检索质量评估 |
| `RagEvaluator` | LLM Judge | RAGAS 风格评估 |
| `ShortTermMemoryManager` | Memory Query Tool | 短期记忆查询 |
| `LongTermMemoryManager` | Memory Query Tool | 长期记忆查询 |
| `RagMetrics` | 可观测性 | Prometheus 指标 |
| `ResilienceHelper` | 容错 | 熔断/重试/超时 |

---

## 4. 总体需求

### 4.1 业务目标
将现有 Workflow RAG 升级为 Agentic RAG，使系统具备自主规划、多源检索、完备性检查、自反思纠错的能力，显著提升复杂问题的回答质量和可靠性。

### 4.2 核心能力对比

| 能力维度 | 当前系统 | 目标系统 |
|---------|---------|---------|
| 执行模型 | 固定管道 | Agent 自主循环 |
| 查询处理 | 增强 → 检索 | 分解 → 规划 → 逐步执行 |
| 检索次数 | 1 轮（或 N 路并行） | 多轮，Agent 根据结果动态决策 |
| 检索源数量 | 2 种（向量 + BM25） | 4+ 种（向量 + BM25 + SQL + 外部搜索 + 记忆） |
| 质量保障 | CRAG + 后处理过滤 | 完备性检查 + 自反思 + 纠错 + 运行时评估 |
| 状态管理 | RagContext（一次性） | 持久化状态图（可审计、可恢复） |
| 可审计性 | 无 | 完整执行轨迹记录 |

### 4.3 定量目标

| 指标 | 当前（估算） | 目标 | 说明 |
|------|------------|------|------|
| 多跳准确率 | ~50% | ≥80% | 复杂多步推理问题的正确率 |
| 幻觉率 | ~20-28% | ≤10% | 答案中无依据内容的占比 |
| 检索完备率 | 无指标 | ≥90% | 检索材料足以回答问题的比例 |
| p95 延迟（简单） | ~3s | ≤3s | 简单事实性问题 |
| p95 延迟（复杂） | ~6s | ≤8s | 多跳推理问题 |

---

## 5. 功能需求

### 5.1 Supervisor Agent（规划与编排）

#### FR-1 Supervisor Agent

**优先级**: P0  
**描述**: 实现一个 LLM 驱动的 Supervisor Agent，负责接收用户查询、规划执行方案、路由任务到合适的 Worker Agent、综合结果，并决定继续执行或终止。

**子需求**:

**FR-1.1 意图识别与查询分解**
| 内容 | 说明 |
|------|------|
| 输入 | 用户原始查询字符串 |
| 处理 | 识别查询类型（事实性/分析性/比较性/多跳推理） |
| 输出 | 子查询列表 + 依赖关系（如子查询 B 需要子查询 A 的结果） |
| 边界 | 单原子查询不需要分解，直接路由 |
| 异常 | LLM 解析失败时，将整个问题作为单一子查询 |

**FR-1.2 工具路由决策**
| 内容 | 说明 |
|------|------|
| 处理 | 为每个子查询选择最合适的工具（基于工具的 name + description） |
| 策略 | 支持 Top-1 路由和 Top-N 并行路由（如同时调向量和 BM25） |
| 边界 | 至少注册 2 个工具才能路由 |
| 异常 | 无合适工具时返回明确的不可执行说明 |

**FR-1.3 多步执行**
| 内容 | 说明 |
|------|------|
| 处理 | Agent 基于中间结果决定下一步：继续检索/综合/生成/终止 |
| 并行 | 无依赖的子查询可并行执行（CompletableFuture） |
| 边界 | 单步执行超时 15s（复用 ResilienceHelper） |

**FR-1.4 结果综合**
| 内容 | 说明 |
|------|------|
| 处理 | 将多个子查询结果合并为统一的上下文 |
| 冲突处理 | 检测跨源冲突并标记，由 Agent 决策如何消歧 |
| 引用保留 | 每个信息片段保留来源标识（工具名 + 文档 ID） |

**FR-1.5 循环终止决策**
| 内容 | 说明 |
|------|------|
| 终止条件 | 信息完备 或 达到最大循环 或 超时 |
| 降级行为 | 信息不足时：明确声明无法回答，附上已找到的局部信息 |
| 边界 | 最大循环 5 次，总耗时 ≤ 30s |

**验收用例**:
```
输入: "比较 Spring Boot 和 Quarkus 在微服务场景下的优缺点，
       并给出基于我们团队技术栈（Java 21 + K8s）的推荐"

预期行为:
1. 分解为: [Spring Boot 微服务特点, Quarkus 微服务特点, 技术栈匹配度分析]
2. 前两个子查询并行路由到 vector_search
3. 第三个子查询路由到 memory_query（查团队技术栈偏好）+ vector_search
4. 综合三路结果
5. Sufficient Context Agent 检查完备性
6. 生成带引用的对比分析 + 推荐结论
```

---

### 5.2 Worker Agent 工具集

#### FR-2 Vector Search Worker

**优先级**: P0  
**描述**: 将 `HybirdContentRetriever` 封装为 Agent 工具。

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

**实现**: 调用 `HybirdContentRetriever.retrieve(query, topK, threshold, source)`，将返回的 `List<Document>` 转为工具输出格式。

---

#### FR-3 BM25 Search Worker

**优先级**: P0  
**描述**: 将 `Bm25Indexer` 封装为独立工具。

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

**实现**: 调用 `Bm25Indexer.search(query, topK)`，将 `ScoredDocument` 转为 `Document` 列表。

---

#### FR-4 External Search Worker

**优先级**: P1  
**描述**: 将 `ExternalSearchService` 封装为工具。

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

**实现**: 调用 `ExternalSearchService.search(query)`，异常时返回空列表 + 错误标记。

---

#### FR-5 SQL Query Worker

**优先级**: P1  
**描述**: Text-to-SQL 工具，Agent 自动生成 SQL、执行查询并解释结果。

**工具定义**:
```json
{
  "name": "sql_query",
  "description": "通过自然语言查询关系数据库中的结构化数据。适用于统计数据、聚合查询、精确数值查找、历史记录查询",
  "parameters": {
    "query": {"type": "string", "required": true, "description": "自然语言描述的查询需求"},
    "table_hint": {"type": "string", "required": false, "description": "指定优先查询的表名"}
  }
}
```

**实现**:
1. Agent 调用 `sql_query` 工具，传入自然语言
2. 系统拼接 Schema 信息 + 用户查询，调用 LLM 生成 SQL
3. 执行 SQL 校验（只读 SELECT 白名单）
4. JDBC 执行查询
5. 结果转为结构化文本返回给 Agent

**安全约束**:
- 只允许 SELECT 查询（自动校验，非 SELECT 直接拒绝）
- 查询超时 5s
- 行数限制 1000 行

---

#### FR-6 Memory Query Worker

**优先级**: P1  
**描述**: 查询用户记忆封装为工具。

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

**实现**: 调用 `ShortTermMemoryManager.getHistory(userId)` 和 `LongTermMemoryManager.getRelevantMemories(userId, query)`，合并结果返回。

---

#### FR-7 工具注册框架

**优先级**: P1  
**描述**: 统一的工具注册机制，新增工具无需修改 Agent 核心代码。

**子需求**:

| ID | 要求 | 验收标准 |
|----|------|---------|
| FR-7.1 | 基于接口 `AgentTool` 定义工具 | 实现 `AgentTool` 接口的类自动注册 |
| FR-7.2 | `ToolRegistry` 管理工具注册表 | 可通过 `ToolRegistry.getTool(name)` 获取工具 |
| FR-7.3 | LLM 友好的工具描述 | 工具 name + description + parameters 格式化为 LLM 提示词 |
| FR-7.4 | 工具可用性检查 | `isAvailable()` 方法，数据库未连接时 SQL 工具标记为不可用 |

```java
// 工具接口
public interface AgentTool<T> {
    String getName();
    String getDescription();
    ToolParameters getParameters();  // 参数 schema
    T execute(Map<String, Object> params);
    boolean isAvailable();
}

// 调用约定
AgentTool<?> tool = toolRegistry.getTool("vector_search");
Map<String, Object> params = Map.of("query", "Spring Boot 优点", "top_k", 5);
List<Document> result = (List<Document>) tool.execute(params);
```

---

### 5.3 Sufficient Context Agent（上下文完备性检查）

#### FR-8 Sufficient Context Agent

**优先级**: P0  
**描述**: 在生成最终答案前检查检索到的信息是否足以回答问题。Google Research 2026 年提出的模式，研究表明可提升准确率 +34%。

**子需求**:

**FR-8.1 检索片段检查**
| 内容 | 说明 |
|------|------|
| 输入 | 原始查询 + 检索到的文档列表 |
| 处理 | LLM 判断文档内容是否包含回答问题所需的关键信息 |
| 输出 | 完备/不完备 + 缺失信息描述 |
| 边界 | 文档为空直接标记不完备 |

**FR-8.2 中间草稿检查（可选，P1 增强）**
| 内容 | 说明 |
|------|------|
| 处理 | 先生成简短中期草稿，再检查草稿是否覆盖所有子问题 |
| 优势 | 比直接检查文档更精确（Answer 级别的覆盖面判断） |

**FR-8.3 缺失分析与重检索指令**
| 内容 | 说明 |
|------|------|
| 输出 | 具体缺失信息 + 建议的补充检索词 |
| 示例 | "你找到了药物和饮食建议，但漏掉了过敏信息。请搜索'过敏'或'不良反应'" |

**FR-8.4 循环控制**
| 内容 | 说明 |
|------|------|
| 最大轮次 | 3 次（可配置） |
| 提前终止 | 连续两轮检查结果无改善时终止 |
| 超时 | 单次检查 + 重检索总耗时 ≤ 15s |

**验收用例**:
```
输入: "这个病人的过敏史、用药记录和饮食建议是什么？"

第 1 轮: 检索到用药记录和饮食建议，但未找到过敏史
Context Agent: "缺失过敏史信息，请搜索"过敏"或"allergy""

第 2 轮: 补充检索后找到过敏史
Context Agent: "所有信息已完备，可以生成答案"
→ 生成完整答案
```

---

### 5.4 Self-Reflection + Corrective Repair（自反思与纠错）

#### FR-9 Self-Reflection

**优先级**: P0  
**描述**: 答案生成后进行自我反思，检查答案质量和完整性。

**子需求**:

**FR-9.1 引用缺失检查**
- 逐句检查答案中的关键主张是否有对应的引用来源
- 标记无引用支持的声明
- 引用格式符合现有规范 `[1]`, `[2]`

**FR-9.2 子查询覆盖检查**
- 对比原始查询分解出的子查询列表和最终答案
- 标记未覆盖的子问题

**FR-9.3 矛盾检测**
- 检测答案中是否存在自相矛盾的内容
- 检测答案与检索材料之间是否存在矛盾（忠实度问题）

**输出格式**:
```json
{
  "has_issues": true,
  "uncited_claims": ["RAG 系统能减少 50% 的幻觉"],
  "uncovered_subqueries": ["Quarkus 的社区活跃度"],
  "contradictions": ["答案说 A 优于 B，但文档显示 B 在 x 场景更优"]
}
```

#### FR-10 Corrective Repair

**优先级**: P0  
**描述**: 基于 Self-Reflection 的结果进行针对性修复。

**子需求**:

**FR-10.1 有界重试**
- 缺失引用 → 补充检索（提取缺失内容中的关键词，调用相应工具）
- 未覆盖子问题 → 对缺失子问题发起专项检索
- 矛盾 → 重新评估矛盾双方的证据强度，选择可信度高的

**FR-10.2 修复验证**
- 修复后再次执行 Self-Reflection
- 确认问题是否修复
- 最大重试：2 次

**FR-10.3 优雅降级**
- 修复仍失败时声明不确定性而非编造答案
- 格式：*"关于[具体方面]，当前知识库信息不足以给出确定答案。以下是我找到的相关信息：..."*

**验收用例**:
```
输入: "RAG 系统的优缺点是什么？"

答案草稿: "RAG 系统能提高答案准确性，减少幻觉"
           （缺少"缺点"内容）

Self-Reflection → 发现"未覆盖缺点分析"

Corrective Repair → 补充检索"RAG 局限性 挑战 不足"

第 2 轮答案: "优点：提高准确性、可追溯... 缺点：延迟增加、检索质量敏感..."
           （完整覆盖优缺点，引用 [1][2][3]）
```

---

### 5.5 运行时质量门禁

#### FR-11 LLM Judge

**优先级**: P1  
**描述**: 在答案返回给用户前，LLM Judge 对答案进行质量评分，低于阈值时触发重新生成或降级。

**子需求**:

**FR-11.1 评估指标**
| 指标 | 说明 | 评分范围 |
|------|------|---------|
| Faithfulness（忠实度） | 答案是否严格基于检索上下文，不包含无依据内容 | 0~1 |
| Answer Relevancy（相关性） | 答案是否直接回答用户问题，不偏题 | 0~1 |
| Context Precision（上下文精确率） | 检索材料中有多少是回答所必需的 | 0~1 |
| Citation Grounding（引用完整性） | 每个关键主张是否有明确的 `[N]` 引用 | 0~1 |

**FR-11.2 阈值配置**
```yaml
quality-gate:
  enabled: true
  thresholds:
    faithfulness: 0.7
    answer_relevancy: 0.6
    citation_grounding: 0.8
  max_retries: 2
```

**FR-11.3 评分记录**
- 评估结果写入 `rag_evaluations` 表
- 关联到 `agent_trajectories` 的轨迹 ID
- 与现有 EvaluationManager 集成

---

### 5.6 查询转换

#### FR-12 Query Transformation

**优先级**: P1  
**描述**: 在检索前对查询进行智能化转换，提高检索匹配率。

**子需求**:

**FR-12.1 Query Decomposition**
| 内容 | 说明 |
|------|------|
| 输入 | 用户原始查询 |
| 处理 | LLM 识别复合问题的原子子问题 |
| 输出 | 子问题列表 + 依赖关系 DAG |
| 示例 | "Spring Boot 和 Quarkus 的优缺点对比" → ["Spring Boot 特点", "Quarkus 特点", "对比分析"] |

**FR-12.2 Step-Back Query**
| 内容 | 说明 |
|------|------|
| 触发 | 检索结果为空或得分低 |
| 处理 | 生成更宽泛的检索词 |
| 示例 | "Spring Boot 3.5 @EnableWebMvc 变更" → "Spring Boot 3.5 Web MVC 变更" |

**FR-12.3 Multi-Query Generation**
| 内容 | 说明 |
|------|------|
| 处理 | 为每个子查询生成 2-3 个同义变体 |
| 合并 | 多个变体检索结果合并去重 |
| 示例 | "Spring Boot 优点" → ["Spring Boot 优势", "Spring Boot 好处", "Spring Boot 特性"] |

**FR-12.4 转换门控**
| 内容 | 说明 |
|------|------|
| 判断 | 是否需要深度转换 |
| 跳过条件 | 简单事实性问题（长度 < 20 字符、无疑问词）直接检索 |
| 目的 | 节省成本，避免过度处理 |

---

## 6. 非功能需求

### 6.1 状态管理与可审计性

#### NFR-1 Agent 执行轨迹持久化

**优先级**: P0  
**描述**: 每次 Agent 执行的完整决策路径被持久化记录，支持回放和分析。

**要求**:
| ID | 要求 | 验收标准 |
|----|------|---------|
| NFR-1.1 | 记录每个 Agent 决策步骤 | 包括：规划、工具选择、工具入参、工具出参、综合、反思、纠错 |
| NFR-1.2 | 轨迹可回放 | 给定轨迹 ID，可重现完整的 Agent 执行过程 |
| NFR-1.3 | 轨迹与评估关联 | `rag_evaluations` 表新增 `trajectory_id` 字段 |
| NFR-1.4 | 存储成本可控 | 轨迹数据保留 30 天后可归档 |

**存储格式**:
```json
{
  "trajectory_id": "traj_20260614_abc123",
  "user_id": "user123",
  "query": "比较 Spring Boot 和 Quarkus",
  "steps": [
    {
      "step": 1,
      "type": "decomposition",
      "input": "比较 Spring Boot 和 Quarkus",
      "output": { "sub_queries": ["Spring Boot 特点", "Quarkus 特点", "对比分析"] },
      "duration_ms": 1200
    },
    {
      "step": 2,
      "type": "tool_call",
      "tool": "vector_search",
      "input": { "query": "Spring Boot 微服务特点", "top_k": 5 },
      "output": { "document_count": 5, "summary": "找到 5 篇相关文档" },
      "duration_ms": 230
    }
  ],
  "total_duration_ms": 3450,
  "final_answer": "...",
  "quality_scores": { "faithfulness": 0.92, "relevancy": 0.88 }
}
```

---

### 6.2 延迟与性能

#### NFR-2 延迟要求

**优先级**: P0  

| ID | 要求 | 目标 |
|----|------|------|
| NFR-2.1 | 简单查询 p95 延迟 | ≤ 3s |
| NFR-2.2 | 复杂查询 p95 延迟 | ≤ 8s |
| NFR-2.3 | Agent 单次 LLM 决策延迟 | ≤ 1s |
| NFR-2.4 | 独立工具并行执行 | ≥ 2 个工具可同时调用 |

#### NFR-3 缓存策略

**优先级**: P1  

| ID | 缓存层级 | 缓存内容 | TTL | 复用 |
|----|---------|---------|-----|------|
| NFR-3.1 | Embedding 缓存 | 查询 → Embedding 向量 | 10 分钟 | 新增 |
| NFR-3.2 | 检索结果缓存 | 查询 → 检索结果 | 5 分钟 | 新增 |
| NFR-3.3 | Agent 决策缓存 | 相似查询 → 规划结果 | 30 分钟 | 新增 |
| NFR-3.4 | 最终答案缓存 | Q&A 对 | 1 小时 | 复用 CacheService |

#### NFR-4 循环控制

**优先级**: P0  

| ID | 控制项 | 默认值 | 可配置 |
|----|--------|--------|--------|
| NFR-4.1 | Agent 主循环最大次数 | 5 | 是 |
| NFR-4.2 | Context Agent 最大重检索轮次 | 3 | 是 |
| NFR-4.3 | Corrective Repair 最大修复轮次 | 2 | 是 |
| NFR-4.4 | 单次 Agent 执行最大耗时 | 30,000ms | 是 |
| NFR-4.5 | LLM 单次调用超时 | 15,000ms | 是（复用 ResilienceHelper） |

---

### 6.3 可观测性

#### NFR-5 新增 Prometheus 指标

**优先级**: P0  

| ID | 指标名 | 类型 | Label | 说明 |
|----|--------|------|-------|------|
| NFR-5.1 | `agent_decision_total` | Counter | - | Agent 决策总次数 |
| NFR-5.2 | `agent_tool_call_total` | Counter | `tool` | 各工具调用次数 |
| NFR-5.3 | `agent_tool_duration_seconds` | Histogram | `tool` | 各工具调用耗时分布 |
| NFR-5.4 | `agent_loop_count` | Histogram | - | Agent 循环轮次分布 |
| NFR-5.5 | `agent_context_complete` | Gauge | - | 上下文完备性评分 |
| NFR-5.6 | `agent_retry_total` | Counter | `reason` | 重试次数及原因 |
| NFR-5.7 | `agent_reflection_issues` | Gauge | - | 自反思发现的问题数 |

**兼容性**: 现有指标 `rag_cache_hit_total`、`rag_llm_call_total`、`rag_answer_duration_seconds` 保持不变。

#### NFR-6 日志要求

**优先级**: P0  

| ID | 要求 | 说明 |
|----|------|------|
| NFR-6.1 | Agent 决策日志 JSON 格式 | 包含完整输入/输出，与 Logstash 兼容 |
| NFR-6.2 | 工具调用日志 | 包含调用参数、耗时、结果摘要 |
| NFR-6.3 | 日志级别可配置 | 开发 DEBUG，生产 INFO |
| NFR-6.4 | 现有 ELK 集成不变 | Filebeat 采集路径不变 |

---

### 6.4 安全性

#### NFR-7 安全约束

**优先级**: P1  

| ID | 要求 | 说明 |
|----|------|------|
| NFR-7.1 | Agent 循环硬性上限 | 总步骤数 ≤ 20（防止无限循环和 Token 爆炸） |
| NFR-7.2 | 工具权限边界 | Agent 只能调用 `ToolRegistry` 注册过的工具 |
| NFR-7.3 | SQL 注入防护 | Text-to-SQL 只允许 SELECT，执行前语法校验 |
| NFR-7.4 | 敏感信息过滤 | Agent 响应中不应暴露密钥/密码/Token |
| NFR-7.5 | 审计日志不可篡改 | 轨迹数据写入后只追加不修改 |

---

## 7. 系统架构需求

### 7.1 核心组件

```
┌──────────────────────────────────────────────────────────┐
│                     API Layer                             │
│    KnowledgeQAController.ask() + /ask/agent 端点         │
└────────────────────────┬─────────────────────────────────┘
                         │
┌────────────────────────▼─────────────────────────────────┐
│               AgenticRagFlow (新增 RagFlow 子类)           │
│                                                          │
│  ┌──────────────────────────────────────────────────┐    │
│  │           AgentOrchestrator (执行引擎)              │    │
│  │  - 主循环: 规划 → 执行 → 检查 → 反思 → 循环         │    │
│  │  - 状态管理: AgentState (可持久化)                  │    │
│  │  - 循环控制: 最大次数 / 超时 / 质量门禁              │    │
│  │  - 并行派发: 独立子查询并行执行                      │    │
│  └──────────────────────┬───────────────────────────┘    │
│                         │                                 │
│  ┌──────────────────────▼───────────────────────────┐    │
│  │            ToolRegistry (工具注册表)               │    │
│  │  VectorSearchTool  /  BM25SearchTool              │    │
│  │  ExternalSearchTool /  SQLQueryTool               │    │
│  │  MemoryQueryTool                                   │    │
│  └──────────────────────────────────────────────────┘    │
│                                                          │
│  ┌──────────────────────────────────────────────────┐    │
│  │        Quality Pipeline (质量保障流水线)            │    │
│  │  SufficientContextAgent → Self-Reflection →       │    │
│  │  CorrectiveRepair → LLM Judge                    │    │
│  └──────────────────────────────────────────────────┘    │
└────────────────────────┬─────────────────────────────────┘
                         │
┌────────────────────────▼─────────────────────────────────┐
│                  基础设施 (复用)                           │
│  Milvus / MySQL / Redis / DashScope API / Tavily/Bing    │
│  Resilience4j / RagMetrics / CacheService                │
└──────────────────────────────────────────────────────────┘
```

### 7.2 Agent 执行循环

```
   ┌─────────────────────────────────────────────────────┐
   │                    开始                               │
   │  用户查询到达 AgenticRagFlow                          │
   └────────────────────┬────────────────────────────────┘
                        ▼
   ┌─────────────────────────────────────────────────────┐
   │              1. Supervisor Agent                     │
   │  意图识别 → 查询分解 → 规划工具路由 → 分配子任务       │
   └────────────────────┬────────────────────────────────┘
                        ▼
   ┌─────────────────────────────────────────────────────┐
   │              2. 执行工具调用                          │
   │  无依赖子查询并行 → 各工具执行 → 收集结果              │
   │  (VectorSearch / BM25Search / ExternalSearch ...)   │
   └────────────────────┬────────────────────────────────┘
                        ▼
   ┌─────────────────────────────────────────────────────┐
   │              3. 综合多源结果                          │
   │  跨源融合 → 冲突检测 → 构建统一上下文                  │
   └────────────────────┬────────────────────────────────┘
                        ▼
   ┌─────────────────────────────────────────────────────┐
   │        4. Sufficient Context Agent                   │
   │  检查上下文完备性 → 是否足以回答问题？                  │
   └────────────────────┬────────────────────────────────┘
              ┌─────────┴─────────┐
              ▼                   ▼
         不完备               完备
              │                   │
              ▼                   ▼
   ┌────────────────┐  ┌─────────────────────────────────┐
   │ 生成重检索指令   │  │     5. 生成答案草稿               │
   │ → 返回步骤 2    │  │  LLM 基于上下文生成带引用答案      │
   └────────────────┘  └──────────────┬──────────────────┘
                                     ▼
              ┌──────────────────────────────────────────┐
              │      6. Self-Reflection                   │
              │  引用检查 → 子查询覆盖 → 矛盾检测          │
              └────────────────┬─────────────────────────┘
              ┌────────────────┴────────────────┐
              ▼                                 ▼
          有 issues                        无 issues
              │                                 │
              ▼                                 ▼
   ┌────────────────────┐    ┌──────────────────────────┐
   │ 7. Corrective Repair│    │  8. LLM Judge            │
   │ 补充检索/修复 →     │    │  质量评分 → 通过阈值？     │
   │ → 回到步骤 5        │    └────────┬─────────────────┘
   └────────────────────┘       ┌──────┴──────┐
                                ▼             ▼
                             通过           不通过
                                │             │
                                ▼             ▼
                        ┌────────────┐  ┌──────────────┐
                        │ 返回答案    │  │ 重试/降级     │
                        │ + 轨迹 ID  │  │ → 回到步骤 5  │
                        └────────────┘  └──────────────┘
```

### 7.3 Agent 状态模型

```java
public class AgentState {
    // ── 输入 ──
    private String originalQuery;
    private String userId;

    // ── 规划阶段 ──
    private List<SubQuery> subQueries;         // 分解后的子查询及依赖
    private List<ToolAssignment> assignments;  // 子查询→工具分配

    // ── 执行阶段 ──
    private Map<String, ToolResult> toolResults; // 工具名 → 执行结果
    private int loopCount;

    // ── 综合阶段 ──
    private String synthesizedContext;           // 综合上下文
    private List<SourceConflict> conflicts;      // 跨源冲突

    // ── 质量检查 ──
    private ContextVerdict contextVerdict;       // 完备性判断
    private List<String> missingInfoQueries;     // 重检索建议

    // ── 生成阶段 ──
    private String draftAnswer;
    private ReflectionReport reflectionReport;

    // ── 最终 ──
    private String finalAnswer;
    private QualityScores qualityScores;

    // ── 轨迹 ──
    private List<StepRecord> trajectory;
    private AgentStatus status = AgentStatus.RUNNING;
    private String error;
}
```

### 7.4 与现有系统的集成关系

| 现有组件 | 集成方式 | 变更程度 |
|---------|---------|---------|
| `AbstractRagFlow` | 新增 `AgenticRagFlow` 子类（与 Basic/Advanced 并行） | 新增 |
| `DefaultRagPipeline` | Agentic 模式下不使用 Pipeline；Workflow 模式保留 | 不修改 |
| `HybirdContentRetriever` | 包装为 `VectorSearchTool` | 包装 |
| `Bm25Indexer` | 包装为 `BM25SearchTool` | 包装 |
| `ExternalSearchService` | 包装为 `ExternalSearchTool` | 包装 |
| `ShortTermMemoryManager` | 包装为 `MemoryQueryTool` | 包装 |
| `LongTermMemoryManager` | 包装为 `MemoryQueryTool` | 包装 |
| `CacheService` | 复用 + 新增 Agent 决策缓存 | 部分新增 |
| `RagMetrics` | 新增 Agent 指标 | 新增指标 |
| `ResilienceHelper` | 工具调用中复用 | 不修改 |
| `RagOrchestrator` | Agentic 模式下替换为 `AgentOrchestrator` | 新增 |
| `KnowledgeQAService` | 新增 Agentic 路由逻辑（分类 → 选择 Agentic/Basic/Advanced） | 修改 |
| `RagEvaluator` | LLM Judge 复用评估逻辑 | 包装 |

---

## 8. 接口需求

### 8.1 新增 API

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/qa/ask/agent` | Agentic RAG 问答（新端点） | JWT |
| GET | `/api/qa/trajectory/{trajectoryId}` | 查询 Agent 执行轨迹详情 | JWT |

**`/api/qa/ask/agent` 请求**:
```json
{
  "userId": "user123",
  "question": "比较 Spring Boot 和 Quarkus",
  "config": {
    "max_loops": 5,
    "timeout_ms": 30000,
    "enable_external_search": false
  }
}
```

**`/api/qa/ask/agent` 响应**:
```json
{
  "answer": "对比分析结果...",
  "sources": [
    {"content": "...", "metadata": {"source": "doc.pdf", "tool": "vector_search"}}
  ],
  "trajectoryId": "traj_20260614_abc123",
  "loopCount": 2,
  "qualityScores": {
    "faithfulness": 0.92,
    "answer_relevancy": 0.88
  },
  "totalDurationMs": 3450
}
```

### 8.2 保持兼容的现有 API

| 现有接口 | 变更 |
|---------|------|
| `POST /api/qa/ask` | 不变（继续使用 Workflow RAG 作为降级模式） |
| 所有认证接口 | 不变 |
| 所有评估查询接口 | 不变，新增 `trajectoryId` 关联 |
| 所有会话管理接口 | 不变 |

### 8.3 Agent 内部接口

```java
// ── 工具接口 ──
public interface AgentTool<T> {
    String getName();
    String getDescription();
    ToolSchema getSchema();
    T execute(Map<String, Object> params);
    boolean isAvailable();
}

// ── Agent 执行器 ──
public interface AgentExecutor {
    AgentResult execute(AgentRequest request);
}

public class AgentRequest {
    private String query;
    private String userId;
    private AgentConfig config;  // 循环上限、超时等
}

public class AgentResult {
    private String answer;
    private List<Document> sources;
    private String trajectoryId;
    private QualityScores qualityScores;
    private int loopCount;
    private long totalDurationMs;
}

// ── 工具注册表 ──
@Component
public class ToolRegistry {
    public void register(AgentTool<?> tool) { ... }
    public AgentTool<?> getTool(String name) { ... }
    public List<AgentTool<?>> getAllTools() { ... }
    public String getToolDescriptionsForLLM() { ... } // 格式化给 LLM 提示词
}
```

---

## 9. 数据需求

### 9.1 新增表

**agent_trajectories**:
```sql
CREATE TABLE agent_trajectories (
    id VARCHAR(36) PRIMARY KEY COMMENT '轨迹唯一标识',
    user_id VARCHAR(100) NOT NULL COMMENT '用户ID',
    query TEXT NOT NULL COMMENT '用户原始查询',
    trajectory JSON NOT NULL COMMENT '完整执行轨迹（步骤列表）',
    total_steps INT DEFAULT 0 COMMENT '总步数',
    total_loops INT DEFAULT 0 COMMENT '总循环轮次',
    total_duration_ms BIGINT DEFAULT 0 COMMENT '总耗时(ms)',
    tools_used JSON COMMENT '使用的工具列表',
    quality_scores JSON COMMENT '质量评分快照',
    status VARCHAR(20) DEFAULT 'COMPLETED' COMMENT 'COMPLETED/FAILED/TIMEOUT',
    error_message TEXT COMMENT '错误信息',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent执行轨迹表';
```

### 9.2 现有表扩展

**rag_evaluations** 扩展:
```sql
ALTER TABLE rag_evaluations
    ADD COLUMN trajectory_id VARCHAR(36) NULL COMMENT '关联的Agent轨迹ID',
    ADD INDEX idx_trajectory_id (trajectory_id);
```

### 9.3 数据兼容性

- `trajectory_id` 可为 NULL（Workflow RAG 模式无轨迹）
- 现有评估查询 API 全部保持向后兼容

---

## 10. 需求优先级与实施路线

### 10.1 优先级总览

| 优先级 | 含义 | 占比 |
|--------|------|------|
| P0 | 必须实现，否则 Agentic RAG 不可用 | ~60% |
| P1 | 重要功能，显著提升效果和可用性 | ~30% |
| P2 | 锦上添花，后续迭代优化 | ~10% |

### 10.2 第一阶段：核心 Agent 循环（P0）

| 需求 | 工作项 | 工时 |
|------|--------|------|
| FR-1 | Supervisor Agent 核心（规划 + 路由 + 综合 + 终止决策） | 5 人日 |
| FR-1.3 | 并行子任务执行 | 1 人日 |
| FR-2 | Vector Search Tool（包装 HybirdContentRetriever） | 1 人日 |
| FR-3 | BM25 Search Tool（包装 Bm25Indexer） | 0.5 人日 |
| FR-4 | External Search Tool（包装 ExternalSearchService） | 0.5 人日 |
| NFR-1 | Agent State + 轨迹记录 | 2 人日 |
| NFR-4 | 循环控制 + 超时 | 0.5 人日 |
| - | `/api/qa/ask/agent` 端点和 AgenticRagFlow 骨架 | 1 人日 |
| - | ToolRegistry + AgentTool 接口 | 1 人日 |
| **合计** | | **12.5 人日** |

**阶段目标**: 可运行的 Agentic RAG 原型，支持多工具选择和调用、基本循环控制。

---

### 10.3 第二阶段：质量保障（P0）

| 需求 | 工作项 | 工时 |
|------|--------|------|
| FR-8 | Sufficient Context Agent | 3 人日 |
| FR-9 | Self-Reflection | 2 人日 |
| FR-10 | Corrective Repair | 2 人日 |
| NFR-5 | Agent Prometheus 指标（6 个新指标） | 1 人日 |
| NFR-6 | Agent 日志增强 | 0.5 人日 |
| **合计** | | **8.5 人日** |

**阶段目标**: Agent 能自主检查检索完备性、自反思纠错，质量显著提升。

---

### 10.4 第三阶段：增强与优化（P1 + P2）

| 需求 | 工作项 | 工时 |
|------|--------|------|
| FR-12.1 | Query Decomposition | 2 人日 |
| FR-12.2 | Step-Back Query | 1 人日 |
| FR-11 | LLM Judge 运行时评估 | 2 人日 |
| FR-5 | SQL Query Worker | 2 人日 |
| FR-6 | Memory Query Worker | 1 人日 |
| NFR-3 | 多级缓存 | 1.5 人日 |
| FR-12.4 | 转换门控 | 0.5 人日 |
| **合计** | | **10 人日** |

**阶段目标**: 完整生产级 Agentic RAG，支持所有工具、质量门禁、缓存优化。

---

### 10.5 总体路线图

```
阶段 1 (P0, ~12.5人日)    阶段 2 (P0, ~8.5人日)     阶段 3 (P1+P2, ~10人日)
┌────────────────────┐   ┌────────────────────┐   ┌────────────────────┐
│ Supervisor Agent   │   │ Sufficient Context │   │ LLM Judge          │
│ Vector Search Tool │   │   Agent            │   │ Query Decomposition │
│ BM25 Search Tool   │   │ Self-Reflection    │   │ Step-Back          │
│ External Tool      │──▶│ Corrective Repair  │──▶│ SQL Query Tool     │
│ ToolRegistry       │   │ Prometheus 指标    │   │ Memory Tool        │
│ 轨迹持久化          │   │ 日志增强           │   │ 多级缓存           │
│ Agent 循环控制      │   │                    │   │ 转换门控           │
│ API 端点           │   │                    │   │                    │
└────────────────────┘   └────────────────────┘   └────────────────────┘
                               ▲                            ▲
                               │ 总预估: ~31 人日            │
                               └────────────────────────────┘
```

---

## 11. 附录：架构对比

### 11.1 当前（Workflow RAG）vs 目标（Agentic RAG）

| 维度 | Workflow RAG | Agentic RAG |
|------|-------------|-------------|
| 执行模型 | 固定管道，一次执行 | Agent 循环，动态决策 |
| 检索策略 | 预设规则（topK, threshold） | Agent 根据结果动态调整 |
| 检索轮次 | 1 轮（或 N 路并行） | 多轮，直到完备 |
| 信息完备性 | 不检查 | Sufficient Context Agent 逐轮检查 |
| 错误处理 | 固定降级（CRAG → 外部搜索） | Self-Reflection + Corrective Repair |
| 质量评估 | 异步后置（不阻塞主流程） | 运行时 LLM Judge 门禁 |
| 可审计性 | 无轨迹记录 | 完整轨迹持久化 |
| 扩展检索源 | 修改管道代码，增加阶段 | 注册新工具到 ToolRegistry |
| 代码侵入 | 修改现有 PipelineStage | 新增 AgentTool 实现类 |

### 11.2 关键风险与缓解

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|---------|
| LLM 决策不稳定（Agent 规划质量波动） | 答案质量不稳定 | 中 | 设置决策缓存 + 兜底路由（默认走 vector_search） |
| 循环失控导致高延迟 | 用户体验下降 | 低 | 硬性循环上限 + 超时熔断 + 降级到 Workflow RAG |
| Token 消耗大幅增加 | 运营成本上升 | 高 | 转换门控 + 缓存 + 简单查询直接路由不走 Agent |
| Agent 调用错误工具 | 检索结果无效 | 低 | 工具描述完善 + 结果有效性检查 |
| 与现有 Workflow RAG 并行维护 | 维护成本增加 | 中 | 共享 Tool 实现，Agent 和 Pipeline 共用同一检索逻辑 |

### 11.3 参考来源

| 来源 | 链接 | 核心贡献 |
|------|------|---------|
| Elastic: Building production AI agents | [el astic.co/blog/building-ai-agents](https://www.elastic.co/blog/building-ai-agents-elasticsearch-platform) | 生产环境 Agent 实践经验 |
| Google Research: Sufficient Context Agent | [research.google/blog](https://research.google/blog/unlocking-dependable-responses-with-gemini-enterprise-agent-platforms-agentic-rag/) | Sufficient Context Agent 模式 +34% 准确率 |
| InfoQ: Protocol-H 分层次 Agentic RAG | [infoq.cn](https://www.infoq.cn/article/zx2KoLNl2bz0zh8TuCZd) | Supervisor-Worker 拓扑 + 84.5% 准确率 |
| arXiv: AgenticRAG (May 2026) | [arxiv.org/abs/2605.05538](https://browse-export.arxiv.org/abs/2605.05538) | Agent 工具使用 → 5.9x 提升 |
| FutureAGI: Agentic RAG Playbook | [futureagi.com](https://futureagi.com/ebooks/mastering-agentic-rag/) | 生产级 Agentic RAG 最佳实践 |
| GreenNode: Low-Latency RAG + AI Agents | [greennode.ai](https://greennode.ai/blog/rag-ai-agents-low-latency-architecture) | 延迟优化架构 |

---

**文档结束**
