package org.example.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "rag_evaluations")
public class RagEvaluationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column(columnDefinition = "TEXT")
    private String question;

    @Column(columnDefinition = "TEXT")
    private String answer;

    @Column(columnDefinition = "TEXT")
    private String context;

    @Column(columnDefinition = "TEXT")
    private String groundTruth;

    private Double answerRelevance;
    private Double faithfulness;
    private Double contextRelevance;
    private Double contextPrecision;
    private Double contextRecall;
    private Double overallScore;

    @CreationTimestamp
    private LocalDateTime evaluatedAt;
}
