package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAgentAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAudioSpeechAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAudioTranscriptionAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeEmbeddingAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeImageAutoConfiguration;

@SpringBootApplication(exclude = {
        DashScopeAgentAutoConfiguration.class,
        DashScopeAudioSpeechAutoConfiguration.class,
        DashScopeAudioTranscriptionAutoConfiguration.class,
        DashScopeEmbeddingAutoConfiguration.class,
        DashScopeImageAutoConfiguration.class,
})
@EnableScheduling  // 启用定时任务
public class ApplicationStarter {

    public static void main(String[] args) {
        SpringApplication.run(ApplicationStarter.class, args);
    }
}
