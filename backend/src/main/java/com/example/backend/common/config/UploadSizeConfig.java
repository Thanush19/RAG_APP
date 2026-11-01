package com.example.backend.common.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * Tomcat's default per-file multipart cap is 1 MB, but the Android client
 * stages files up to 10 MB (see UploadStager.MAX_BYTES), so anything between
 * the two limits used to fail as an opaque 500. The caps are declared here
 * rather than in application.yaml to keep both ends of the pipe in code.
 */
@Configuration
public class UploadSizeConfig {

    public static final DataSize MAX_FILE_SIZE = DataSize.ofMegabytes(10);

    private static final DataSize MAX_REQUEST_SIZE = DataSize.ofMegabytes(11);

    @Bean
    MultipartConfigElement multipartConfigElement() {
        return new MultipartConfigElement(
                System.getProperty("java.io.tmpdir"),
                MAX_FILE_SIZE.toBytes(),
                MAX_REQUEST_SIZE.toBytes(),
                0
        );
    }
}
