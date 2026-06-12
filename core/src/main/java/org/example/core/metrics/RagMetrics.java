package org.example.core.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * RAG 自定义业务指标
 */
@Slf4j
@Component
public class RagMetrics {

    private final MeterRegistry meterRegistry;
    
    // 计数器
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;
    private final Counter llmCallCounter;
    private final Counter vectorSearchCounter;
    
    // 计时器
    private final Timer ragAnswerTimer;
    private final Timer llmCallTimer;
    private final Timer vectorSearchTimer;

    public RagMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // 初始化计数器
        this.cacheHitCounter = Counter.builder("rag_cache_hit")
                .description("RAG 缓存命中次数")
                .register(meterRegistry);
        
        this.cacheMissCounter = Counter.builder("rag_cache_miss")
                .description("RAG 缓存未命中次数")
                .register(meterRegistry);
        
        this.llmCallCounter = Counter.builder("rag_llm_call_total")
                .description("LLM 调用总次数")
                .register(meterRegistry);
        
        this.vectorSearchCounter = Counter.builder("rag_vector_search_total")
                .description("向量搜索总次数")
                .register(meterRegistry);
        
        // 初始化计时器
        this.ragAnswerTimer = Timer.builder("rag_answer_duration")
                .description("RAG 问答总耗时")
                .register(meterRegistry);
        
        this.llmCallTimer = Timer.builder("rag_llm_call_duration")
                .description("LLM 调用耗时")
                .register(meterRegistry);
        
        this.vectorSearchTimer = Timer.builder("rag_vector_search_duration")
                .description("向量搜索耗时")
                .register(meterRegistry);
    }

    /**
     * 记录缓存命中
     */
    public void recordCacheHit() {
        cacheHitCounter.increment();
        log.debug("缓存命中，当前命中数: {}", cacheHitCounter.count());
    }

    /**
     * 记录缓存未命中
     */
    public void recordCacheMiss() {
        cacheMissCounter.increment();
    }

    /**
     * 记录 LLM 调用
     */
    public void recordLlmCall() {
        llmCallCounter.increment();
    }

    /**
     * 记录向量搜索
     */
    public void recordVectorSearch() {
        vectorSearchCounter.increment();
    }

    /**
     * 记录 RAG 问答耗时
     */
    public void recordRagAnswerDuration(double seconds) {
        ragAnswerTimer.record(java.time.Duration.ofMillis((long) (seconds * 1000)));
    }

    /**
     * 记录 LLM 调用耗时
     */
    public void recordLlmCallDuration(double seconds) {
        llmCallTimer.record(java.time.Duration.ofMillis((long) (seconds * 1000)));
    }

    /**
     * 记录向量搜索耗时
     */
    public void recordVectorSearchDuration(double seconds) {
        vectorSearchTimer.record(java.time.Duration.ofMillis((long) (seconds * 1000)));
    }

    /**
     * 获取缓存命中率
     */
    public double getCacheHitRate() {
        double hits = cacheHitCounter.count();
        double misses = cacheMissCounter.count();
        double total = hits + misses;
        return total > 0 ? hits / total : 0.0;
    }
}
