package org.example.api.controller;

import jakarta.annotation.Resource;
import org.example.core.service.KnowledgeEmbeddingService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/chat")
public class ApiController {

    @Resource
    private KnowledgeEmbeddingService knowledgeEmbeddingService;

    @PostMapping("/upload")
    public void upload(MultipartFile file) throws IOException {
        knowledgeEmbeddingService.embedding(List.of(file.getResource().getFile()));
    }
}
