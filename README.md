# RAG 智能知识库问答系统

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.0-blue)]()
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.1.7-green)]()
[![Java](https://img.shields.io/badge/Java-21-orange)]()
[![License](https://img.shields.io/badge/License-MIT-yellow)]()

企业级 RAG（Retrieval-Augmented Generation）知识库问答系统，基于 **Spring AI** 构建，采用 **管道模式 + 策略模式 + 编排器模式** 实现智能问答、记忆管理、质量评估与全方位可观测性。

# 后续优化项小记

> **当前阶段一目标已完成**，该项目已经是可以部署运行的企业级 RAG 知识库系统。  
> 后续将进行 Agent 学习开发，故对功能优化暂作记录留存。

## 1. Query

- **Query 预处理**  
  项目中实现了 query 预处理，但实现方式比较简单，只处理了空格、换行等基础操作。  
  **后续可优化**：添加特殊字符处理，hyDE，parent-child等增强功能。

- **Multi-Query**  
  项目中实现了 multi-query，但对 multi-query 的处理比较简单。  
  **后续可优化**：作为优化点进行增强。

## 2. Document Loader

项目中文档加载的实现比较简单。  
**后续可优化方案**：

- 提供不同的文本切分器，由前端页面人工选择切分器并上传文件；
- 后端根据所选切分器进行分割，将分割后的文档返回前端；
- 由人工审核或人工调整分割结果后，再提交入库。
### splitter
**分割器优化**
- 自定义分割器添加更多的元数据，在自定义操作中可以有更多的选择

## 3. retrieve

- **混合检索**
项目采用混合检索的方式进行召回，关键词检索采用的BNM25，本项目中由内存+处理逻辑自实现，后续可优化为通过milvus数据库做关键词查询

## 4. rerank

- **MMRrerank**
  重排序只用了向量化重排序的方式，优化点为，将question和docs输入给llm，让大模型给出更精确的排序，缺点是耗时和token消耗

## 5. generation
- **生成**
  最后的结果生成阶段，处理比较潦草。后续当重构该阶段

## 6. 山下文

- **记忆**
 项目采用了本地cache，redis，file三种方式，分层记录上下文内容，记忆提取比较粗暴，可根据需求制定提取记忆内容

## 7. ragas
- **质量评估**
  实现了RagMetrics RAG 自定义业务指标
  ragas 留了个钩子 待后续实现
---

## 核心特性

### RAG 引擎
- **混合检索**：向量相似度（Milvus 稠密检索）+ BM25 关键词（稀疏检索），通过 **RRF（Reciprocal Rank Fusion）** 算法融合
- **10 阶段管道**：查询清洗 → 查询增强 → 多查询生成 → 并行检索 → 单次检索 → CRAG 修正 → 去重 → 过滤 → MMR 重排序 → 上下文压缩 → 生成
- **纠错机制（CRAG）**：LLM 评估检索质量（正确/模糊/错误），模糊时精炼重查，错误时切换外部搜索（Tavily/Bing）
- **文档处理**：PDF / Markdown / TXT / CSV / JSON / 代码等多格式支持，200~500 字符智能分块

### 记忆管理
- **短期记忆**：Redis 持久化 + 内存二级缓存，30 分钟 TTL，多实例共享
- **长期记忆**：MySQL 持久化 + 向量检索，LLM 自动提取事实/偏好/上下文，相似度合并

### 企业级能力
- **安全**：JWT + Refresh Token + BCrypt + API 速率限制（Bucket4j）
- **容错**：Resilience4j 三层防护（熔断 → 重试 → 超时）
- **缓存**：Redis 问答缓存（1 小时 TTL）+ Caffeine 本地缓存
- **可观测性**：Prometheus + Grafana + Zipkin + ELK + 钉钉告警

---

## 快速开始

### 环境要求
- Java 21+
- Docker 20.10+ / Docker Compose 2.0+

### 启动

```bash
# 1. 配置环境变量
cp .env.example .env
# 编辑 .env: JWT_SECRET, DB_PASSWORD, DASHSCOPE_API_KEY

# 2. 启动基础设施（MySQL, Redis, Prometheus, Grafana, Zipkin）
docker-compose up -d

# 3. 编译并启动
mvn clean package -DskipTests
java -jar starter/target/starter-1.0-SNAPSHOT.jar --spring.profiles.active=prod
```

### 验证

```bash
# 健康检查
curl http://localhost:8080/actuator/health

# 登录
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password123"}'

# RAG 问答
curl -X POST http://localhost:8080/api/qa/ask \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"userId":"user123","question":"什么是 RAG？"}'
```

### 服务端口

| 服务 | 端口 | 说明 |
|------|------|------|
| API | 8080 | REST API / Swagger |
| Grafana | 3000 | 监控面板 (admin/123456) |
| Prometheus | 9090 | 指标收集 |
| Zipkin | 9411 | 分布式追踪 |
| Kibana | 5601 | 日志搜索 |

---

## 系统架构

```
┌─────────────────────────────────────────────────────┐
│              API Layer (Spring MVC)                  │
│   AuthController / KnowledgeQAController             │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│               RAG Flow (AbstractRagFlow)             │
│  ┌─────────────────────────────────────────────┐    │
│  │         RagOrchestrator (编排器)              │    │
│  │  beforeExecute: 缓存检查 → 加载记忆 → 分类复杂度 │    │
│  │  afterExecute:  保存记忆 → 异步评估 → 缓存结果  │    │
│  └─────────────────────────────────────────────┘    │
│  ┌─────────────────────────────────────────────┐    │
│  │         RagPipeline (管道，按序执行阶段)       │    │
│  │  ① QueryCleaningStage                       │    │
│  │  ② QueryEnhancementStage (记忆+关键词扩展)    │    │
│  │  ③ MultiQueryGenerationStage                │    │
│  │  ④ ParallelRetrievalStage (并行多路检索)      │    │
│  │  ⑤ RetrievalStage (单次检索)                 │    │
│  │  ⑥ CragStage (纠错式 RAG)                   │    │
│  │  ⑦ DeduplicationStage (去重)                │    │
│  │  ⑧ FilteringStage (过滤)                    │    │
│  │  ⑨ ReRankingStage (MMR 重排序)               │    │
│  │  ⑩ CompressionStage (上下文压缩)             │    │
│  │  ⑪ GenerationStage (答案生成)                │    │
│  └─────────────────────────────────────────────┘    │
└──────────────────────┬──────────────────────────────┘
                       │
     ┌─────────────────┼─────────────────┐
     ▼                 ▼                 ▼
┌──────────┐    ┌──────────┐    ┌──────────┐
│  Redis   │    │  MySQL   │    │  Milvus  │
│ (缓存)   │    │ (持久化)  │    │ (向量)   │
└──────────┘    └──────────┘    └──────────┘
```

---

## 项目结构

```
knowledge/
├── api/                      # API 层
│   └── controller/           # AuthController, KnowledgeQAController, ThreadPoolMonitorController
├── core/                     # 核心业务逻辑
│   └── main/java/org/example/core/
│       ├── cache/            # Redis 缓存服务
│       ├── compress/         # Embedding/LLM 混合压缩 (HybridCompressor)
│       ├── config/           # 全局配置 (Milvus, Redis, Security, ThreadPool, Resilience4j, Cors)
│       ├── document/         # 文档加载器 (PdfLoader / MarkDownLoader / TextLoader)
│       ├── evaluation/       # RAGAS 质量评估 (RagEvaluator, EvaluationManager, QualityMonitor)
│       ├── health/           # 健康检查 (Redis, Milvus)
│       ├── memory/           # 短期/长期记忆管理 + MemoryExtractor
│       ├── metrics/          # Prometheus 指标 (RagMetrics)
│       ├── rag/              # RAG 核心流程
│       │   ├── context/      # RagContext
│       │   ├── handler/      # CRAG 处理器 / KnowledgeRefiner / ExternalSearchService
│       │   ├── impl/         # BasicRagFlow / AdvancedRagFlow
│       │   ├── orchestrator/ # RagOrchestrator / DefaultRagOrchestrator
│       │   ├── pipeline/     # 管道框架 + 11 个阶段
│       │   │   └── stage/    # 各阶段实现
│       │   └── strategy/     # 检索/增强/处理策略
│       ├── repository/       # JPA Repository
│       ├── rerank/           # MMR 重排序
│       ├── resilience/       # 熔断/重试/超时辅助 (ResilienceHelper)
│       ├── retrieval/        # 检索器 (BasicContentRetriever, HybirdContentRetriever, Bm25Indexer, RetrievalOptimizer)
│       ├── security/         # JWT 认证 (JwtUtil, JwtAuthenticationFilter)
│       ├── service/          # 业务服务 (KnowledgeQAService, KnowledgeEmbeddingService, DocumentService)
│       └── splitter/         # 文档分块 (RecursiveCharacterTextSplitter, TokenContentSplitter, SentencesSplitter)
├── model/                    # 数据模型 / JPA 实体 / 枚举
├── starter/                  # 启动模块 / 配置文件 / DB schema / Prometheus 规则
├── doc/                      # 设计文档
├── deploy/                   # Nginx / Docker 部署配置
├── grafana/                  # 预配置仪表盘和数据源
├── docker-compose.yml        # 基础设施编排
├── docker-compose.logging.yml # ELK 日志栈
├── docker-compose.dingtalk.yml # 钉钉告警集成
└── pom.xml                   # 父 POM（多模块：api / core / model / starter）
```

---

## 技术栈

| 类别 | 技术 |
|------|------|
| 框架 | Spring Boot 3.5, Spring AI 1.1.7, Spring Security, Spring Data JPA |
| AI 服务 | DashScope Qwen-Max (LLM), text-embedding-v4 (Embedding) |
| 向量数据库 | Milvus 2.3 (MilvusServiceClient) |
| 存储 | MySQL 8.0, Redis 7 (Lettuce) |
| 可观测性 | Micrometer + Prometheus + Grafana, Zipkin (Brave), ELK (Filebeat + ES + Kibana) |
| 容错 | Resilience4j (CircuitBreaker + Retry + TimeLimiter), Bucket4j (限流) |
| 文档处理 | Spring AI PDF Reader, 自定义递归字符分割器 |
| 部署 | Docker, Docker Compose, Nginx |

---

## 设计模式

| 模式 | 说明 |
|------|------|
| **管道模式** | `RagPipeline` 按序执行 `PipelineStage` 列表，支持条件跳过和耗时统计 |
| **策略模式** | `RetrievalStrategy` / `QueryEnhancementStrategy` / `DocumentProcessingStrategy` 可插拔 |
| **编排器模式** | `RagOrchestrator` 协调横切关注点（缓存、记忆、评估） |
| **模板方法** | `AbstractRagFlow` 定义骨架，子类只需配置管道和编排器 |
| **适配器模式** | `DocumentLoader` / `ContentSplitter` 统一文档处理接口 |

---

## 文档索引

- [项目介绍](doc/项目介绍.md) — 项目全景概述
- [概要设计](doc/概要设计.md) — 系统架构设计
- [详细设计](doc/详细设计.md) — 模块详细设计
- [部署指南](doc/部署指南.md) — 生产部署指南

---

**版本**: v1.0.0 | **最后更新**: 2026-06-14
