package com.example.backend;

import com.example.backend.document.entity.DocumentChunk;
import com.example.backend.document.repository.DocumentChunkRepository;
import com.example.backend.rag.ask.RagAnswer;
import com.example.backend.rag.ask.RagService;
import com.example.backend.rag.ask.RagSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RagServiceTest {

    private static final UUID REAL_USER_ID =
            UUID.fromString("22a37781-c505-4b87-a97e-1bdeb215d173");

    private static final UUID REAL_DOCUMENT_ID =
            UUID.fromString("00109296-ee93-491b-810c-42c395dbcaf4");

    @Autowired
    private RagService ragService;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @Test
    void shouldAnswerGroundedInRealDocument() {

        RagAnswer result = ragService.ask(
                REAL_USER_ID,
                "What is this document about? Who is the person?",
                5
        );

        assertThat(result.getAnswer()).isNotBlank();
        assertThat(result.getSources()).isNotEmpty();

        assertThat(result.getSources())
                .allSatisfy(source -> {
                    assertThat(source.getDocumentId()).isEqualTo(REAL_DOCUMENT_ID);
                    assertThat(source.getChunkId()).isNotNull();
                    assertThat(source.getScore()).isBetween(-1f, 1f);
                });
    }

    @Test
    void shouldShortCircuitWhenNothingRetrieved() {

        RagAnswer result = ragService.ask(
                UUID.randomUUID(),
                "What is this document about?",
                5
        );

        assertThat(result.getSources()).isEmpty();
        assertThat(result.getAnswer())
                .isEqualTo("I could not find any relevant content in your documents.");
    }

    @Test
    void shouldOnlyCiteOwnedChunks() {

        RagAnswer result = ragService.ask(
                REAL_USER_ID,
                "Describe the professional experience mentioned.",
                20
        );

        List<UUID> chunkIds = result.getSources().stream()
                .map(RagSource::getChunkId)
                .toList();

        // Dynamic expectation: re-ingestion recreates chunks with new
        // IDs, so cite-set must equal the document's current rows.
        List<UUID> expectedChunkIds = documentChunkRepository
                .findAllByDocumentId(REAL_DOCUMENT_ID)
                .stream()
                .map(DocumentChunk::getId)
                .toList();

        assertThat(expectedChunkIds).isNotEmpty();
        assertThat(chunkIds).containsExactlyInAnyOrderElementsOf(expectedChunkIds);
    }
}
