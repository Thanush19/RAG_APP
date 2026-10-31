package com.example.backend.rag.llm;

import java.util.function.Consumer;

public interface LlmClient {

    String chat(String systemPrompt, String userPrompt);

    /**
     * Streams the generated answer token-by-token, invoking
     * {@code tokenConsumer} per upstream delta, and returns the full
     * accumulated text.
     */
    String chatStream(
            String systemPrompt,
            String userPrompt,
            Consumer<String> tokenConsumer
    );
}
