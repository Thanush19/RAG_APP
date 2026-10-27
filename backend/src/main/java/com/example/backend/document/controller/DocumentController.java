package com.example.backend.document.controller;

import com.example.backend.auth.entity.User;
import com.example.backend.document.dto.DocumentResponse;
import com.example.backend.document.service.DocumentService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
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

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> uploadDocument(
            Authentication authentication,
            @RequestParam("file") MultipartFile file
    ) {
        User user = (User) authentication.getPrincipal();

        DocumentResponse response = documentService.uploadDocument(
                user,
                file
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    private UUID getAuthenticatedUserId(Authentication authentication) {
        return ((User) authentication.getPrincipal())
                .getId();
    }

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocument(
            Authentication authentication,
            @PathVariable UUID documentId
    ) {
        UUID userId = getAuthenticatedUserId(authentication);

        documentService.deleteDocumentRecord(userId, documentId);
    }

    @GetMapping(
            path = "/{documentId}/bytes",
            produces = MediaType.APPLICATION_PDF_VALUE
    )
    public ResponseEntity<byte[]> getDocumentBytes(
            Authentication authentication,
            @PathVariable UUID documentId
    ) {
        UUID userId = getAuthenticatedUserId(authentication);

        try (InputStream inputStream =
                     documentService.downloadDocumentBytes(
                             userId,
                             documentId
                     )) {

            byte[] bytes = inputStream.readAllBytes();

            return ResponseEntity.ok(bytes);

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to download document bytes",
                    exception
            );
        }
    }
}