package com.example.backend.document.service;

import com.example.backend.document.dto.ChunkData;
import com.example.backend.document.dto.ExtractedPage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class RecursiveChunkingStrategy implements ChunkingStrategy {

    private static final int MAX_CHUNK_SIZE = 3000;
    private static final int OVERLAP_SIZE = 300;

    private static final List<String> SEPARATORS = List.of(
            "\n\n",
            "\n",
            ". ",
            " ",
            ""
    );

    @Override
    public List<ChunkData> chunk(List<ExtractedPage> pages) {

        List<ChunkData> chunks = new ArrayList<>();

        for (ExtractedPage page : pages) {

            if (page.getText() == null || page.getText().isBlank()) {
                continue;
            }

            List<String> pageChunks = splitRecursively(
                    page.getText().trim(),
                    SEPARATORS,
                    0
            );

            for (String text : pageChunks) {

                if (!text.isBlank()) {
                    chunks.add(
                            new ChunkData(
                                    page.getPageNumber(),
                                    text.trim()
                            )
                    );
                }
            }
        }

        return chunks;
    }

    private List<String> splitRecursively(
            String text,
            List<String> separators,
            int separatorIndex
    ) {

        if (text.length() <= MAX_CHUNK_SIZE) {
            return List.of(text);
        }

        if (separatorIndex >= separators.size()) {
            return splitByFixedSize(text);
        }

        String separator = separators.get(separatorIndex);

        List<String> parts;

        if (separator.isEmpty()) {
            return splitByFixedSize(text);
        }

        parts = Arrays.stream(text.split(
                        java.util.regex.Pattern.quote(separator)
                ))
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .toList();

        if (parts.size() <= 1) {
            return splitRecursively(
                    text,
                    separators,
                    separatorIndex + 1
            );
        }

        List<String> result = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();

        for (String part : parts) {

            if (part.length() > MAX_CHUNK_SIZE) {

                if (!currentChunk.isEmpty()) {
                    result.add(currentChunk.toString().trim());
                    currentChunk.setLength(0);
                }

                result.addAll(
                        splitRecursively(
                                part,
                                separators,
                                separatorIndex + 1
                        )
                );

                continue;
            }

            int additionalLength = currentChunk.isEmpty()
                    ? part.length()
                    : separator.length() + part.length();

            if (currentChunk.length() + additionalLength
                    <= MAX_CHUNK_SIZE) {

                if (!currentChunk.isEmpty()) {
                    currentChunk
                            .append(separator);
                }

                currentChunk.append(part);

            } else {

                if (!currentChunk.isEmpty()) {
                    result.add(currentChunk.toString().trim());
                }

                String overlap = getOverlap(currentChunk.toString());

                currentChunk.setLength(0);

                if (!overlap.isBlank()) {
                    currentChunk.append(overlap);
                    currentChunk.append(separator);
                }

                currentChunk.append(part);
            }
        }

        if (!currentChunk.isEmpty()) {
            result.add(currentChunk.toString().trim());
        }

        return result;
    }

    private List<String> splitByFixedSize(String text) {

        List<String> chunks = new ArrayList<>();

        int start = 0;

        while (start < text.length()) {

            int end = Math.min(
                    start + MAX_CHUNK_SIZE,
                    text.length()
            );

            chunks.add(
                    text.substring(start, end).trim()
            );

            if (end == text.length()) {
                break;
            }

            start = Math.max(
                    end - OVERLAP_SIZE,
                    start + 1
            );
        }

        return chunks;
    }

    private String getOverlap(String text) {

        if (text == null || text.isBlank()) {
            return "";
        }

        if (text.length() <= OVERLAP_SIZE) {
            return text;
        }

        return text.substring(
                text.length() - OVERLAP_SIZE
        );
    }
}