package org.example.core.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * Resilience4j 工具类
 * 提供熔断、重试、超时的一站式封装
 */
@Slf4j
@Component
public class ResilienceHelper {

    private final CircuitBreaker llmCircuitBreaker;
    private final CircuitBreaker vectorSearchCircuitBreaker;
    private final Retry llmRetry;
    private final Retry vectorSearchRetry;
    private final TimeLimiter llmTimeLimiter;
    private final TimeLimiter vectorSearchTimeLimiter;

    public ResilienceHelper(CircuitBreaker llmCircuitBreaker,
                           CircuitBreaker vectorSearchCircuitBreaker,
                           Retry llmRetry,
                           Retry vectorSearchRetry,
                           TimeLimiter llmTimeLimiter,
                           TimeLimiter vectorSearchTimeLimiter) {
        this.llmCircuitBreaker = llmCircuitBreaker;
        this.vectorSearchCircuitBreaker = vectorSearchCircuitBreaker;
        this.llmRetry = llmRetry;
        this.vectorSearchRetry = vectorSearchRetry;
        this.llmTimeLimiter = llmTimeLimiter;
        this.vectorSearchTimeLimiter = vectorSearchTimeLimiter;
    }

    /**
     * 执行 LLM 调用（带熔断、重试、超时）
     * @param supplier LLM 调用逻辑
     * @param fallback 降级逻辑（熔断或超时时触发）
     * @return 执行结果
     */
    public <T> T executeWithLlmResilience(Supplier<T> supplier, Supplier<T> fallback) {
        // 1. 包装重试
        Supplier<T> retrySupplier = Retry.decorateSupplier(llmRetry, supplier);
        
        // 2. 包装熔断
        Supplier<T> circuitBreakerSupplier = CircuitBreaker.decorateSupplier(llmCircuitBreaker, retrySupplier);
        
        // 3. 包装超时（异步）
        try {
            return TimeLimiter.decorateFutureSupplier(
                llmTimeLimiter, 
                () -> CompletableFuture.supplyAsync(circuitBreakerSupplier)
            ).call();
        } catch (Exception e) {
            log.warn("LLM 调用失败，触发降级逻辑: {}", e.getMessage());
            return fallback.get();
        }
    }

    /**
     * 执行向量搜索（带熔断、重试、超时）
     * @param supplier 向量搜索逻辑
     * @param fallback 降级逻辑
     * @return 执行结果
     */
    public <T> T executeWithVectorSearchResilience(Supplier<T> supplier, Supplier<T> fallback) {
        Supplier<T> retrySupplier = Retry.decorateSupplier(vectorSearchRetry, supplier);
        Supplier<T> circuitBreakerSupplier = CircuitBreaker.decorateSupplier(vectorSearchCircuitBreaker, retrySupplier);
        
        try {
            return TimeLimiter.decorateFutureSupplier(
                vectorSearchTimeLimiter,
                () -> CompletableFuture.supplyAsync(circuitBreakerSupplier)
            ).call();
        } catch (Exception e) {
            log.warn("向量搜索失败，触发降级逻辑: {}", e.getMessage());
            return fallback.get();
        }
    }

    /**
     * 获取 LLM 熔断器状态
     */
    public String getLlmCircuitBreakerState() {
        return llmCircuitBreaker.getState().name();
    }

    /**
     * 获取向量搜索熔断器状态
     */
    public String getVectorSearchCircuitBreakerState() {
        return vectorSearchCircuitBreaker.getState().name();
    }
}
