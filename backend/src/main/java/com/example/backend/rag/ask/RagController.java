package com.example.backend.rag.ask;

import com.example.backend.auth.entity.User;
import com.example.backend.rag.ask.dto.AskRequest;
import com.example.backend.rag.ask.dto.AskResponse;
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
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class RagController {

    private static final int DEFAULT_TOP_K = 5;

    private final RagService ragService;

    @PostMapping("/ask")
    public ResponseEntity<AskResponse> ask(
            Authentication authentication,
            @Valid @RequestBody AskRequest request
    ) {
        UUID userId = ((User) authentication.getPrincipal()).getId();

        int topK = request.getTopK() == null
                ? DEFAULT_TOP_K
                : request.getTopK();

        RagAnswer answer = ragService.ask(
                userId,
                request.getQuestion(),
                topK
        );

        return ResponseEntity.ok(
                AskResponse.builder()
                        .answer(answer.getAnswer())
                        .sources(answer.getSources())
                        .build()
        );
    }
}
