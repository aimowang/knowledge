package org.example.core.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "spring.ai.vectorstore.milvus")
public class MilvusVectorStoreProperties {

    private String host = "localhost";

    private int port = 19530;

    private String collectionName = "ai_knowledge";

    private int embeddingDimension = 1536;

    private String username;

    private String password;

    private boolean autoCreateCollection = true;
}
