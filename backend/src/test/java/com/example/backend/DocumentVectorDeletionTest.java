package com.example.backend;

import com.example.backend.rag.vector.VectorStoreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
class DocumentVectorDeletionTest {

    private static final UUID REAL_USER_ID =
            UUID.fromString("22a37781-c505-4b87-a97e-1bdeb215d173");

    @Autowired
    private VectorStoreService vectorStoreService;

    @Test
    void shouldDeleteByUserIdAndDocumentIdFilter() {

        // A deliberately non-existent documentId: proves the filter
        // executes (requires the documentId keyword payload index)
        // without deleting the real indexed document.
        assertThatCode(() -> vectorStoreService.deleteDocumentVectors(
                REAL_USER_ID,
                UUID.randomUUID()
        )).doesNotThrowAnyException();
    }
}
