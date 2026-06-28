package org.example.api.controller;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.core.service.DocumentService;
import org.example.model.entity.UploadedFile;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 文档管理接口 — 仅负责 HTTP 通信，业务逻辑委托给 DocumentService
 */
@Slf4j
@RestController
@RequestMapping("/api/documents")
@Tag(name = "文档管理", description = "上传和删除知识库文档")
public class DocumentController {

    @Resource
    private DocumentService documentService;

    /**
     * 获取所有已上传文件列表
     */
    @GetMapping("/list")
    public List<UploadedFile> listFiles(@RequestParam(required = false) String username) {
        return documentService.listAll(username);
    }

    /**
     * 获取向量入库成功的文件列表
     */
    @GetMapping("/success")
    public List<UploadedFile> listSuccessFiles(@RequestParam(required = false) String username) {
        return documentService.listSuccess(username);
    }

    /**
     * 上传并处理文档
     * 文件 -> 磁盘 -> 向量库 -> 成功后写入 MySQL
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "username", defaultValue = "anonymous") String username) {

        DocumentService.UploadResult result = documentService.upload(file, username);

        if (!result.success()) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", result.message()
            ));
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", result.message(),
                "id", result.id(),
                "status", result.status()
        ));
    }

    /**
     * 删除文件记录和物理文件
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        boolean deleted = documentService.delete(id);
        if (deleted) {
            return ResponseEntity.ok(Map.of("success", true, "message", "删除成功"));
        }
        return ResponseEntity.notFound().build();
    }
}
