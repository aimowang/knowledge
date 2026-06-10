package org.example.core.rag.handler;

import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Service
public class ExternalSearchService {
    @Resource
    private RestClient restClient;
    // 调用 Bing / Tavily API
    public List<Document> search(String query) {
        return new ArrayList<>();
    }
}
