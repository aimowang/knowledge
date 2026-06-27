package org.example.core.rag.agentic.trajectory;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Agent 执行轨迹 JPA 实体。
 *
 * <p>存储每次 Agent 执行的完整决策路径，支持回放和分析。
 * 轨迹数据保留 30 天后可归档（通过 {@code agentic-rag.trajectory.retention-days} 配置）。
 */
@Entity
@Table(name = "agent_trajectories",
       indexes = {
           @Index(name = "idx_user_id", columnList = "userId"),
           @Index(name = "idx_status", columnList = "status"),
           @Index(name = "idx_created_at", columnList = "createdAt")
       })
public class TrajectoryEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 100)
    private String userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String query;

    @Column(nullable = false, columnDefinition = "JSON")
    private String trajectoryJson;

    @Column(nullable = false)
    private int totalSteps;

    @Column(nullable = false)
    private int totalLoops;

    @Column(nullable = false)
    private long totalDurationMs;

    @Column(columnDefinition = "JSON")
    private String toolsUsed;

    @Column(columnDefinition = "JSON")
    private String qualityScores;

    @Column(length = 20, nullable = false)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private boolean archived;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ── Getters / Setters ──

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public String getTrajectoryJson() { return trajectoryJson; }
    public void setTrajectoryJson(String trajectoryJson) { this.trajectoryJson = trajectoryJson; }

    public int getTotalSteps() { return totalSteps; }
    public void setTotalSteps(int totalSteps) { this.totalSteps = totalSteps; }

    public int getTotalLoops() { return totalLoops; }
    public void setTotalLoops(int totalLoops) { this.totalLoops = totalLoops; }

    public long getTotalDurationMs() { return totalDurationMs; }
    public void setTotalDurationMs(long totalDurationMs) { this.totalDurationMs = totalDurationMs; }

    public String getToolsUsed() { return toolsUsed; }
    public void setToolsUsed(String toolsUsed) { this.toolsUsed = toolsUsed; }

    public String getQualityScores() { return qualityScores; }
    public void setQualityScores(String qualityScores) { this.qualityScores = qualityScores; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
}
