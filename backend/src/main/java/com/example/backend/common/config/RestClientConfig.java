package com.example.backend.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder(
            @Value("${app.ai.nvidia.connect-timeout-seconds:10}") int connectTimeoutSeconds,
            @Value("${app.ai.nvidia.read-timeout-seconds:120}") int readTimeoutSeconds
    ) {
        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(connectTimeoutSeconds * 1000);
        factory.setReadTimeout(readTimeoutSeconds * 1000);

        return RestClient.builder()
                .requestFactory(factory);
    }
}
