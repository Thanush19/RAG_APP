package com.example.backend.document.repository;

import com.example.backend.document.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DocumentChunkRepository
        extends JpaRepository<DocumentChunk, UUID> {

    @Query("""
            select c.id as chunkId,
                   c.document.id as documentId,
                   c.pageNumber as pageNumber,
                   c.text as text
            from DocumentChunk c
            where c.id in :chunkIds
              and c.document.user.id = :userId
            """)
    List<OwnedChunkProjection> findOwnedChunksByIds(
            @Param("userId") UUID userId,
            @Param("chunkIds") Collection<UUID> chunkIds
    );

    // Bulk JPQL delete executes immediately; a derived delete would be
    // queued in the persistence context and flushed AFTER new chunk
    // inserts, violating (document_id, chunk_index) on re-ingestion.
    @Modifying
    @Query("delete from DocumentChunk c where c.document.id = :documentId")
    void deleteAllByDocumentId(@Param("documentId") UUID documentId);

    List<DocumentChunk> findAllByDocumentId(UUID documentId);
}