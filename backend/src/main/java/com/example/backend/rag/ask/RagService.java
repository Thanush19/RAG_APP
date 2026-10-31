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

    /**
     * Callback surface for the streaming ask; each method is invoked
     * from the request-processing worker thread.
     */
    public interface RagStreamListener {

        default void onSources(List<RagSource> sources) {
        }

        default void onToken(String token) {
        }

        default void onDone(String fullAnswer) {
        }

        default void onError(Exception exception) {
        }
    }

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

    /**
     * Streaming variant of {@link #ask}: identical retrieval and prompt
     * construction, but the LLM answer is delivered token-by-token to
     * the listener. The sources event fires before generation starts;
     * on no-context the fallback answer is emitted as a single token.
     */
    public void askStream(
            UUID userId,
            String question,
            int topK,
            Float minScore,
            RagStreamListener listener
    ) {
        try {
            List<RetrievedChunk> chunks =
                    retrievalService.retrieve(
                            userId,
                            question,
                            topK,
                            null,
                            minScore
                    );

            List<RagSource> sources = chunks.stream()
                    .map(chunk -> RagSource.builder()
                            .chunkId(chunk.getChunkId())
                            .documentId(chunk.getDocumentId())
                            .pageNumber(chunk.getPageNumber())
                            .score(chunk.getScore())
                            .build())
                    .toList();

            listener.onSources(sources);

            String fullAnswer;

            if (chunks.isEmpty()) {
                listener.onToken(NO_CONTEXT_ANSWER);
                fullAnswer = NO_CONTEXT_ANSWER;

            } else {
                fullAnswer = llmClient.chatStream(
                        contextBuilder.buildSystemPrompt(),
                        contextBuilder.buildUserPrompt(question, chunks),
                        listener::onToken
                );
            }

            listener.onDone(fullAnswer);

        } catch (Exception exception) {
            listener.onError(exception);
        }
    }
}
