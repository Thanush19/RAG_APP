package com.example.backend.rag.retrieval;

import com.example.backend.auth.entity.User;
import com.example.backend.rag.retrieval.dto.SearchRequest;
import com.example.backend.rag.retrieval.dto.SearchResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class RetrievalController {

    private static final int DEFAULT_TOP_K = 5;

    private final RetrievalService retrievalService;

    @PostMapping
    public ResponseEntity<SearchResponse> search(
            Authentication authentication,
            @Valid @RequestBody SearchRequest request
    ) {
        UUID userId = ((User) authentication.getPrincipal()).getId();

        int topK = request.getTopK() == null
                ? DEFAULT_TOP_K
                : request.getTopK();

        return ResponseEntity.ok(
                SearchResponse.builder()
                        .results(
                                retrievalService.retrieve(
                                        userId,
                                        request.getQuery(),
                                        topK
                                )
                        )
                        .build()
        );
    }
}
