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

    private String keywords;

    private Integer importance;

    private Integer accessCount;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime lastAccessedAt;
}
