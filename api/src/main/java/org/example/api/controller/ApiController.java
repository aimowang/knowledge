package org.example.api.controller;

import jakarta.annotation.Resource;
import org.example.core.service.KnowledgeEmbeddingService;
import org.example.core.service.KnowledgeQAService;
import org.example.model.QuestionRequest;
import org.springframework.http.ResponseEntity;
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
    @Resource
    private KnowledgeQAService qaService;

    @PostMapping("/ask")
    public ResponseEntity<String> ask(@RequestBody QuestionRequest request) {
        return ResponseEntity.ok(qaService.ask(request.getQuestion()));
    }

    @PostMapping("/upload")
    public void upload(MultipartFile file) throws IOException {
        knowledgeEmbeddingService.embedding(List.of(file.getResource().getFile()));
    }
}
