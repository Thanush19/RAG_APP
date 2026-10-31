package com.example.backend.rag.ask;

import com.example.backend.auth.entity.User;
import com.example.backend.common.exception.UpstreamServiceException;
import com.example.backend.rag.ask.dto.AskRequest;
import com.example.backend.rag.ask.dto.AskResponse;
import com.example.backend.rag.ask.dto.StreamErrorPayload;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class RagController {

    private static final int DEFAULT_TOP_K = 5;
    private static final long SSE_TIMEOUT_MILLIS = 300_000L;

    private final RagService ragService;
    private final AsyncTaskExecutor applicationTaskExecutor;
    private final ObjectMapper objectMapper;

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
                topK,
                request.getMinScore()
        );

        return ResponseEntity.ok(
                AskResponse.builder()
                        .answer(answer.getAnswer())
                        .sources(answer.getSources())
                        .build()
        );
    }

    /**
     * Same pipeline as /ask, delivered over SSE: one "sources" event,
     * N "token" events, then "done"; failures after the stream opens
     * arrive as an "error" event (HTTP status is already 200).
     */
    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(
            Authentication authentication,
            @Valid @RequestBody AskRequest request
    ) {
        UUID userId = ((User) authentication.getPrincipal()).getId();

        int topK = request.getTopK() == null
                ? DEFAULT_TOP_K
                : request.getTopK();

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);

        applicationTaskExecutor.execute(() ->
                ragService.askStream(
                        userId,
                        request.getQuestion(),
                        topK,
                        request.getMinScore(),
                        new RagService.RagStreamListener() {

                            @Override
                            public void onSources(List<RagSource> sources) {
                                send(emitter, "sources", sources);
                            }

                            @Override
                            public void onToken(String token) {
                                send(emitter, "token", Map.of("t", token));
                            }

                            @Override
                            public void onDone(String fullAnswer) {
                                send(emitter, "done", Map.of("answer", fullAnswer));
                                emitter.complete();
                            }

                            @Override
                            public void onError(Exception exception) {
                                sendError(emitter, exception);
                                emitter.complete();
                            }
                        }
                )
        );

        return emitter;
    }

    private void send(SseEmitter emitter, String event, Object payload) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event)
                    .data(objectMapper.writeValueAsString(payload),
                            MediaType.APPLICATION_JSON));

        } catch (IOException | RuntimeException exception) {
            emitter.completeWithError(exception);
        }
    }

    private void sendError(SseEmitter emitter, Exception exception) {
        String code = exception instanceof UpstreamServiceException
                ? "UPSTREAM_UNAVAILABLE"
                : "INTERNAL_ERROR";

        send(emitter, "error", StreamErrorPayload.builder()
                .code(code)
                .message("Answer generation failed; please retry.")
                .build());
    }
}
