package org.example.api.controller;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.core.service.KnowledgeEmbeddingService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/chat")
public class ApiController {

    @Resource
    private KnowledgeEmbeddingService knowledgeEmbeddingService;

    @PostMapping("/upload")
    public void upload(@RequestParam("file") MultipartFile file) throws IOException {
        // 获取原始文件名和后缀
        String originalName = file.getOriginalFilename();
        String suffix = "";
        if (originalName != null && originalName.contains(".")) {
            suffix = originalName.substring(originalName.lastIndexOf("."));
        }
        // 保存到临时文件（清除非文件名字符）
        Path tempFile = Files.createTempFile("upload_", suffix);
        try {
            file.transferTo(tempFile.toFile());
            log.info("文件上传成功: {} -> {}", originalName, tempFile);
            knowledgeEmbeddingService.embedding(List.of(tempFile.toFile()));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
