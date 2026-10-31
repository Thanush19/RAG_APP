package com.example.backend;

import com.example.backend.rag.embedding.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
class NvidiaEmbeddingServiceTest {

    @Autowired
    private EmbeddingService embeddingService;

    @Test
    void shouldGeneratePassageEmbedding() {

        String text = """
                This is a test document chunk.
                It contains information about software engineering.
                """;

        float[] embedding =
                embeddingService.embedPassage(text);

        assertThat(embedding).isNotNull();
        assertThat(embedding).hasSize(2048);
    }
}