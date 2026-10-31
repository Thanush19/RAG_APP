package com.example.backend.rag.llm;

import com.example.backend.common.exception.LlmUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NvidiaLlmClient implements LlmClient {

    private final RestClient.Builder restClientBuilder;

    @Value("${app.ai.nvidia.api-key}")
    private String apiKey;

    @Value("${app.ai.nvidia.base-url}")
    private String baseUrl;

    @Value("${app.ai.nvidia.chat-model}")
    private String chatModel;

    @Value("${app.ai.nvidia.chat-max-tokens:2048}")
    private int chatMaxTokens;

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalArgumentException("User prompt must not be blank");
        }

        RestClient restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader(
                        "Authorization",
                        "Bearer " + apiKey
                )
                .defaultHeader(
                        "Accept",
                        MediaType.APPLICATION_JSON_VALUE
                )
                .build();

        Map<String, Object> requestBody = Map.of(
                "model", chatModel,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", 0.2,
                "top_p", 0.9,
                "max_tokens", chatMaxTokens,
                "stream", false
        );

        JsonNode response;

        try {
            response = restClient
                    .post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);

        } catch (RestClientException exception) {
            throw new LlmUnavailableException(exception);
        }

        JsonNode content = response == null
                ? null
                : response.path("choices").path(0).path("message").path("content");

        if (content == null || content.isMissingNode() || content.asString().isBlank()) {
            throw new LlmUnavailableException(new IllegalStateException(
                    "NVIDIA response does not contain generated content"
            ));
        }

        return content.asString().trim();
    }
}
