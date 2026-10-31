package com.example.backend.document.repository;

import java.util.UUID;

public interface OwnedChunkProjection {

    UUID getChunkId();

    UUID getDocumentId();

    Integer getPageNumber();

    String getText();
}
