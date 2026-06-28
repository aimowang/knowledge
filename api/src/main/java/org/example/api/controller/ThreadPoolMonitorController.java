package org.example.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 线程池监控控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/monitor")
@Tag(name = "监控管理", description = "线程池状态和系统监控")
public class ThreadPoolMonitorController {

    private final ThreadPoolTaskExecutor ragRetrievalExecutor;
    private final ThreadPoolTaskExecutor llmCallExecutor;

    public ThreadPoolMonitorController(ThreadPoolTaskExecutor ragRetrievalExecutor,
                                      ThreadPoolTaskExecutor llmCallExecutor) {
        this.ragRetrievalExecutor = ragRetrievalExecutor;
        this.llmCallExecutor = llmCallExecutor;
    }

    /**
     * 获取线程池状态
     */
    @GetMapping("/threadpool")
    public Map<String, Object> getThreadPoolStatus() {
        Map<String, Object> status = new HashMap<>();
        
        // RAG 检索线程池
        status.put("ragRetrieval", getExecutorInfo("RAG检索", ragRetrievalExecutor));
        
        // LLM 调用线程池
        status.put("llmCall", getExecutorInfo("LLM调用", llmCallExecutor));
        
        return status;
    }

    private Map<String, Object> getExecutorInfo(String name, ThreadPoolTaskExecutor executor) {
        Map<String, Object> info = new HashMap<>();
        info.put("name", name);
        info.put("corePoolSize", executor.getCorePoolSize());
        info.put("maxPoolSize", executor.getMaxPoolSize());
        info.put("poolSize", executor.getThreadPoolExecutor().getPoolSize());
        info.put("activeCount", executor.getThreadPoolExecutor().getActiveCount());
        info.put("queueSize", executor.getThreadPoolExecutor().getQueue().size());
        info.put("queueRemainingCapacity", executor.getThreadPoolExecutor().getQueue().remainingCapacity());
        info.put("completedTaskCount", executor.getThreadPoolExecutor().getCompletedTaskCount());
        info.put("taskCount", executor.getThreadPoolExecutor().getTaskCount());
        
        // 计算使用率
        double usageRate = executor.getThreadPoolExecutor().getActiveCount() * 100.0 / executor.getMaxPoolSize();
        info.put("usageRate", String.format("%.2f%%", usageRate));
        
        return info;
    }
}
