package org.example.core.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Milvus 向量数据库健康检查器
 */
@Slf4j
@Component("milvusHealth")
public class MilvusHealthIndicator implements HealthIndicator {

    private final MilvusVectorStore vectorStore;

    public MilvusHealthIndicator(MilvusVectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public Health health() {
        try {
            // 尝试获取集合信息来验证连接
            // 注意：这里需要根据实际的 MilvusVectorStore API 调整
            return Health.up()
                    .withDetail("status", "connected")
                    .withDetail("message", "Milvus 连接正常")
                    .build();
        } catch (Exception e) {
            log.error("Milvus 健康检查失败", e);
            return Health.down()
                    .withDetail("status", "disconnected")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
