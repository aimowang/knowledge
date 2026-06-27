package org.example.core.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
    
    // ── 新增: Agent 指标 ──────────────────────────────────
    private final Counter agentDecisionCounter;
    private final Counter agentToolCallCounter;
    private final Counter agentRetryCounter;
    private final Timer agentToolDurationTimer;
    private final Timer agentLoopTimer;
    private final AtomicInteger agentContextCompleteGauge = new AtomicInteger(0);
    private final AtomicInteger agentReflectionIssuesGauge = new AtomicInteger(0);

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
        
        // ── Agent 指标初始化 ──
        this.agentDecisionCounter = Counter.builder("agent_decision_total")
                .description("Agent 决策总次数")
                .register(meterRegistry);

        this.agentToolCallCounter = Counter.builder("agent_tool_call_total")
                .description("各工具调用次数")
                .tag("tool", "all")
                .register(meterRegistry);

        this.agentRetryCounter = Counter.builder("agent_retry_total")
                .description("Agent 重试次数及原因")
                .tag("reason", "all")
                .register(meterRegistry);

        this.agentToolDurationTimer = Timer.builder("agent_tool_duration_seconds")
                .description("各工具调用耗时分布")
                .tag("tool", "all")
                .register(meterRegistry);

        this.agentLoopTimer = Timer.builder("agent_loop_count")
                .description("Agent 循环轮次分布")
                .register(meterRegistry);

        Gauge.builder("agent_context_complete", agentContextCompleteGauge,
                AtomicInteger::doubleValue)
                .description("上下文完备性评分")
                .register(meterRegistry);

        Gauge.builder("agent_reflection_issues", agentReflectionIssuesGauge,
                AtomicInteger::doubleValue)
                .description("自反思发现的问题数")
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

    // ════════════════════════════════════════════════════════════
    // Agent 指标方法
    // ════════════════════════════════════════════════════════════

    /** 记录 Agent 决策次数 */
    public void incrementAgentDecision() {
        agentDecisionCounter.increment();
    }

    /** 记录工具调用次数 */
    public void incrementToolCall(String toolName) {
        agentToolCallCounter.increment();
        log.debug("工具调用: {}", toolName);
    }

    /** 记录工具调用耗时 */
    public void recordToolCallDuration(String toolName, long durationMs) {
        agentToolDurationTimer.record(java.time.Duration.ofMillis(durationMs));
    }

    /** 记录循环轮次 */
    public void recordLoopCount(int loopCount) {
        agentLoopTimer.record(java.time.Duration.ofMillis(loopCount * 1000L));
    }

    /** 设置上下文完备性评分 (0~100) */
    public void setContextCompleteScore(int score) {
        agentContextCompleteGauge.set(score);
    }

    /** 设置自反思问题数 */
    public void setReflectionIssues(int count) {
        agentReflectionIssuesGauge.set(count);
    }

    /** 记录重试 */
    public void incrementRetry(String reason) {
        agentRetryCounter.increment();
        log.debug("Agent 重试: {}", reason);
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
