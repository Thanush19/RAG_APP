package com.example.backend.document.dto;

import com.example.backend.document.entity.Document;
import com.example.backend.document.entity.DocumentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class DocumentResponse {

    private UUID id;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private DocumentStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static DocumentResponse from(Document document) {
        return DocumentResponse.builder()
                .id(document.getId())
                .fileName(document.getFileName())
                .fileType(document.getFileType())
                .fileSize(document.getFileSize())
                .status(document.getStatus())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }
}