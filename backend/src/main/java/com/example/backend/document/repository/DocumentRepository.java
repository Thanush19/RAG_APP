package com.example.backend.document.repository;

import com.example.backend.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository
        extends JpaRepository<Document, UUID> {

    List<Document> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Document> findByIdAndUserId(
            UUID documentId,
            UUID userId
    );

    @Query("""
            SELECT COALESCE(SUM(d.fileSize), 0)
            FROM Document d
            WHERE d.user.id = :userId
            """)
    long getTotalStorageUsedByUser(
            @Param("userId") UUID userId
    );
}