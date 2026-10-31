package com.example.backend.rag.vector;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QdrantConfig {

    @Bean
    public QdrantClient qdrantClient(
            @Value("${app.vector.qdrant.url}") String url,
            @Value("${app.vector.qdrant.api-key}") String apiKey
    ) {
        String host = extractHost(url);

        return new QdrantClient(
                QdrantGrpcClient.newBuilder(
                                host,
                                6334,
                                true
                        )
                        .withApiKey(apiKey)
                        .build()
        );
    }

    private String extractHost(String url) {
        return url
                .replaceFirst("^https?://", "")
                .replaceFirst("/$", "");
    }
}