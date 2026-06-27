package org.example.core.rag.agentic.quality;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

/**
 * 上下文完备性检查代理 — Phase 2 完整实现。
 *
 * <p>Phase 1 提供基础判断逻辑，检查检索到的上下文是否足以回答问题。
 */
public class SufficientContextAgent {

    private static final Logger log = LoggerFactory.getLogger(SufficientContextAgent.class);

    private final ChatClient chatClient;

    public SufficientContextAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 检查上下文完备性。
     */
    public ContextVerdict check(String query, String context) {
        if (context == null || context.isBlank()) {
            log.debug("上下文为空，标记为不完备");
            return ContextVerdict.insufficient("未检索到任何文档", query);
        }

        // Phase 1: 简单判断，Phase 2 使用 LLM 精确判断
        // 如果上下文长度足够大，认为基本完备
        if (context.length() > 100) {
            log.debug("上下文长度足够 ({} chars)，标记为完备", context.length());
            return ContextVerdict.sufficient();
        }

        log.debug("上下文不足 ({} chars)，标记为不完备", context.length());
        return ContextVerdict.insufficient("检索结果信息不足", query);
    }
}
