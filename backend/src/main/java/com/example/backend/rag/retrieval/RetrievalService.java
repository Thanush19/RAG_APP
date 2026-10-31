package com.example.backend.rag.retrieval;

import com.example.backend.document.repository.DocumentChunkRepository;
import com.example.backend.document.repository.OwnedChunkProjection;
import com.example.backend.rag.embedding.EmbeddingService;
import com.example.backend.rag.vector.VectorMatch;
import com.example.backend.rag.vector.VectorStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RetrievalService {

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final DocumentChunkRepository documentChunkRepository;

    public List<RetrievedChunk> retrieve(
            UUID userId,
            String query,
            int topK
    ) {
        float[] queryEmbedding = embeddingService.embedQuery(query);

        List<VectorMatch> matches =
                vectorStoreService.searchSimilar(userId, queryEmbedding, topK);

        if (matches.isEmpty()) {
            return List.of();
        }

        List<UUID> chunkIds = matches.stream()
                .map(VectorMatch::getChunkId)
                .toList();

        Map<UUID, OwnedChunkProjection> chunksById = new HashMap<>();

        for (OwnedChunkProjection chunk :
                documentChunkRepository.findOwnedChunksByIds(userId, chunkIds)) {
            chunksById.put(chunk.getChunkId(), chunk);
        }

        List<RetrievedChunk> results = new ArrayList<>();

        for (VectorMatch match : matches) {
            OwnedChunkProjection chunk = chunksById.get(match.getChunkId());

            if (chunk == null) {
                continue;
            }

            results.add(
                    RetrievedChunk.builder()
                            .chunkId(chunk.getChunkId())
                            .documentId(chunk.getDocumentId())
                            .pageNumber(chunk.getPageNumber())
                            .text(chunk.getText())
                            .score(match.getScore())
                            .build()
            );
        }

        return results;
    }
}
