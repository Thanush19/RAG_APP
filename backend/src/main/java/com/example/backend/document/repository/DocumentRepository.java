package com.example.backend.document.repository;

import com.example.backend.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Document> findByIdAndUserId(UUID documentId, UUID userId);
}