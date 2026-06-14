package org.example.core.repository;

import org.example.model.entity.UploadedFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UploadedFileRepository extends JpaRepository<UploadedFile, Long> {
    List<UploadedFile> findAllByOrderByCreatedAtDesc();
    List<UploadedFile> findByUploadedByOrderByCreatedAtDesc(String uploadedBy);
    List<UploadedFile> findByStatusOrderByCreatedAtDesc(String status);
    List<UploadedFile> findByStatusAndUploadedByOrderByCreatedAtDesc(String status, String uploadedBy);
}
