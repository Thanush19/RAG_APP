package com.example.backend.rag.ask;

import com.example.backend.rag.llm.LlmClient;
import com.example.backend.rag.retrieval.RetrievalService;
import com.example.backend.rag.retrieval.RetrievedChunk;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RagService {

    private static final String NO_CONTEXT_ANSWER =
            "I could not find any relevant content in your documents.";

    private final RetrievalService retrievalService;
    private final ContextBuilder contextBuilder;
    private final LlmClient llmClient;

    public RagAnswer ask(
            UUID userId,
            String question,
            int topK
    ) {
        return ask(userId, question, topK, null);
    }

    public RagAnswer ask(
            UUID userId,
            String question,
            int topK,
            Float minScore
    ) {
        List<RetrievedChunk> chunks =
                retrievalService.retrieve(
                        userId,
                        question,
                        topK,
                        null,
                        minScore
                );

        if (chunks.isEmpty()) {
            return RagAnswer.builder()
                    .answer(NO_CONTEXT_ANSWER)
                    .sources(List.of())
                    .build();
        }

        String answer = llmClient.chat(
                contextBuilder.buildSystemPrompt(),
                contextBuilder.buildUserPrompt(question, chunks)
        );

        List<RagSource> sources = chunks.stream()
                .map(chunk -> RagSource.builder()
                        .chunkId(chunk.getChunkId())
                        .documentId(chunk.getDocumentId())
                        .pageNumber(chunk.getPageNumber())
                        .score(chunk.getScore())
                        .build())
                .toList();

        return RagAnswer.builder()
                .answer(answer)
                .sources(sources)
                .build();
    }
}
