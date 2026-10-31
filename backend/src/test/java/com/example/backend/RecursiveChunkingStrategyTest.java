package com.example.backend;

import com.example.backend.document.dto.ChunkData;
import com.example.backend.document.dto.ExtractedPage;
import com.example.backend.document.service.RecursiveChunkingStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecursiveChunkingStrategyTest {

    private final RecursiveChunkingStrategy chunkingStrategy =
            new RecursiveChunkingStrategy();

    @Test
    void shouldKeepSmallTextAsSingleChunk() {

        ExtractedPage page = new ExtractedPage(
                1,
                "This is a small document."
        );

        List<ChunkData> chunks =
                chunkingStrategy.chunk(List.of(page));

        assertEquals(1, chunks.size());

        assertEquals(1, chunks.get(0).pageNumber());

        assertEquals(
                "This is a small document.",
                chunks.get(0).text()
        );
    }

    @Test
    void shouldSplitLargeTextIntoMultipleChunks() {

        String text = "This is a sentence. ".repeat(300);

        ExtractedPage page = new ExtractedPage(
                1,
                text
        );

        List<ChunkData> chunks =
                chunkingStrategy.chunk(List.of(page));

        assertTrue(chunks.size() > 1);

        for (ChunkData chunk : chunks) {
            assertTrue(chunk.text().length() <= 3000);
        }
    }

    @Test
    void shouldPreservePageNumber() {

        ExtractedPage page1 = new ExtractedPage(
                1,
                "Page one content."
        );

        ExtractedPage page2 = new ExtractedPage(
                2,
                "Page two content."
        );

        List<ChunkData> chunks =
                chunkingStrategy.chunk(List.of(page1, page2));

        assertEquals(2, chunks.size());

        assertEquals(1, chunks.get(0).pageNumber());
        assertEquals(2, chunks.get(1).pageNumber());
    }

    @Test
    void shouldIgnoreEmptyPages() {

        ExtractedPage page1 = new ExtractedPage(
                1,
                ""
        );

        ExtractedPage page2 = new ExtractedPage(
                2,
                "Valid content."
        );

        List<ChunkData> chunks =
                chunkingStrategy.chunk(List.of(page1, page2));

        assertEquals(1, chunks.size());

        assertEquals(
                "Valid content.",
                chunks.get(0).text()
        );

        assertEquals(
                2,
                chunks.get(0).pageNumber()
        );
    }
}