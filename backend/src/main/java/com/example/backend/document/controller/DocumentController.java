package com.example.backend.document.controller;

import com.example.backend.document.dto.DocumentResponse;
import com.example.backend.document.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @GetMapping
    public ResponseEntity<List<DocumentResponse>> getDocuments(
            Authentication authentication
    ) {
        UUID userId = getAuthenticatedUserId(authentication);

        return ResponseEntity.ok(
                documentService.getUserDocuments(userId)
        );
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<DocumentResponse> getDocument(
            Authentication authentication,
            @PathVariable UUID documentId
    ) {
        UUID userId = getAuthenticatedUserId(authentication);

        return ResponseEntity.ok(
                documentService.getUserDocument(userId, documentId)
        );
    }

    private UUID getAuthenticatedUserId(Authentication authentication) {
        return ((com.example.backend.auth.entity.User) authentication.getPrincipal())
                .getId();
    }
}