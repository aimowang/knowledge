package org.example.core.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;

import io.micrometer.observation.ObservationRegistry;

/**
 * AI 模型配置 — 创建两个带 Qualifier 的 ChatClient Bean。
 *
 * <p>轻量任务（分类/检查/评分/查询转换）使用 fast 模型（qwen-turbo），
 * 重量任务（答案生成）使用 full 模型（qwen-max）。
 *
 * <p>两者都通过 DashScope API，仅 model 参数不同。
 * qwen-turbo 推理速度更快、成本更低，适合不需要深度推理的轻量任务。
 */
@Configuration
public class AiModelConfig {

    @Bean("fullChatClient")
    public ChatClient fullChatClient(DashScopeChatModel chatModel) {
        return ChatClient.create(chatModel);
    }

    @Bean("fastChatClient")
    public ChatClient fastChatClient(DashScopeApi dashScopeApi,
                                      ToolCallingManager toolCallingManager,
                                      RetryTemplate retryTemplate,
                                      ObservationRegistry observationRegistry) {
        // qwen-turbo: 推理速度更快、成本更低的模型，适合轻量任务
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .withModel("qwen-turbo")
                .build();

        DashScopeChatModel fastModel = new DashScopeChatModel(
                dashScopeApi, options, toolCallingManager,
                retryTemplate, observationRegistry);
        return ChatClient.create(fastModel);
    }
}
