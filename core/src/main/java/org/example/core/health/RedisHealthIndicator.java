package org.example.core.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 健康检查器
 */
@Slf4j
@Component("redisHealth")
public class RedisHealthIndicator implements HealthIndicator {

    private final RedisTemplate<String, String> redisTemplate;

    public RedisHealthIndicator(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Health health() {
        try {
            // 尝试 ping Redis
            Boolean result = redisTemplate.hasKey("health:check");
            // 即使 key 不存在，只要能连接就算健康
            return Health.up()
                    .withDetail("status", "connected")
                    .withDetail("message", "Redis 连接正常")
                    .build();
        } catch (Exception e) {
            log.error("Redis 健康检查失败", e);
            return Health.down()
                    .withDetail("status", "disconnected")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
