package com.example.backend.rag.llm;

public interface LlmClient {

    String chat(String systemPrompt, String userPrompt);
}
