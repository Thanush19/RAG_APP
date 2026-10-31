package com.example.backend.rag.ask;

import com.example.backend.rag.retrieval.RetrievedChunk;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ContextBuilder {

    public String buildSystemPrompt() {
        return """
                You are a question-answering assistant.
                Answer ONLY using the provided context.
                If the context does not contain the answer, say exactly: \
                "The provided documents do not contain an answer to this question."
                Cite sources inline using their labels, e.g. [S1], [S2].
                Keep the answer concise and factual.
                """;
    }

    public String buildContext(List<RetrievedChunk> chunks) {
        StringBuilder context = new StringBuilder();

        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk chunk = chunks.get(i);

            context
                    .append("[S").append(i + 1).append("]")
                    .append(" (document=")
                    .append(chunk.getDocumentId())
                    .append(", page=")
                    .append(chunk.getPageNumber())
                    .append(")\n")
                    .append(chunk.getText().trim())
                    .append("\n\n");
        }

        return context.toString().trim();
    }

    public String buildUserPrompt(String question, List<RetrievedChunk> chunks) {
        return """
                Context:
                %s

                Question:
                %s
                """.formatted(buildContext(chunks), question);
    }
}
