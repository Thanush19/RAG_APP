package com.example.backend.document.service;

import com.example.backend.common.exception.EntityNotFoundException;
import com.example.backend.document.dto.ChunkData;
import com.example.backend.document.dto.ExtractedPage;
import com.example.backend.document.entity.Document;
import com.example.backend.document.entity.DocumentChunk;
import com.example.backend.document.entity.DocumentStatus;
import com.example.backend.document.repository.DocumentChunkRepository;
import com.example.backend.document.repository.DocumentRepository;
import com.example.backend.rag.embedding.EmbeddingService;
import com.example.backend.rag.vector.VectorStoreService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private static final Logger log =
            LoggerFactory.getLogger(DocumentIngestionService.class);

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final PdfTextExtractionService pdfTextExtractionService;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;

    @Transactional
    public void ingest(UUID userId, UUID documentId) {

        Document document = documentRepository
                .findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Document not found"
                ));

        // Idempotent re-ingestion: clear any previous chunk rows and
        // Qdrant points so retries never duplicate or leave stale vectors.
        documentChunkRepository.deleteAllByDocumentId(documentId);

        vectorStoreService.deleteDocumentVectors(userId, documentId);

        List<ExtractedPage> pages =
                pdfTextExtractionService.extract(document);

        List<ChunkData> chunkData =
                chunkingService.chunk(pages);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        List<DocumentChunk> chunks = new ArrayList<>();

        for (int i = 0; i < chunkData.size(); i++) {

            ChunkData data = chunkData.get(i);

            DocumentChunk chunk = DocumentChunk.builder()
                    .id(UUID.randomUUID())
                    .document(document)
                    .chunkIndex(i)
                    .pageNumber(data.pageNumber())
                    .text(data.text())
                    .createdAt(now)
                    .build();

            DocumentChunk savedChunk =
                    documentChunkRepository.save(chunk);

            float[] embedding =
                    embeddingService.embedPassage(data.text());

            log.debug(
                    "Generated embedding for chunk {} of document {} with dimension {}",
                    i,
                    documentId,
                    embedding.length
            );

            vectorStoreService.storeChunk(
                    userId,
                    documentId,
                    savedChunk.getId(),
                    data.pageNumber(),
                    embedding
            );

            savedChunk.setQdrantPointId(savedChunk.getId());

            chunks.add(savedChunk);
        }

        document.setStatus(DocumentStatus.READY);
        document.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        log.info(
                "Ingestion completed for document {}: {} chunks",
                documentId,
                chunks.size()
        );
    }
}
