package org.example.core.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VectorStoreConfig {

    @Bean
    public MilvusServiceClient milvusClient(@Value("${spring.ai.vectorstore.milvus.host}") String host,
                                            @Value("${spring.ai.vectorstore.milvus.port}") Integer port) {
        ConnectParam param = ConnectParam.newBuilder()
                .withHost(host).withPort(port).build();
        return new MilvusServiceClient(param);
    }

    @Bean
    public MilvusVectorStore vectorStore(MilvusServiceClient client, EmbeddingModel embedding,
                                         @Value("${spring.ai.vectorstore.milvus.embedding-dimension}") Integer embeddingDimension,
                                         @Value("${spring.ai.vectorstore.milvus.collection-name}") String collectionName,
                                         @Value("${spring.ai.vectorstore.milvus.embedding-field}") String fieldName) {
        return MilvusVectorStore.builder(client, embedding)
                .collectionName(collectionName)
                .embeddingDimension(embeddingDimension)
                .autoId(true)
                .embeddingFieldName(fieldName)
                // todo: 首次执行时初始化向量库结构
                .initializeSchema(true)
                .build();
    }

    /**
     * 记忆向量存储 - 用于长期记忆的语义检索。
     * 使用独立集合 ai_memory_vectors。
     */
    @Bean
    public MilvusVectorStore memoryVectorStore(MilvusServiceClient client, EmbeddingModel embedding,
                                               @Value("${spring.ai.vectorstore.milvus.embedding-dimension}") Integer dimension) {
        return MilvusVectorStore.builder(client, embedding)
                .collectionName("ai_memory_vectors")
                .embeddingDimension(dimension)
                .autoId(false)
                .embeddingFieldName("vector")
                .initializeSchema(true)
                .build();
    }
}