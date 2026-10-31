package com.example.backend.rag.embedding;

import com.example.backend.rag.dto.NvidiaEmbeddingResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NvidiaEmbeddingService implements EmbeddingService {

    private final RestClient.Builder restClientBuilder;

    @Value("${app.ai.nvidia.api-key}")
    private String apiKey;

    @Value("${app.ai.nvidia.base-url}")
    private String baseUrl;

    @Value("${app.ai.nvidia.embedding-model}")
    private String embeddingModel;

    @Override
    public float[] embedPassage(String text) {
        return generateEmbedding(text, "passage");
    }

    @Override
    public float[] embedQuery(String text) {
        return generateEmbedding(text, "query");
    }

    private float[] generateEmbedding(
            String text,
            String inputType
    ) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(
                    "Text must not be blank"
            );
        }

        Map<String, Object> requestBody = new HashMap<>();

        requestBody.put("model", embeddingModel);
        requestBody.put("input", List.of(text));
        requestBody.put("input_type", inputType);
        requestBody.put("encoding_format", "float");
        requestBody.put("embedding_type", "float");
        requestBody.put("truncate", "END");

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

        NvidiaEmbeddingResponse response = restClient
                .post()
                .uri("/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(NvidiaEmbeddingResponse.class);

        return parseEmbedding(response);
    }

    private float[] parseEmbedding(
            NvidiaEmbeddingResponse response
    ) {
        if (response == null
                || response.data() == null
                || response.data().isEmpty()
                || response.data().getFirst().embedding() == null) {

            throw new IllegalStateException(
                    "NVIDIA response does not contain an embedding"
            );
        }

        List<Double> values =
                response.data().getFirst().embedding();

        float[] embedding = new float[values.size()];

        for (int i = 0; i < values.size(); i++) {
            embedding[i] = values.get(i).floatValue();
        }

        return embedding;
    }
}