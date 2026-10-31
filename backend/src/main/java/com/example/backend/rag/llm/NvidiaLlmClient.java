package com.example.backend.rag.llm;

import com.example.backend.common.exception.LlmUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class NvidiaLlmClient implements LlmClient {

    private static final String SSE_DONE_MARKER = "[DONE]";

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${app.ai.nvidia.api-key}")
    private String apiKey;

    @Value("${app.ai.nvidia.base-url}")
    private String baseUrl;

    @Value("${app.ai.nvidia.chat-model}")
    private String chatModel;

    @Value("${app.ai.nvidia.chat-max-tokens:2048}")
    private int chatMaxTokens;

    @Value("${app.ai.nvidia.connect-timeout-seconds:10}")
    private int connectTimeoutSeconds;

    @Value("${app.ai.nvidia.read-timeout-seconds:120}")
    private int readTimeoutSeconds;

    @Value("${app.ai.nvidia.stream-timeout-seconds:180}")
    private int streamTimeoutSeconds;

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

    @Override
    public String chatStream(
            String systemPrompt,
            String userPrompt,
            Consumer<String> tokenConsumer
    ) {
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalArgumentException("User prompt must not be blank");
        }
        if (tokenConsumer == null) {
            throw new IllegalArgumentException("Token consumer must not be null");
        }

        String requestBody;

        try {
            requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", chatModel,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "temperature", 0.2,
                    "top_p", 0.9,
                    "max_tokens", chatMaxTokens,
                    "stream", true
            ));

        } catch (RuntimeException exception) {
            throw new LlmUnavailableException(exception);
        }

        // SimpleClientHttpRequestFactory applies its read timeout only to the
        // first packet, so a long token stream needs the JDK client with a
        // whole-exchange timeout.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(readTimeoutSeconds + streamTimeoutSeconds))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<InputStream> response;

        try {
            response = httpClient.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofInputStream()
            );

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LlmUnavailableException(exception);
        } catch (Exception exception) {
            throw new LlmUnavailableException(exception);
        }

        if (response.statusCode() != 200) {
            closeQuietly(response.body());
            throw new LlmUnavailableException(new IllegalStateException(
                    "NVIDIA stream returned HTTP " + response.statusCode()
            ));
        }

        StringBuilder fullText = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {

            String line;

            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }

                String payload = line.substring("data:".length()).trim();

                if (payload.isEmpty() || SSE_DONE_MARKER.equals(payload)) {
                    continue;
                }

                JsonNode delta = objectMapper.readTree(payload)
                        .path("choices").path(0).path("delta").path("content");

                if (delta.isMissingNode() || !delta.isString()) {
                    continue;
                }

                String token = delta.asString();

                if (!token.isEmpty()) {
                    fullText.append(token);
                    tokenConsumer.accept(token);
                }
            }

        } catch (Exception exception) {
            throw new LlmUnavailableException(exception);
        }

        if (fullText.isEmpty()) {
            throw new LlmUnavailableException(new IllegalStateException(
                    "NVIDIA stream produced no content"
            ));
        }

        return fullText.toString().trim();
    }

    private void closeQuietly(InputStream inputStream) {
        if (inputStream == null) {
            return;
        }

        try {
            inputStream.close();
        } catch (Exception ignored) {
            // best-effort cleanup of a non-200 response body
        }
    }
}
