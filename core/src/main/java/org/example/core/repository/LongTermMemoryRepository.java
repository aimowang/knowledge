package org.example.core.repository;

import org.example.model.entity.LongTermMemoryEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LongTermMemoryRepository extends JpaRepository<LongTermMemoryEntity, String> {

    /** 获取用户的所有记忆（无分页） */
    List<LongTermMemoryEntity> findByUserId(String userId);

    /** 获取用户的记忆（分页，按重要性+最近访问降序） */
    @Query("SELECT m FROM LongTermMemoryEntity m WHERE m.userId = :userId ORDER BY m.importance DESC, m.lastAccessedAt DESC")
    List<LongTermMemoryEntity> findByUserId(@Param("userId") String userId, Pageable pageable);

    /** 统计用户的记忆数量 */
    long countByUserId(String userId);

    void deleteByUserId(String userId);
}
