package com.example.backend.document.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "document_chunks",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_document_chunks_document_index",
                        columnNames = {"document_id", "chunk_index"}
                )
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "qdrant_point_id")
    private UUID qdrantPointId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}