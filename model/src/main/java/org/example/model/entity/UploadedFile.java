package org.example.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 上传文件记录实体
 */
@Data
@Entity
@Table(name = "uploaded_files")
public class UploadedFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 原始文件名 */
    @Column(nullable = false)
    private String originalName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件类型（后缀） */
    private String fileType;

    /** 文件存储路径 */
    private String storagePath;

    /** 上传用户 */
    @Column(nullable = false)
    private String uploadedBy;

    /** 状态: PROCESSING / COMPLETED / FAILED */
    @Column(nullable = false)
    private String status = "COMPLETED";

    /** 错误信息 */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
