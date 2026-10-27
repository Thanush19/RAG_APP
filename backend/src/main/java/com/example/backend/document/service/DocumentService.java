package com.example.backend.document.service;

import com.example.backend.auth.entity.User;
import com.example.backend.common.exception.EntityNotFoundException;
import com.example.backend.document.dto.DocumentResponse;
import com.example.backend.document.entity.Document;
import com.example.backend.document.entity.DocumentStatus;
import com.example.backend.document.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
                .orElseThrow(() -> new IllegalArgumentException(
                        "Document not found"
                ));

        return DocumentResponse.from(document);
    }

    @Transactional
    public DocumentResponse uploadDocument(
            User user,
            MultipartFile file
    ) {
        validateFile(file);

        long currentStorage =
                documentRepository.getTotalStorageUsedByUser(user.getId());

        long newStorage = currentStorage + file.getSize();

        if (newStorage > MAX_USER_STORAGE) {
            throw new IllegalArgumentException(
                    "Storage limit exceeded. Maximum storage per user is 50 MB."
            );
        }

        String storageKey = documentStorageService.upload(file);

        try {
            OffsetDateTime now =
                    OffsetDateTime.now(ZoneOffset.UTC);

            Document document = Document.builder()
                    .id(UUID.randomUUID())
                    .user(user)
                    .fileName(file.getOriginalFilename())
                    .fileType(file.getContentType())
                    .fileSize(file.getSize())
                    .storageKey(storageKey)
                    .status(DocumentStatus.UPLOADED)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            Document savedDocument =
                    documentRepository.save(document);

            return DocumentResponse.from(savedDocument);

        } catch (RuntimeException exception) {

            // PostgreSQL save failed after Cloudinary upload.
            // Remove the orphaned Cloudinary file.
            try {
                documentStorageService.delete(storageKey);
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }

            throw exception;
        }
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

        return storageKey;
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