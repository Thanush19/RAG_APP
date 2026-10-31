package com.example.backend;

import com.example.backend.common.exception.ConflictException;
import com.example.backend.common.exception.EntityNotFoundException;
import com.example.backend.document.service.DocumentService;
import com.example.backend.rag.vector.QdrantOrphanSweepService;
import com.example.backend.rag.vector.VectorStoreDocumentRef;
import com.example.backend.rag.vector.VectorStoreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class IngestionConsistencyTest {

    private static final UUID REAL_USER_ID =
            UUID.fromString("22a37781-c505-4b87-a97e-1bdeb215d173");

    private static final UUID REAL_DOCUMENT_ID =
            UUID.fromString("00109296-ee93-491b-810c-42c395dbcaf4");

    @Autowired
    private DocumentService documentService;

    @Autowired
    private VectorStoreService vectorStoreService;

    @Autowired
    private QdrantOrphanSweepService orphanSweepService;

    @Test
    void shouldRejectReprocessOfReadyDocument() {

        assertThatThrownBy(() -> documentService.reprocessDocument(
                REAL_USER_ID,
                REAL_DOCUMENT_ID
        )).isInstanceOf(ConflictException.class);
    }

    @Test
    void shouldRejectReprocessOfForeignDocument() {

        assertThatThrownBy(() -> documentService.reprocessDocument(
                UUID.randomUUID(),
                REAL_DOCUMENT_ID
        )).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void scanShouldFindTheRealDocument() {

        List<VectorStoreDocumentRef> refs =
                vectorStoreService.scanStoredDocumentRefs();

        assertThat(refs).anySatisfy(ref -> {
            assertThat(ref.getDocumentId()).isEqualTo(REAL_DOCUMENT_ID);
            assertThat(ref.getUserId()).isEqualTo(REAL_USER_ID);
        });
    }

    @Test
    void sweepShouldNotThrowAndNotRemoveLiveDocument() {

        assertThatCode(() -> orphanSweepService.sweepOrphanedVectors())
                .doesNotThrowAnyException();

        assertThat(documentService.getUserDocument(
                REAL_USER_ID,
                REAL_DOCUMENT_ID
        )).isNotNull();

        assertThat(vectorStoreService.scanStoredDocumentRefs())
                .anySatisfy(ref ->
                        assertThat(ref.getDocumentId())
                                .isEqualTo(REAL_DOCUMENT_ID)
                );
    }
}
