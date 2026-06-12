package org.example.core.repository;

import org.example.model.entity.LongTermMemoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LongTermMemoryRepository extends JpaRepository<LongTermMemoryEntity, String> {
    List<LongTermMemoryEntity> findByUserId(String userId);
    void deleteByUserId(String userId);
}
