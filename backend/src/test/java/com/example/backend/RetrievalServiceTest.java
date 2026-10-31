package com.example.backend;

import com.example.backend.rag.retrieval.RetrievalService;
import com.example.backend.rag.retrieval.RetrievedChunk;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RetrievalServiceTest {

    private static final UUID REAL_USER_ID =
            UUID.fromString("22a37781-c505-4b87-a97e-1bdeb215d173");

    private static final UUID REAL_DOCUMENT_ID =
            UUID.fromString("00109296-ee93-491b-810c-42c395dbcaf4");

    @Autowired
    private RetrievalService retrievalService;

    @Test
    void shouldRetrieveRealChunksForOwner() {

        List<RetrievedChunk> results = retrievalService.retrieve(
                REAL_USER_ID,
                "What is this document about?",
                5
        );

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(chunk -> {
            assertThat(chunk.getDocumentId()).isEqualTo(REAL_DOCUMENT_ID);
            assertThat(chunk.getText()).isNotBlank();
            assertThat(chunk.getScore()).isBetween(-1f, 1f);
        });

        assertThat(results).isSortedAccordingTo(
                (a, b) -> Float.compare(b.getScore(), a.getScore())
        );
    }

    @Test
    void shouldRespectTopK() {

        List<RetrievedChunk> results = retrievalService.retrieve(
                REAL_USER_ID,
                "Summarize the main idea",
                1
        );

        assertThat(results).hasSize(1);
    }

    @Test
    void shouldReturnNothingForDifferentUser() {

        List<RetrievedChunk> results = retrievalService.retrieve(
                UUID.randomUUID(),
                "What is this document about?",
                5
        );

        assertThat(results).isEmpty();
    }

    @Test
    void shouldScopeSearchToGivenDocument() {

        List<RetrievedChunk> results = retrievalService.retrieve(
                REAL_USER_ID,
                "What is this document about?",
                5,
                REAL_DOCUMENT_ID,
                null
        );

        assertThat(results).isNotEmpty();

        List<RetrievedChunk> otherDocument = retrievalService.retrieve(
                REAL_USER_ID,
                "What is this document about?",
                5,
                UUID.randomUUID(),
                null
        );

        assertThat(otherDocument).isEmpty();
    }

    @Test
    void shouldFilterResultsBelowMinScore() {

        List<RetrievedChunk> results = retrievalService.retrieve(
                REAL_USER_ID,
                "What is this document about?",
                5,
                null,
                0.9f
        );

        assertThat(results).isEmpty();
    }
}
