package com.example.backend.rag.retrieval.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SearchRequest {

    @NotBlank(message = "Query is required")
    @Size(max = 2000, message = "Query must not exceed 2000 characters")
    private String query;

    @Min(value = 1, message = "topK must be at least 1")
    @Max(value = 20, message = "topK must not exceed 20")
    private Integer topK;

    private UUID documentId;

    @DecimalMin(value = "-1.0", message = "minScore must be at least -1.0")
    @DecimalMax(value = "1.0", message = "minScore must not exceed 1.0")
    private Float minScore;
}
