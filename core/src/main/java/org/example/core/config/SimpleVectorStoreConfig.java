package org.example.core.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.File;
import java.io.IOException;

/**
 * SimpleVectorStore 配置类
 * 使用内存向量库，适合开发和测试环境
 * 支持持久化到文件
 */
@Configuration
public class SimpleVectorStoreConfig {

    /**
     * 创建 SimpleVectorStore Bean
     * 使用 @Primary 注解，使其成为默认的 VectorStore 实现
     */
    @Bean
    @Primary
    public SimpleVectorStore vectorStore(EmbeddingModel embeddingModel) throws IOException {
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        
        // 持久化文件路径
        File persistFile = new File("vector-store.json");
        
        // 如果文件存在，加载已有数据
        if (persistFile.exists()) {
            vectorStore.load(persistFile);
        } else {
            // 首次启动创建空文件
            persistFile.createNewFile();
        }
        
        // 注册关闭钩子，项目停止时自动保存数据
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            vectorStore.save(persistFile);
        }));
        
        return vectorStore;
    }
}
