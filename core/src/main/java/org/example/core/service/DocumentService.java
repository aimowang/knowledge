package org.example.core.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.core.repository.UploadedFileRepository;
import org.example.model.entity.UploadedFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Slf4j
@Service
public class DocumentService {

    private static final String UPLOAD_DIR = "uploads";

    @Resource
    private UploadedFileRepository uploadedFileRepository;

    @Resource
    private KnowledgeEmbeddingService knowledgeEmbeddingService;

    /**
     * 获取全部文件列表
     */
    public List<UploadedFile> listAll(String username) {
        if (username != null && !username.isBlank()) {
            return uploadedFileRepository.findByUploadedByOrderByCreatedAtDesc(username);
        }
        return uploadedFileRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * 获取向量入库成功的文件列表
     */
    public List<UploadedFile> listSuccess(String username) {
        if (username != null && !username.isBlank()) {
            return uploadedFileRepository.findByStatusAndUploadedByOrderByCreatedAtDesc("COMPLETED", username);
        }
        return uploadedFileRepository.findByStatusOrderByCreatedAtDesc("COMPLETED");
    }

    /**
     * 上传文件并处理向量入库，入库成功后才写入 MySQL
     */
    public UploadResult upload(MultipartFile file, String username) {
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            return UploadResult.failure("文件名不能为空");
        }

        String suffix = "";
        if (originalName.contains(".")) {
            suffix = originalName.substring(originalName.lastIndexOf("."));
        }

        try {
            Files.createDirectories(Paths.get(UPLOAD_DIR));
        } catch (IOException e) {
            log.error("无法创建上传目录", e);
            return UploadResult.failure("服务器内部错误");
        }

        String storageName = System.currentTimeMillis() + "_" + originalName;
        Path targetPath = Paths.get(UPLOAD_DIR, storageName);

        try {
            // ① 保存文件到磁盘
            try (var is = file.getInputStream()) {
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
            log.info("文件保存成功: {} -> {}", originalName, targetPath);

            // ② 向量数据库插入
            try {
                knowledgeEmbeddingService.embedding(List.of(targetPath.toFile()));
            } catch (Exception e) {
                Files.deleteIfExists(targetPath);
                log.error("文件向量化失败: {}", originalName, e);
                return UploadResult.failure("文件向量化失败: " + e.getMessage());
            }

            // ③ 向量入库成功 → 写入 MySQL
            UploadedFile record = new UploadedFile();
            record.setOriginalName(originalName);
            record.setFileSize(file.getSize());
            record.setFileType(suffix.toLowerCase().replace(".", ""));
            record.setStoragePath(targetPath.toString());
            record.setUploadedBy(username);
            record.setStatus("COMPLETED");
            uploadedFileRepository.save(record);

            log.info("文件上传成功并已入库: {}，ID={}", originalName, record.getId());

            return UploadResult.success("上传并入库成功", record.getId());

        } catch (IOException e) {
            log.error("文件上传失败: {}", originalName, e);
            try { Files.deleteIfExists(targetPath); } catch (IOException ignored) {}
            return UploadResult.failure("文件保存失败: " + e.getMessage());
        }
    }

    /**
     * 删除文件记录及物理文件
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long id) {
        return uploadedFileRepository.findById(id)
                .map(record -> {
                    try {
                        Path path = Paths.get(record.getStoragePath());
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        log.warn("删除物理文件失败: {}", record.getStoragePath(), e);
                    }
                    uploadedFileRepository.delete(record);
                    return true;
                })
                .orElse(false);
    }

    // ===== 内部结果类 =====

    public record UploadResult(boolean success, String message, Long id, String status) {
        public static UploadResult success(String message, Long id) {
            return new UploadResult(true, message, id, "COMPLETED");
        }

        public static UploadResult failure(String message) {
            return new UploadResult(false, message, null, null);
        }
    }
}
