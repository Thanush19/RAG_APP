package com.example.backend.document.service;

import com.example.backend.document.dto.DocumentResponse;
import com.example.backend.document.entity.Document;
import com.example.backend.document.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentService {

    private final DocumentRepository documentRepository;

    public List<DocumentResponse> getUserDocuments(UUID userId) {
        return documentRepository
                .findAllByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(DocumentResponse::from)
                .toList();
    }

    public DocumentResponse getUserDocument(UUID userId, UUID documentId) {
        Document document = documentRepository
                .findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Document not found"
                ));

        return DocumentResponse.from(document);
    }
}