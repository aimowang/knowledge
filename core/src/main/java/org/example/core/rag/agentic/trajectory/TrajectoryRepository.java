package org.example.core.rag.agentic.trajectory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 轨迹数据 JPA Repository。
 */
@Repository
public interface TrajectoryRepository extends JpaRepository<TrajectoryEntity, String> {

    /** 按用户查询轨迹（按时间倒序） */
    List<TrajectoryEntity> findByUserIdOrderByCreatedAtDesc(String userId);

    /** 按用户分页查询轨迹 */
    Page<TrajectoryEntity> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    /** 按状态查询 */
    List<TrajectoryEntity> findByStatus(String status);

    /** 查询指定时间前的轨迹（用于归档） */
    List<TrajectoryEntity> findByCreatedAtBefore(LocalDateTime cutoff);

    /** 统计用户轨迹数 */
    long countByUserId(String userId);
}
