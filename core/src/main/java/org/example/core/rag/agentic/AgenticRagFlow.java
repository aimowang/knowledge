package org.example.core.rag.agentic;

import lombok.extern.slf4j.Slf4j;
import org.example.core.metrics.RagMetrics;
import org.example.core.rag.AbstractRagFlow;
import org.example.core.rag.agentic.agent.AgentOrchestrator;
import org.example.core.rag.agentic.agent.AgentState;
import org.example.core.rag.orchestrator.RagOrchestrator;
import org.example.core.rag.pipeline.RagPipeline;
import org.example.model.RagAnswer;
import org.springframework.ai.document.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Agentic RAG 流程 — AbstractRagFlow 的子类。
 *
 * <p>Agentic 模式不组装 Pipeline 阶段，通过 AgentOrchestrator
 * 执行 Agent 主循环（HarnessAgent 自主推理 → Quality Pipeline 检查）。
 *
 * <p>与 AdvancedRagFlow（Workflow 模式）并存，通过 {@code agentic-rag.enabled}
 * 配置开关控制启用。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "agentic-rag.enabled", havingValue = "true", matchIfMissing = false)
public class AgenticRagFlow extends AbstractRagFlow {

    private final AgentOrchestrator orchestrator;

    public AgenticRagFlow(RagPipeline pipeline,
                          RagOrchestrator ragOrchestrator,
                          RagMetrics ragMetrics,
                          AgentOrchestrator orchestrator) {
        super(pipeline, ragOrchestrator, ragMetrics);
        this.orchestrator = orchestrator;
    }

    @Override
    protected void configurePipeline(RagPipeline pipeline) {
        // Agentic 模式不使用 Pipeline 阶段
    }

    @Override
    protected void configureOrchestrator(RagOrchestrator orchestrator) {
        // Agentic 模式使用自身的状态管理
    }

    @Override
    public RagAnswer executeRag(String question, String userId, String source) {
        log.info("Agentic RAG 执行 - 用户: {}, 查询: {}", userId, truncate(question, 100));

        long startTime = System.currentTimeMillis();
        try {
            AgentState state = orchestrator.execute(question, userId);
            RagAnswer answer = convertToRagAnswer(state);
            recordTotalDuration(startTime);

            log.info("Agentic RAG 完成 - 状态: {}, 耗时: {}ms, 循环: {}",
                state.getStatus(), state.getTotalDurationMs(), state.getLoopCount());
            return answer;

        } catch (Exception e) {
            recordTotalDuration(startTime);
            log.error("Agentic RAG 执行失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    private RagAnswer convertToRagAnswer(AgentState state) {
        RagAnswer answer = new RagAnswer();
        answer.setAnswer(state.getFinalAnswer());

        // 转换引用来源为字符串列表
        if (state.getSources() != null && !state.getSources().isEmpty()) {
            List<String> sourceList = state.getSources().stream()
                .map(this::documentToSourceString)
                .collect(Collectors.toList());
            answer.setSources(sourceList);
        }

        answer.setCategory("agentic");
        answer.setComplexity("COMPLEX");
        return answer;
    }

    private String documentToSourceString(Document doc) {
        String source = doc.getMetadata() != null
            ? (String) doc.getMetadata().getOrDefault("source",
                doc.getMetadata().getOrDefault("file_name", "unknown"))
            : "unknown";
        return String.format("[来源: %s] %s", source, truncate(doc.getText(), 200));
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
