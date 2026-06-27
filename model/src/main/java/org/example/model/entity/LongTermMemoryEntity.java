package org.example.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "long_term_memories")
public class LongTermMemoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String type; // FACT, PREFERENCE, CONTEXT

    @Column(columnDefinition = "TEXT")
    private String content;

    /** 内容摘要（用于列表展示，长度300字符） */
    @Column(length = 300)
    private String summary;

    private String keywords;

    private Integer importance;

    private Integer accessCount;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime lastAccessedAt;
}
