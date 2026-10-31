package com.example.backend;

import io.qdrant.client.QdrantClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class QdrantConnectionTest {

    @Autowired
    private QdrantClient qdrantClient;

    @Test
    void shouldConnectToQdrant() throws Exception {
        var response = qdrantClient
                .healthCheckAsync()
                .get();

        assertNotNull(response);

        System.out.println(
                "Qdrant connection successful: " + response
        );
    }
}