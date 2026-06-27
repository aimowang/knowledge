# Knowledge Assistant — 长期记忆

_自动维护的精炼记忆。此文件由 HarnessAgent 自动更新。_

## 系统知识
- 知识库存储在 Milvus 向量数据库中
- 支持混合检索（向量 + BM25），默认返回 top-5 结果
- 提供外部搜索能力（Tavily/Bing），默认关闭

## 用户偏好（示例）
- 回答使用中文
- 使用 [N] 标记引用来源
