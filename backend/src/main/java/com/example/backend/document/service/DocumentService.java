package com.example.backend.document.service;

import com.example.backend.auth.entity.User;
import com.example.backend.common.exception.EntityNotFoundException;
import com.example.backend.document.dto.ChunkData;
import com.example.backend.document.dto.DocumentResponse;
import com.example.backend.document.dto.ExtractedPage;
import com.example.backend.document.entity.Document;
import com.example.backend.document.entity.DocumentChunk;
import com.example.backend.document.entity.DocumentStatus;
import com.example.backend.document.repository.DocumentChunkRepository;
import com.example.backend.document.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
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
    private final DocumentChunkRepository documentChunkRepository;
    private final ChunkingService chunkingService;

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

        try {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

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

            Document savedDocument = documentRepository.save(document);

            // PDF → extraction → chunking → PostgreSQL
            processDocumentChunks(
                    user.getId(),
                    savedDocument.getId()
            );

            return DocumentResponse.from(savedDocument);

        } catch (RuntimeException exception) {
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

    @Transactional
    public List<DocumentChunk> processDocumentChunks(
            UUID userId,
            UUID documentId
    ) {
        Document document = documentRepository
                .findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Document not found"
                ));

        // 1. Extract text from PDF
        List<ExtractedPage> pages =
                pdfTextExtractionService.extract(document);

        // 2. Split extracted text into chunks
        List<ChunkData> chunkData =
                chunkingService.chunk(pages);

        // 3. Convert ChunkData → DocumentChunk
        OffsetDateTime now =
                OffsetDateTime.now(ZoneOffset.UTC);

        List<DocumentChunk> chunks = new java.util.ArrayList<>();

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

            chunks.add(chunk);
        }

        // 4. Persist all chunks
        return documentChunkRepository.saveAll(chunks);
    }
}