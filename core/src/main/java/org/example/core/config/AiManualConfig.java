package org.example.core.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.embedding.text.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.text.DashScopeEmbeddingOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.MetadataMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Spring AI 手动配置类。
 * spring-ai-alibaba-starter 1.0.0-M6.1 与 Spring Boot 3.5.0 不完全兼容，
 * 自动配置中的 DashScopeAutoConfiguration 未被触发，此处手动创建所需 Bean。
 */
@Configuration
public class AiManualConfig {

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    @Value("${spring.ai.dashscope.base-url:https://dashscope.aliyuncs.com}")
    private String baseUrl;

    @Value("${spring.ai.dashscope.model:deepseek-v4-flush}")
    private String model;

    @Bean
    @ConditionalOnMissingBean
    public ResponseErrorHandler responseErrorHandler() {
        return new DefaultResponseErrorHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public DashScopeApi dashScopeApi(RestClient.Builder restClientBuilder,
                                     WebClient.Builder webClientBuilder,
                                     ResponseErrorHandler responseErrorHandler) {
        return DashScopeApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .restClientBuilder(restClientBuilder)
                .webClientBuilder(webClientBuilder)
                .responseErrorHandler(responseErrorHandler)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public DashScopeEmbeddingModel dashScopeEmbeddingModel(DashScopeApi dashScopeApi) {
        DashScopeEmbeddingOptions options = DashScopeEmbeddingOptions.builder()
                .model("text-embedding-v4")
                .dimensions(1536)
                .build();
        return new DashScopeEmbeddingModel(dashScopeApi, MetadataMode.EMBED, options);
    }


    @Bean
    public ChatClient chatClient(DashScopeChatModel chatModel) {
        return ChatClient.create(chatModel);
    }
}
