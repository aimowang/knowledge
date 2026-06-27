-- 创建数据库
CREATE DATABASE IF NOT EXISTS knowledge_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE knowledge_db;

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 会话表
CREATE TABLE IF NOT EXISTS sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    content TEXT COMMENT 'JSON格式的对话历史',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_access_time TIMESTAMP NOT NULL,
    INDEX idx_user_id (user_id),
    INDEX idx_last_access (last_access_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话表';

-- 长期记忆表
CREATE TABLE IF NOT EXISTS long_term_memories (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL COMMENT 'FACT/PREFERENCE/CONTEXT',
    content TEXT NOT NULL,
    keywords VARCHAR(500),
    importance INT DEFAULT 5,
    access_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_accessed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_type (type),
    INDEX idx_importance (importance),
    INDEX idx_user_type (user_id, type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='长期记忆表';

-- RAG评估结果表
CREATE TABLE IF NOT EXISTS rag_evaluations (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    question TEXT NOT NULL,
    answer TEXT NOT NULL,
    context TEXT,
    ground_truth TEXT,
    answer_relevance DOUBLE,
    faithfulness DOUBLE,
    context_relevance DOUBLE,
    context_precision DOUBLE,
    context_recall DOUBLE,
    overall_score DOUBLE,
    evaluated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_overall_score (overall_score),
    INDEX idx_evaluated_at (evaluated_at),
    INDEX idx_user_time (user_id, evaluated_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG评估结果表';

-- Refresh Token 表
CREATE TABLE IF NOT EXISTS refresh_tokens (
    token VARCHAR(500) PRIMARY KEY COMMENT 'Refresh Token',
    user_id VARCHAR(100) NOT NULL COMMENT '用户ID',
    expiry_date DATETIME NOT NULL COMMENT '过期时间',
    revoked TINYINT(1) DEFAULT 0 COMMENT '是否已撤销',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_id (user_id),
    INDEX idx_expiry_date (expiry_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Refresh Token 存储表';

-- 上传文件记录表
CREATE TABLE IF NOT EXISTS uploaded_files (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    original_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_size BIGINT COMMENT '文件大小（字节）',
    file_type VARCHAR(50) COMMENT '文件类型',
    storage_path VARCHAR(500) COMMENT '存储路径',
    uploaded_by VARCHAR(100) NOT NULL COMMENT '上传用户',
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED' COMMENT '状态: PROCESSING/COMPLETED/FAILED',
    error_message TEXT COMMENT '错误信息',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_uploaded_by (uploaded_by),
    INDEX idx_created_at (created_at),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上传文件记录表';

-- 插入默认测试用户（密码: password123，BCrypt加密）
INSERT INTO users (username, password) VALUES
('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy')
ON DUPLICATE KEY UPDATE username=username;

-- ============================================================
-- Agentic RAG 相关表
-- ============================================================

-- Agent 执行轨迹表
CREATE TABLE IF NOT EXISTS agent_trajectories (
    id VARCHAR(36) PRIMARY KEY COMMENT '轨迹唯一标识（格式: traj_yyyyMMdd_xxx）',
    user_id VARCHAR(100) NOT NULL COMMENT '用户ID',
    query TEXT NOT NULL COMMENT '用户原始查询',
    trajectory_json JSON NOT NULL COMMENT '完整执行轨迹（步骤列表 JSON）',
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

-- rag_evaluations 表扩展（新增 trajectory_id 关联）
ALTER TABLE rag_evaluations
    ADD COLUMN IF NOT EXISTS trajectory_id VARCHAR(36) NULL COMMENT '关联的Agent轨迹ID',
    ADD INDEX IF NOT EXISTS idx_trajectory_id (trajectory_id);
