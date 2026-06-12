package org.example.core.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Resilience4j 配置类
 * 用于管理熔断器、重试和时间限制器
 */
@Slf4j
@Configuration
public class Resilience4jConfig {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final TimeLimiterRegistry timeLimiterRegistry;

    public Resilience4jConfig(CircuitBreakerRegistry circuitBreakerRegistry,
                              RetryRegistry retryRegistry,
                              TimeLimiterRegistry timeLimiterRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
        this.timeLimiterRegistry = timeLimiterRegistry;
    }

    /**
     * 获取 LLM 调用的熔断器
     */
    @Bean
    public io.github.resilience4j.circuitbreaker.CircuitBreaker llmCircuitBreaker() {
        return circuitBreakerRegistry.circuitBreaker("llmCall");
    }

    /**
     * 获取向量搜索的熔断器
     */
    @Bean
    public io.github.resilience4j.circuitbreaker.CircuitBreaker vectorSearchCircuitBreaker() {
        return circuitBreakerRegistry.circuitBreaker("vectorSearch");
    }

    /**
     * 获取 LLM 调用的重试机制
     */
    @Bean
    public io.github.resilience4j.retry.Retry llmRetry() {
        return retryRegistry.retry("llmCall");
    }

    /**
     * 获取向量搜索的重试机制
     */
    @Bean
    public io.github.resilience4j.retry.Retry vectorSearchRetry() {
        return retryRegistry.retry("vectorSearch");
    }

    /**
     * 获取 LLM 调用的时间限制器
     */
    @Bean
    public io.github.resilience4j.timelimiter.TimeLimiter llmTimeLimiter() {
        return timeLimiterRegistry.timeLimiter("llmCall");
    }

    /**
     * 获取向量搜索的时间限制器
     */
    @Bean
    public io.github.resilience4j.timelimiter.TimeLimiter vectorSearchTimeLimiter() {
        return timeLimiterRegistry.timeLimiter("vectorSearch");
    }
}
