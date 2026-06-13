package org.example.core.rag.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 外部搜索服务
 * 支持 Tavily 和 Bing Search API
 */
@Slf4j
@Service
public class ExternalSearchService {

    private final RestClient restClient;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExternalSearchService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Value("${external.search.api-key:}")
    private String apiKey;
    
    @Value("${external.search.provider:tavily}")
    private String provider;  // tavily 或 bing
    
    @Value("${external.search.tavily.url:https://api.tavily.com/search}")
    private String tavilyUrl;
    
    @Value("${external.search.bing.url:https://api.bing.microsoft.com/v7.0/search}")
    private String bingUrl;

    /**
     * 执行外部搜索
     * @param query 搜索查询
     * @return 搜索结果文档列表
     */
    public List<Document> search(String query) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("未配置外部搜索 API Key，返回空结果");
            return new ArrayList<>();
        }
        
        try {
            return switch (provider.toLowerCase()) {
                case "bing" -> searchWithBing(query);
                case "tavily" -> searchWithTavily(query);
                default -> {
                    log.warn("未知的搜索提供商: {}，使用 Tavily", provider);
                    yield searchWithTavily(query);
                }
            };
        } catch (Exception e) {
            log.error("外部搜索失败: {}", query, e);
            return new ArrayList<>();
        }
    }

    /**
     * 使用 Tavily API 搜索
     * Tavily 是专门为 RAG 优化的搜索引擎
     */
    private List<Document> searchWithTavily(String query) {
        log.info("使用 Tavily 搜索: {}", query);
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("query", query);
        requestBody.put("search_depth", "advanced");  // basic 或 advanced
        requestBody.put("max_results", 5);
        
        String response = restClient.post()
                .uri(tavilyUrl)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header("Authorization", "Bearer " + apiKey)
                .body(requestBody)
                .retrieve()
                .body(String.class);
        
        return parseTavilyResponse(response);
    }

    /**
     * 使用 Bing Search API 搜索
     */
    private List<Document> searchWithBing(String query) {
        log.info("使用 Bing 搜索: {}", query);
        
        String response = restClient.get()
                .uri(bingUrl + "?q={query}&count=5", query)
                .header("Ocp-Apim-Subscription-Key", apiKey)
                .retrieve()
                .body(String.class);
        
        return parseBingResponse(response);
    }

    /**
     * 解析 Tavily 响应
     */
    private List<Document> parseTavilyResponse(String jsonResponse) {
        List<Document> documents = new ArrayList<>();
        
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode results = root.get("results");
            
            if (results != null && results.isArray()) {
                for (JsonNode result : results) {
                    String content = result.path("content").asText();
                    String title = result.path("title").asText("未知标题");
                    String url = result.path("url").asText();
                    
                    if (content != null && !content.isEmpty()) {
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("title", title);
                        metadata.put("source", url);
                        metadata.put("category", "external_search");
                        
                        documents.add(new Document(content, metadata));
                    }
                }
            }
            
            log.info("Tavily 搜索返回 {} 条结果", documents.size());
        } catch (Exception e) {
            log.error("解析 Tavily 响应失败", e);
        }
        
        return documents;
    }

    /**
     * 解析 Bing 响应
     */
    private List<Document> parseBingResponse(String jsonResponse) {
        List<Document> documents = new ArrayList<>();
        
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode webPages = root.path("webPages").path("value");
            
            if (webPages.isArray()) {
                for (JsonNode page : webPages) {
                    String snippet = page.path("snippet").asText();
                    String title = page.path("name").asText("未知标题");
                    String url = page.path("url").asText();
                    
                    if (snippet != null && !snippet.isEmpty()) {
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("title", title);
                        metadata.put("source", url);
                        metadata.put("category", "external_search");
                        
                        documents.add(new Document(snippet, metadata));
                    }
                }
            }
            
            log.info("Bing 搜索返回 {} 条结果", documents.size());
        } catch (Exception e) {
            log.error("解析 Bing 响应失败", e);
        }
        
        return documents;
    }
}
