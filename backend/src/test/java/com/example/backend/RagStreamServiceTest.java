package com.example.backend;

import com.example.backend.rag.ask.RagService;
import com.example.backend.rag.ask.RagSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RagStreamServiceTest {

    private static final UUID REAL_USER_ID =
            UUID.fromString("22a37781-c505-4b87-a97e-1bdeb215d173");

    private static final UUID REAL_DOCUMENT_ID =
            UUID.fromString("00109296-ee93-491b-810c-42c395dbcaf4");

    @Autowired
    private RagService ragService;

    @Test
    void shouldStreamTokensAfterSourcesForGroundedQuestion() {

        List<RagSource> sources = Collections.synchronizedList(new ArrayList<>());
        List<String> tokens = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<String> doneAnswer = new AtomicReference<>();
        AtomicInteger errorCount = new AtomicInteger();
        AtomicBoolean done = new AtomicBoolean();

        ragService.askStream(
                REAL_USER_ID,
                "What is this document about?",
                5,
                null,
                new RagService.RagStreamListener() {

                    @Override
                    public void onSources(List<RagSource> s) {
                        sources.addAll(s);
                    }

                    @Override
                    public void onToken(String token) {
                        tokens.add(token);
                    }

                    @Override
                    public void onDone(String fullAnswer) {
                        doneAnswer.set(fullAnswer);
                        done.set(true);
                    }

                    @Override
                    public void onError(Exception exception) {
                        errorCount.incrementAndGet();
                        throw new RuntimeException(exception);
                    }
                }
        );

        assertThat(errorCount.get()).isZero();
        assertThat(done).isTrue();
        assertThat(sources).isNotEmpty();
        assertThat(sources)
                .allSatisfy(source ->
                        assertThat(source.getDocumentId()).isEqualTo(REAL_DOCUMENT_ID));

        // Real token streaming, not one-shot delivery.
        assertThat(tokens.size()).isGreaterThan(1);

        String joined = String.join("", tokens);
        assertThat(joined).isNotBlank();
        assertThat(doneAnswer.get()).isNotBlank();
    }

    @Test
    void shouldStreamFallbackAnswerWhenNothingRetrieved() {

        List<String> tokens = new ArrayList<>();
        AtomicReference<String> doneAnswer = new AtomicReference<>();
        AtomicBoolean errored = new AtomicBoolean();

        ragService.askStream(
                UUID.randomUUID(),
                "What is this document about?",
                5,
                null,
                new RagService.RagStreamListener() {

                    @Override
                    public void onToken(String token) {
                        tokens.add(token);
                    }

                    @Override
                    public void onDone(String fullAnswer) {
                        doneAnswer.set(fullAnswer);
                    }

                    @Override
                    public void onError(Exception exception) {
                        errored.set(true);
                    }
                }
        );

        assertThat(errored).isFalse();
        assertThat(tokens).hasSize(1);
        assertThat(doneAnswer.get())
                .isEqualTo("I could not find any relevant content in your documents.");
    }
}
