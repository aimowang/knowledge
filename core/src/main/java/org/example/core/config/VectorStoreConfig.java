package org.example.core.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;

//@Configuration
public class VectorStoreConfig {
    @Autowired
    private MilvusVectorStoreProperties vectorStoreProperties;

    @Bean
    public MilvusServiceClient milvusClient() {
        ConnectParam param = ConnectParam.newBuilder()
                .withHost(vectorStoreProperties.getHost()).withPort(vectorStoreProperties.getPort()).build();
        return new MilvusServiceClient(param);
    }

    @Bean
    public MilvusVectorStore vectorStore(MilvusServiceClient client, EmbeddingModel embedding) {
        return MilvusVectorStore.builder(client, embedding).build();
    }
}
