package org.example.core.repository;

import org.example.model.entity.RagEvaluationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RagEvaluationRepository extends JpaRepository<RagEvaluationEntity, String> {
    List<RagEvaluationEntity> findByUserId(String userId);
    void deleteByUserId(String userId);
    
    /**
     * 获取全局统计信息（高性能 SQL 聚合）
     */
    @Query("SELECT COUNT(DISTINCT e.userId), COUNT(e), AVG(e.overallScore) FROM RagEvaluationEntity e")
    Object[] getGlobalStatistics();
}
