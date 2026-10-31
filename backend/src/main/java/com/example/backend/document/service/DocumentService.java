package com.example.backend.document.service;

import com.example.backend.auth.entity.User;
import com.example.backend.common.exception.ConflictException;
import com.example.backend.common.exception.DocumentProcessingException;
import com.example.backend.common.exception.EntityNotFoundException;
import com.example.backend.document.dto.DocumentResponse;
import com.example.backend.document.dto.ExtractedPage;
import com.example.backend.document.entity.Document;
import com.example.backend.document.entity.DocumentStatus;
import com.example.backend.document.repository.DocumentRepository;
import com.example.backend.rag.vector.VectorStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB
    private static final long MAX_USER_STORAGE = 50 * 1024 * 1024; // 50 MB

    private static final Logger log =
            LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final DocumentStorageService documentStorageService;
    private final PdfTextExtractionService pdfTextExtractionService;
    private final DocumentIngestionService documentIngestionService;
    private final VectorStoreService vectorStoreService;

    public List<DocumentResponse> getUserDocuments(UUID userId) {
        return documentRepository
                .findAllByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(DocumentResponse::from)
                .toList();
    }

    public DocumentResponse getUserDocument(
            UUID userId,
            UUID documentId
    ) {
        Document document = documentRepository
                .findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Document not found"
                ));

        return DocumentResponse.from(document);
    }

    // NOT_SUPPORTED: the class-level readOnly transaction must NOT wrap
    // this orchestration — PROCESSING/READY/FAILED transitions and chunk
    // rows must commit independently of ingestion outcome, otherwise a
    // rollback leaves PostgreSQL unchanged while Qdrant (non-transactional)
    // has already been mutated.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DocumentResponse uploadDocument(User user, MultipartFile file) {
        validateFile(file);

        long currentStorage =
                documentRepository.getTotalStorageUsedByUser(user.getId());

        long newStorage = currentStorage + file.getSize();

        if (newStorage > MAX_USER_STORAGE) {
            throw new IllegalArgumentException(
                    "Storage limit exceeded. Maximum storage per user is 50 MB.");
        }

        String storageKey = documentStorageService.upload(file);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        Document document = Document.builder()
                .id(UUID.randomUUID())
                .user(user)
                .fileName(file.getOriginalFilename())
                .fileType(file.getContentType())
                .fileSize(file.getSize())
                .storageKey(storageKey)
                .status(DocumentStatus.PROCESSING)
                .createdAt(now)
                .updatedAt(now)
                .build();

        Document savedDocument = documentRepository.save(document);

        runIngestionOrMarkFailed(
                user.getId(),
                savedDocument.getId(),
                "upload"
        );

        return DocumentResponse.from(
                documentRepository
                        .findByIdAndUserId(savedDocument.getId(), user.getId())
                        .orElseThrow(() -> new EntityNotFoundException(
                                "Document not found"
                        ))
        );
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DocumentResponse reprocessDocument(
            UUID userId,
            UUID documentId
    ) {
        Document document = documentRepository
                .findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Document not found"
                ));

        if (document.getStatus() == DocumentStatus.READY) {
            throw new ConflictException(
                    "Document is already processed"
            );
        }

        if (document.getStatus() == DocumentStatus.PROCESSING) {
            throw new ConflictException(
                    "Document is currently being processed"
            );
        }

        document.setStatus(DocumentStatus.PROCESSING);
        document.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        documentRepository.save(document);

        runIngestionOrMarkFailed(userId, documentId, "reprocess");

        return DocumentResponse.from(
                documentRepository
                        .findByIdAndUserId(documentId, userId)
                        .orElseThrow(() -> new EntityNotFoundException(
                                "Document not found"
                        ))
        );
    }

    @Transactional
    public String deleteDocumentRecord(
            UUID userId,
            UUID documentId
    ) {
        Document document = documentRepository
                .findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Document not found"
                ));

        String storageKey = document.getStorageKey();

        documentRepository.delete(document);

        try {
            vectorStoreService.deleteDocumentVectors(userId, documentId);
        } catch (Exception exception) {
            log.warn(
                    "Document {} deleted from PostgreSQL but Qdrant "
                            + "vector cleanup failed; orphaned points remain, "
                            + "unreachable via retrieval, until the "
                            + "orphan sweep removes them",
                    documentId,
                    exception
            );
        }

        return storageKey;
    }

    public List<ExtractedPage> extractDocumentText(
            UUID userId,
            UUID documentId
    ) {
        Document document = documentRepository
                .findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Document not found"
                ));

        return pdfTextExtractionService.extract(document);
    }

    public InputStream downloadDocumentBytes(
            UUID userId,
            UUID documentId
    ) {
        Document document = documentRepository
                .findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Document not found"
                ));

        return documentStorageService.download(
                document.getStorageKey()
        );
    }

    private void runIngestionOrMarkFailed(
            UUID userId,
            UUID documentId,
            String trigger
    ) {
        try {
            documentIngestionService.ingest(userId, documentId);

        } catch (RuntimeException exception) {

            // The Cloudinary object is intentionally kept: reprocess
            // needs the original PDF to retry ingestion.
            markFailed(documentId);

            throw new DocumentProcessingException(
                    "Document stored and marked FAILED during " + trigger
                            + "; retry via POST /api/v1/documents/"
                            + documentId + "/reprocess",
                    exception
            );
        }
    }

    private void markFailed(UUID documentId) {
        try {
            documentRepository.findById(documentId).ifPresent(document -> {
                document.setStatus(DocumentStatus.FAILED);
                document.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                documentRepository.save(document);
            });

        } catch (RuntimeException statusException) {
            log.error(
                    "Failed to mark document {} as FAILED",
                    documentId,
                    statusException
            );
        }
    }

    private void validateFile(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "File must not be empty"
            );
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "File size must not exceed 10 MB"
            );
        }

        if (!"application/pdf".equalsIgnoreCase(
                file.getContentType()
        )) {
            throw new IllegalArgumentException(
                    "Only PDF files are supported"
            );
        }
    }
}
