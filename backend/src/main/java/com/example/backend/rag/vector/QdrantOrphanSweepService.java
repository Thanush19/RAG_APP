package com.example.backend.rag.vector;

import com.example.backend.document.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.vector.qdrant.orphan-sweep.enabled",
        havingValue = "true"
)
public class QdrantOrphanSweepService {

    private static final Logger log =
            LoggerFactory.getLogger(QdrantOrphanSweepService.class);

    private final VectorStoreService vectorStoreService;
    private final DocumentRepository documentRepository;

    @Scheduled(
            initialDelayString = "${app.vector.qdrant.orphan-sweep.initial-delay-millis:300000}",
            fixedDelayString = "${app.vector.qdrant.orphan-sweep.interval-millis:3600000}"
    )
    public void sweepOrphanedVectors() {

        try {
            List<VectorStoreDocumentRef> refs =
                    vectorStoreService.scanStoredDocumentRefs();

            int removed = 0;

            for (VectorStoreDocumentRef ref : refs) {

                if (documentRepository.existsById(ref.getDocumentId())) {
                    continue;
                }

                // PostgreSQL is the source of truth: no document row
                // means these points are unreachable orphans.
                vectorStoreService.deleteDocumentVectors(
                        ref.getUserId(),
                        ref.getDocumentId()
                );
                removed++;

                log.warn(
                        "Orphan sweep deleted Qdrant vectors for "
                                + "document {} (no PostgreSQL row)",
                        ref.getDocumentId()
                );
            }

            if (removed > 0) {
                log.info(
                        "Orphan sweep finished: {} document(s) purged of "
                                + "{} scanned",
                        removed,
                        refs.size()
                );
            }

        } catch (Exception exception) {
            log.error("Orphan sweep failed; will retry next interval",
                    exception);
        }
    }
}
