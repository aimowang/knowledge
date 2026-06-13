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
                                         @Value("${spring.ai.vectorstore.milvus.collection-name}") String collectionName) {
        return MilvusVectorStore.builder(client, embedding)
                .collectionName(collectionName)
                .embeddingDimension(embeddingDimension)
                .build();
    }
}
