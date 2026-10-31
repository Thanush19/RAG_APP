# RAG Backend Project — Updated Development Plan & Current Status

## 1. Project Overview

Production-oriented Java Spring Boot RAG backend being developed as a backend-transition project.

### Stack

- Java 21
- Spring Boot 4.1.1
- Spring Framework 7.0.9
- Gradle 9.7.1
- PostgreSQL / Neon
- Flyway
- Spring Data JPA
- Spring Security + JWT
- Lombok
- Swagger / OpenAPI
- Actuator
- Cloudinary
- Apache PDFBox
- NVIDIA API for embeddings and later LLM
- Qdrant Cloud for vector storage

### Architecture principles

- Feature-oriented modular monolith
- PostgreSQL is the source of truth for document/chunk metadata and text
- Qdrant stores embeddings and retrieval metadata
- Embeddings are NOT stored in PostgreSQL
- External integrations are hidden behind service interfaces
- Preserve user/document ownership isolation
- No premature microservices, Kafka, Redis, Kubernetes, or Elasticsearch
- Retrieval must work before introducing the LLM generation layer
- Internal document-processing methods should not be exposed as controller endpoints
- Keep the architecture production-oriented and interview-explainable
- **Use Lombok classes (`@Getter`/`@Builder`/`@AllArgsConstructor`),
  NOT Java records, for all new DTOs and value carriers**
  (see section 20 — Coding Conventions)

---

# 2. COMPLETED

## 2.1 Backend Foundation — COMPLETE

Implemented:

- Spring Boot application
- Java 21
- Gradle build
- PostgreSQL / Neon
- Flyway migrations
- JPA
- Spring Security
- JWT authentication
- User ownership/security
- Swagger/OpenAPI
- Actuator
- Cloudinary integration

---

## 2.2 Document Upload — COMPLETE

Current flow:

PDF upload
→ validation
→ Cloudinary storage
→ document metadata in PostgreSQL
→ PDF extraction
→ chunking
→ DocumentChunk persistence
→ embedding generation
→ Qdrant vector storage

Constraints:

- PDF only
- Maximum individual document size: 10 MB
- Maximum user storage: 50 MB

---

## 2.3 PDF Text Extraction — COMPLETE

`PdfTextExtractionService` extracts PDF content into:

`List<ExtractedPage>`

`ExtractedPage` is a Lombok class.

Available accessors:

- `getPageNumber()`
- `getText()`

---

## 2.4 Chunking — COMPLETE

`ChunkingService` accepts:

`List<ExtractedPage>`

and produces:

`List<ChunkData>`

`ChunkData`:

- `pageNumber`
- `text`

Chunking is already integrated into document processing.

---

## 2.5 PostgreSQL Document Chunks — COMPLETE

`DocumentChunk` entity contains:

- `id`
- `document`
- `chunkIndex`
- `pageNumber`
- `text`
- `qdrantPointId`
- `createdAt`

Database uniqueness:

`(document_id, chunk_index)`

Important design:

`DocumentChunk.id == Qdrant point ID`

The entity now has a setter for `qdrantPointId`.

A real PDF has already been processed successfully.

Verified PostgreSQL rows:

- Chunk 0:
  - id: `84382c00-d17a-4dad-b6b0-7e8f3bd38d5a`
  - document_id: `00109296-ee93-491b-810c-42c395dbcaf4`
  - page_number: 1
  - qdrant_point_id: `84382c00-d17a-4dad-b6b0-7e8f3bd38d5a`

- Chunk 1:
  - id: `87e700e7-6fd0-4c7d-b5c0-b15f49efc359`
  - document_id: `00109296-ee93-491b-810c-42c395dbcaf4`
  - page_number: 1
  - qdrant_point_id: `87e700e7-6fd0-4c7d-b5c0-b15f49efc359`

This confirms the PostgreSQL ↔ Qdrant point-ID mapping.

---

# 3. NVIDIA EMBEDDINGS — COMPLETE

OpenAI embeddings were replaced with the NVIDIA free API.

### Model

`nvidia/nemotron-3.5-embed-1b`

### Base URL

`https://integrate.api.nvidia.com/v1`

### Architecture

`EmbeddingService`
→
`NvidiaEmbeddingService`
→
NVIDIA API

Interface:

```java
public interface EmbeddingService {
    float[] embedPassage(String text);
    float[] embedQuery(String text);
}
```

Implementation uses Spring `RestClient`.

Do NOT introduce Spring AI just for embeddings.

### Configuration

- `app.ai.nvidia.api-key`
- `app.ai.nvidia.base-url`
- `app.ai.nvidia.embedding-model`

### Verification

`NvidiaEmbeddingServiceTest` passes.

Command:

```bash
./gradlew test --tests com.example.backend.NvidiaEmbeddingServiceTest
```

Embedding dimension verified:

`2048`

---

# 4. EMBEDDING IN INGESTION — COMPLETE

For every chunk:

```text
ChunkData
↓
EmbeddingService.embedPassage()
↓
2048-dimensional float[]
```

Embeddings are not persisted in PostgreSQL.

---

# 5. QDRANT SETUP — COMPLETE

Dependency:

```gradle
implementation 'io.qdrant:client:1.20.0'
```

Configuration:

```yaml
app:
  vector:
    qdrant:
      url: ${QDRANT_URL}
      api-key: ${QDRANT_API_KEY}
      collection-name: document_chunks
```

`QdrantConfig` creates the `QdrantClient`.

Cloud connectivity was verified using the Qdrant health check.

---

# 6. QDRANT COLLECTION — COMPLETE

Collection:

`document_chunks`

Configuration:

- Vector size: 2048
- Distance: Cosine
- Collection health: healthy / green

The collection initially contained 0 points, which was expected before ingestion integration.

---

# 7. VECTOR STORE ABSTRACTION — COMPLETE

Interface:

```java
public interface VectorStoreService {

    void storeChunk(
        UUID userId,
        UUID documentId,
        UUID chunkId,
        Integer pageNumber,
        float[] embedding
    );
}
```

Implementation:

`QdrantVectorStoreService`

Current implementation stores:

- Qdrant point ID
- vector
- userId
- documentId
- chunkId
- pageNumber

The current Qdrant API implementation uses:

- `PointStruct`
- `PointIdFactory.id`
- `ValueFactory.value`
- `VectorsFactory.vectors`
- `setId(id(chunkId))`
- `setVectors(vectors(embedding))`
- `putAllPayload(...)`
- `upsertAsync(...)`

---

# 8. END-TO-END QDRANT INGESTION — COMPLETE

`DocumentService.processDocumentChunks()` now performs:

```text
Extract PDF pages
↓
Chunk text
↓
Create DocumentChunk
↓
Save DocumentChunk in PostgreSQL
↓
Generate NVIDIA passage embedding
↓
Store vector in Qdrant
↓
Set qdrantPointId = DocumentChunk.id
```

The Qdrant point ID is intentionally the same UUID as `DocumentChunk.id`.

### Real verification

The uploaded document produced 2 Qdrant points.

Payloads were verified.

Example payload:

```json
{
  "chunkId": "84382c00-d17a-4dad-b6b0-7e8f3bd38d5a",
  "pageNumber": 1,
  "userId": "22a37781-c505-4b87-a97e-1bdeb215d173",
  "documentId": "00109296-ee93-491b-810c-42c395dbcaf4"
}
```

Second point:

```json
{
  "documentId": "00109296-ee93-491b-810c-42c395dbcaf4",
  "chunkId": "87e700e7-6fd0-4c7d-b5c0-b15f49efc359",
  "pageNumber": 1,
  "userId": "22a37781-c505-4b87-a97e-1bdeb215d173"
}
```

Therefore verified:

- Qdrant points exist
- Point IDs match PostgreSQL chunk IDs
- Payload contains ownership/document/chunk/page metadata
- 2048-dimensional vectors are being inserted
- PostgreSQL stores the corresponding `qdrantPointId`

---

# 9. CURRENT CHECKPOINT

The ingestion side is now complete.

Current architecture:

```text
PDF
 ↓
Cloudinary
 ↓
Document metadata
 ↓
PDF extraction
 ↓
Chunking
 ↓
PostgreSQL DocumentChunk
 ↓
NVIDIA passage embedding
 ↓
VectorStoreService
 ↓
Qdrant
    ├── 2048-d vector
    └── payload
         ├── userId
         ├── documentId
         ├── chunkId
         └── pageNumber
```

---

# 10. RETRIEVAL — COMPLETE (WITH FULL IMPLEMENTATION RECORD)

Semantic retrieval is implemented and verified end-to-end against the
real ingested document (the resume PDF, document
`00109296-ee93-491b-810c-42c395dbcaf4`, user
`22a37781-c505-4b87-a97e-1bdeb215d173`).

## 10.1 Working retrieval flow

```text
Authenticated user (JWT → User principal → userId)
 ↓
POST /api/v1/search { "query": "...", "topK": 5 }
 ↓
RetrievalService.retrieve(userId, query, topK)
 ↓
EmbeddingService.embedQuery(query)            → 2048-d float[]
 ↓
VectorStoreService.searchSimilar(userId, embedding, topK)
 ↓
Qdrant searchAsync(SearchPoints)
 ├── cosine, anonymous vector, limit=topK
 └── filter: must matchKeyword("userId", userId)
 ↓
List<VectorMatch(chunkId, score)>             (similarity order)
 ↓
DocumentChunkRepository.findOwnedChunksByIds(userId, chunkIds)
 ├── JPQL projection: chunkId/documentId/pageNumber/text
 └── AND c.document.user.id = :userId          (second ownership gate)
 ↓
List<RetrievedChunk(chunkId, documentId, pageNumber, text, score)>
```

PostgreSQL is still the only source of chunk text. Qdrant payload is
used only for the `userId` filter; text is deliberately NOT read from
the payload.

## 10.2 Files created/modified for retrieval

| File | Role |
|---|---|
| `rag/vector/VectorStoreService.java` | Added `searchSimilar(UUID userId, float[] queryEmbedding, int topK)` |
| `rag/vector/VectorMatch.java` | Lombok value class `(UUID chunkId, float score)` — keeps gRPC types out of the abstraction |
| `rag/vector/QdrantVectorStoreService.java` | Implements `searchSimilar` with `SearchPoints` + `matchKeyword("userId", ...)`; extracts UUID from `PointId` via `hasUuid()`/`getUuid()` |
| `rag/vector/QdrantCollectionInitializer.java` | Now idempotently creates keyword payload index on `userId` (see 10.5) |
| `document/repository/DocumentChunkRepository.java` | Added `findOwnedChunksByIds(userId, chunkIds)` JPQL ownership-filtered query |
| `document/repository/OwnedChunkProjection.java` | Interface projection: `getChunkId/getDocumentId/getPageNumber/getText` — avoids lazy-loading `Document`/`User` entities |
| `rag/retrieval/RetrievalService.java` | Orchestrates embed → search → lookup; preserves Qdrant order; silently drops point IDs with no owned PostgreSQL row (orphans) |
| `rag/retrieval/RetrievedChunk.java` | Lombok result DTO: chunkId, documentId, pageNumber, text, score |
| `rag/retrieval/RetrievalController.java` | Thin `POST /api/v1/search`; default topK=5; resolves userId from `Authentication` principal |
| `rag/retrieval/dto/SearchRequest.java` | `query` (@NotBlank, max 2000), `topK` (optional, 1..20) |
| `rag/retrieval/dto/SearchResponse.java` | `{ "results": [ ... ] }` |
| `src/test/java/com/example/backend/RetrievalServiceTest.java` | 3 live end-to-end tests (see 10.4) |

Design decisions to preserve:

- `DocumentChunk.id == Qdrant point ID (UUID)` — retrieval returns
  point IDs and uses them directly as PostgreSQL chunk IDs.
- `@Transactional` is intentionally NOT on `RetrievalService.retrieve`
  — it would hold a DB connection across the NVIDIA + Qdrant network
  calls; the repository query manages its own transaction.
- Qdrant-specific API (`SearchPoints`, `ScoredPoint`, filters) lives
  only inside `QdrantVectorStoreService`.
- No Spring AI, no text in Qdrant payload, no client-provided document
  ID trusted anywhere.

## 10.3 API contract (verified shape)

Request (requires `Authorization: Bearer <access-token>`):

```json
POST /api/v1/search
{ "query": "What is this document about?", "topK": 5 }
```

Response 200 (real observed data, resume PDF — 2 chunks total):

```json
{
  "results": [
    {
      "chunkId": "84382c00-d17a-4dad-b6b0-7e8f3bd38d5a",
      "documentId": "00109296-ee93-491b-810c-42c395dbcaf4",
      "pageNumber": 1,
      "text": "Rahul Srivastava ... 08252272454 | writetorahul3125@gmail.com | LinkedIn | GitHub | Bengaluru, Karnataka, India Profes...",
      "score": 0.01692745
    },
    {
      "chunkId": "87e700e7-6fd0-4c7d-b5c0-b15f49efc359",
      "documentId": "00109296-ee93-491b-810c-42c395dbcaf4",
      "pageNumber": 1,
      "text": "eal time – Significantly reduced onboarding turnaround time and dependency on engineering teams while improving data acc...",
      "score": 0.003237601
    }
  ]
}
```

Observations for future tuning (planned section 15, do not act early):

- Cosine scores from `nvidia/nemotron-3.5-embed-1b` on this pair were
  low in absolute terms (≈0.017 and ≈0.003) but correctly ordered —
  relative ranking works; a fixed similarity threshold would be wrong
  until more documents exist.
- Only 2 points exist for this user, so topK>2 cannot yet be exercised
  for truncation against Qdrant; topK=1 truncation IS covered by tests.

## 10.4 Verification evidence (all passing)

`./gradlew test --tests com.example.backend.RetrievalServiceTest`

1. `shouldRetrieveRealChunksForOwner` — query "What is this document
   about?" returns both real chunks, text non-blank, documentId equals
   the real document, scores descending, IDs equal the known
   `DocumentChunk.id` UUIDs.
2. `shouldRespectTopK` — topK=1 returns exactly 1 result.
3. `shouldReturnNothingForDifferentUser` — random UUID as userId
   returns an empty list (Qdrant filter + PostgreSQL gate).

Full suite `./gradlew test` (incl. `NvidiaEmbeddingServiceTest`,
`QdrantConnectionTest`, chunking, context boot) passes.

## 10.5 CRITICAL GOTCHA — Qdrant payload index requirement

Qdrant Cloud (server enforcing indexed filtering) REJECTS filtered
search on unindexed payload fields:

```text
INVALID_ARGUMENT: Bad request: Index required but not found for "userId"
```

Fix already implemented: `QdrantCollectionInitializer.ensureUserIdIndex()`
calls `createPayloadIndexAsync(collection, "userId", Keyword, ...)`
idempotently at startup — both when the collection is newly created and
when it already exists.

Rule for any future filtered field (e.g. `documentId`): create the
keyword payload index BEFORE using it in a search filter, or search
will fail at runtime, not at compile time.

## 10.6 How to test retrieval

Automated (preferred, uses live Neon + Qdrant + NVIDIA from
`application.yaml`):

```bash
cd backend
./gradlew test --tests com.example.backend.RetrievalServiceTest
```

Manual (endpoint level):

```bash
# 1. start the app
./gradlew bootRun

# 2. obtain an access token (use an account that already owns the uploaded PDF)
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"<owner-email>","password":"<password>"}'

# 3. search
curl -X POST http://localhost:8080/api/v1/search \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"query":"What is this document about?","topK":5}'

# 4. negative isolation check: register a NEW user, login, search the
#    same query → "results": [] (their vectors do not exist yet)
```

Also reachable via Swagger UI (`/swagger-ui.html`) as
`POST /api/v1/search` after authorizing a bearer token.

Expect: 2 results for the resume owner (the document produced exactly
2 chunks), ordered by descending score, each result carrying
chunkId/documentId/pageNumber/text/score.

Do NOT start LLM generation yet — it is the next phase only because
retrieval is now proven.

---

# 11. RETRIEVAL IMPLEMENTATION ORDER

> STATUS: ALL STEPS 1–4 COMPLETE — see section 10 for the final
> implementation record. Kept below as historical planning context.

## Step 1 — Extend VectorStoreService

Current interface only supports storing vectors.

Add a retrieval method for similarity search.

The exact Qdrant search API should be based on the currently installed Qdrant Java client version `1.20.0`.

Before changing code, inspect the current:

- `VectorStoreService.java`
- `QdrantVectorStoreService.java`

Do not blindly replace either class.

---

## Step 2 — Implement Qdrant similarity search

Expected responsibility:

Input:

- query embedding
- top-K
- ownership information if applicable

Output:

- matching Qdrant point IDs
- similarity scores
- enough metadata to identify the chunks

Important:

- Use cosine similarity
- Return top-K
- Preserve user/document isolation
- Do not retrieve another user's vectors
- Keep Qdrant-specific API details inside `QdrantVectorStoreService`

---

## Step 3 — Add PostgreSQL chunk lookup

After Qdrant returns matching point IDs:

```text
Qdrant point IDs
 ↓
DocumentChunkRepository
 ↓
DocumentChunk entities
 ↓
chunk text
```

PostgreSQL remains the source of truth for the actual chunk text.

Do not store/retrieve chunk text from Qdrant payload unless there is a deliberate architectural reason later.

---

## Step 4 — Build a Retrieval Service

Introduce a dedicated service responsible for:

```text
query
 ↓
embedQuery()
 ↓
vector search
 ↓
chunk lookup
 ↓
ordered retrieval results
```

Keep controller responsibilities thin.

Possible conceptual result:

```text
RetrievedChunk
├── chunkId
├── documentId
├── pageNumber
├── text
└── score
```

---

# 12. RETRIEVAL SECURITY

> STATUS: IMPLEMENTED AND VERIFIED — double enforcement (Qdrant
> `userId` keyword filter + `c.document.user.id` in PostgreSQL) plus
> the negative isolation test in section 10.4.

This is critical.

A user must never retrieve another user's chunks.

Ownership should be enforced at retrieval time.

Preferred architecture:

```text
Authenticated user
 ↓
query embedding
 ↓
Qdrant search
 ↓
user-scoped results
 ↓
PostgreSQL validation/lookup
```

Do not rely only on the client-provided document ID.

---

# 13. RETRIEVAL VERIFICATION

> STATUS: DONE — all 7 checks below passed; evidence and exact
> commands are recorded in sections 10.4 and 10.6.

Use a real document that already has Qdrant vectors.

Example conceptual query:

```text
"What is this document about?"
```

Verify:

1. Query embedding is generated using `embedQuery()`.
2. Qdrant returns matching points.
3. Returned point IDs correspond to real `DocumentChunk.id` values.
4. PostgreSQL returns the corresponding text.
5. Results are ordered by similarity.
6. `topK` is respected.
7. User ownership is enforced.

Only after this works should the project move to LLM generation.

---

# 14. LLM GENERATION — COMPLETE (FULL IMPLEMENTATION RECORD)

Basic RAG now works end-to-end:
question → retrieval (section 10) → context → NVIDIA LLM → grounded
answer + sources.

## 14.1 Working RAG flow

```text
Authenticated user
 ↓
POST /api/v1/rag/ask { "question": "...", "topK": 5 }
 ↓
RagService.ask(userId, question, topK)
 ↓
RetrievalService.retrieve(...)           (reused unchanged)
 ↓ (empty)  → short-circuit: fixed message, sources=[] , NO LLM call
 ↓ (chunks)
ContextBuilder
 ├── system prompt: answer ONLY from context + cite [S1][S2] + exact
 │                  fallback sentence when context insufficient
 └── user prompt:   numbered [Sn] blocks with document/page metadata
 ↓
LlmClient.chat(system, user)  →  NvidiaLlmClient
 ↓
RagAnswer { answer, sources[] }   (sources ordered = [S1]→sources[0])
```

## 14.2 Files created for generation

| File | Role |
|---|---|
| `rag/llm/LlmClient.java` | Abstraction: `String chat(String systemPrompt, String userPrompt)` |
| `rag/llm/NvidiaLlmClient.java` | RestClient `POST /chat/completions`; parses via `tools.jackson.databind.JsonNode`; no wire DTO needed |
| `rag/ask/ContextBuilder.java` | System prompt + `[Sn] (document=..., page=...)` context blocks |
| `rag/ask/RagService.java` | Orchestration; empty-retrieval short-circuit (saves LLM cost, prevents hallucination) |
| `rag/ask/RagAnswer.java` / `RagSource.java` | Lombok result DTOs (section 20 style) |
| `rag/ask/RagController.java` | Thin `POST /api/v1/rag/ask` |
| `rag/ask/dto/AskRequest.java` | `question` @NotBlank max 2000; `topK` optional 1..20, default 5 |
| `rag/ask/dto/AskResponse.java` | `{ answer, sources[] }` |
| `RagServiceTest.java` (live) | Grounded answer + sources for the real document; short-circuit for foreign user; topK=20 cites exactly the 2 real chunk IDs |

## 14.3 CRITICAL — NVIDIA chat model availability on this free account

`meta/llama-3.1-8b-instruct` returned:

```text
410 Gone: The model 'meta/llama-3.1-8b-instruct' has reached its
end of life on 2026-08-26
```

And the account rejects most catalog models with:

```text
404 Function Not Found for account
```

including `nvidia/llama-3.1-nemotron-70b-instruct`,
`nv-mistralai/mistral-nemo-12b-instruct`, `mistralai/mistral-7b-instruct-v0.3`,
`google/gemma-3-4b-it`, `ibm/granite-3.0-8b-instruct`,
`microsoft/phi-3.5-moe-instruct`, `nvidia/nemotron-nano-3-30b-a3b`.

**Working chat model for this key: `openai/gpt-oss-20b`** — verified live.
Config:

```yaml
app:
  ai:
    nvidia:
      chat-model: openai/gpt-oss-20b
      chat-max-tokens: 2048
```

gpt-oss consumes `max_tokens` for internal reasoning too — keep
`chat-max-tokens >= 2048` or `message.content` comes back null.
Model swap = config change only; `LlmClient` isolates it.

## 14.4 CRITICAL — Jackson 3 in Spring Boot 4

This project deserializes via Jackson 3 (`tools.jackson.*`), NOT
`com.fasterxml.jackson.databind.JsonNode` (that import caused
`HttpMessageConversionException: Type definition error`).
Rule: any new JSON-tree parsing must import
`tools.jackson.databind.JsonNode`; use `asString()` not deprecated
`asText()`.

## 14.5 Verified answer evidence

Query "What is this document about? Who is the person?" against the
real resume document returned (live, 2026-10-31):

```text
The document is a résumé (CV) that outlines the professional
background, technical skills, work experience, education, and projects
of an individual. The person described is Rahul Srivastava, a
Full-Stack Developer currently working at Northern Arc Capital, with
expertise in Spring Boot, Node.js, React.js, Angular, and related
technologies.
```

Sources: the 2 real chunk IDs with documentId/pageNumber/score.

## 14.6 How to test generation

```bash
./gradlew test --tests com.example.backend.RagServiceTest
```

Manual:

```bash
curl -X POST http://localhost:8080/api/v1/rag/ask \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"question":"What is this document about?","topK":5}'
```

Cosine score note: scores can be near-zero or negative for real
queries; tests assert the [-1, 1] range and ORDER, never `score > 0`.

---

# 15. AFTER BASIC RAG WORKS

Potential later improvements:

### Retrieval quality

- top-K tuning
- similarity threshold
- metadata filtering
- document-level filtering
- chunk-size tuning
- overlap tuning
- optional reranking

### RAG response quality

- context limits
- prompt hardening
- citation/source references
- hallucination reduction
- "answer only from provided context" behavior

### Production hardening

- failure handling between PostgreSQL and Qdrant
- retry/idempotency strategy
- cleanup of orphaned vectors
- document deletion → Qdrant deletion
- ingestion status tracking
- observability
- metrics
- structured logging

Do not add these prematurely.

---

# 16. IMPORTANT ARCHITECTURAL ISSUE TO ADDRESS LATER

Current ingestion spans two systems:

```text
PostgreSQL transaction
+
Qdrant external operation
```

`@Transactional` does NOT create a distributed transaction with Qdrant.

Current happy path works, but later we should handle cases such as:

```text
PostgreSQL save succeeds
↓
Qdrant insertion fails
```

or:

```text
Qdrant insertion succeeds
↓
PostgreSQL transaction rolls back
```

For the current project stage, do not over-engineer this.

Once basic RAG works, introduce a simple, production-explainable consistency/retry strategy.

---

# 17. DOCUMENT DELETION — COMPLETE (VECTOR CLEANUP ADDED)

`DELETE /api/v1/documents/{id}` now removes:

```text
Document (PostgreSQL; chunks removed via ON DELETE CASCADE)
 ↓
Qdrant points via deleteDocumentVectors(userId, documentId)
   = deleteAsync(collection, Filter: must userId keyword AND
                              must documentId keyword)
```

Implementation record:

- `VectorStoreService.deleteDocumentVectors(UUID userId, UUID documentId)`
  added; Qdrant details stay inside `QdrantVectorStoreService`.
- `documentId` keyword payload index is now created at startup
  (`QdrantCollectionInitializer.ensurePayloadIndexes()` covers
  `userId` + `documentId`) — same indexed-filter requirement as 10.5.
- Order and failure semantics (deliberate, simple, interview-explainable):
  PostgreSQL delete first; Qdrant delete second inside a try/catch that
  logs a WARN and does NOT roll back or fail the API.
  - Qdrant delete fails → orphan points remain but are UNREACHABLE
    (retrieval requires the PostgreSQL ownership row, which is gone).
  - Known edge: Qdrant delete succeeds then PG commit fails → document
    survives without vectors until re-upload. Accepted at this stage;
    the full consistency/retry strategy is section 16 work.
- Verified by `DocumentVectorDeletionTest` (non-destructive: deletes a
  random documentId, proving filter execution + index existence without
  touching the real indexed document).

---

# 18. FINAL TARGET ARCHITECTURE

```text
                         ┌──────────────────┐
                         │      Client      │
                         └────────┬─────────┘
                                  │
                                  ▼
                         ┌──────────────────┐
                         │ Spring Boot API  │
                         └────────┬─────────┘
                                  │
             ┌────────────────────┼────────────────────┐
             │                    │                    │
             ▼                    ▼                    ▼
       Document Service     Retrieval Service     RAG Service
             │                    │                    │
             │                    │                    │
             ▼                    ▼                    ▼
       PDF Extraction       EmbeddingService      Context Builder
             │                    │                    │
             ▼                    │                    ▼
         Chunking                 │              NVIDIA LLM
             │                    │
             ▼                    ▼
       PostgreSQL             Qdrant
       ┌───────────┐       ┌───────────────┐
       │ Documents │       │ Embeddings    │
       │ Chunks    │◄──────│ Metadata      │
       │ Text      │       │ Point IDs     │
       └───────────┘       └───────────────┘
```

---

# 19. NEXT SESSION — EXACT STARTING POINT

Do not restart or redesign anything.

BASIC RAG IS COMPLETE AND VERIFIED:

- ingestion (sections 2–8) ✔
- retrieval + `POST /api/v1/search` (section 10) ✔
- generation + `POST /api/v1/rag/ask` (section 14) ✔
- deletion removes Qdrant points (section 17) ✔

Next candidates, in recommended order (all from section 15 — pick one,
don't batch):

1. **Production hardening of RAG errors**: LLM/Qdrant/NVIDIA outages
   currently surface as 500 via `IllegalStateException`; consider typed
   exceptions + mapping in `GlobalExceptionHandler` and timeout config
   on the NVIDIA RestClient.
2. **Ingestion status**: `DocumentStatus` exists — expose
   processing/failed states and make upload survive embedding/Qdrant
   failure (currently the whole upload path is one flow).
3. **PostgreSQL↔Qdrant consistency strategy** (section 16) — retry/
   idempotency + orphan sweep.
4. Retrieval quality: similarity threshold, document-scoped search
   (`documentId` filter — index already exists), reranking.

Follow section 20 coding conventions while doing it.

---

# 20. CODING CONVENTIONS — BINDING FOR ALL FUTURE SESSIONS

## 20.1 Lombok, not records (project rule)

All NEW DTOs, response objects, service result carriers, and value
classes must be Lombok classes, NOT Java records.

Canonical shape (matches `LoginRequest`, `RetrievedChunk`,
`VectorMatch`, `SearchResponse`):

```java
@Getter
@Builder
@NoArgsConstructor   // only where deserialization needs it
@AllArgsConstructor
public class SomeDto {

    @NotBlank(message = "...")   // jakarta validation on request DTOs
    private String field;
}
```

Rules:

- Request DTOs: `@Getter @NoArgsConstructor @AllArgsConstructor` +
  jakarta validation annotations (see `SearchRequest`).
- Response/result DTOs: `@Getter @Builder @AllArgsConstructor` with
  `private final` fields (see `RetrievedChunk`, `VectorMatch`).
- Access via getters everywhere (`match.getChunkId()`, never
  `match.chunkId()`).
- Rationale: consistent Jackson deserialization, builder ergonomics,
  validation placement, and the established style of this codebase.

Known legacy exceptions (pre-existing records, do NOT mass-rewrite
them; when touching one for a feature, convert it to Lombok then):

- `common/response/ApiErrorResponse` (+ nested `FieldError`)
- `document/dto/ChunkData`
- `rag/dto/NvidiaEmbeddingResponse` (+ nested `EmbeddingData`)

## 20.2 Other enforced conventions

- Constructor injection via `@RequiredArgsConstructor` + `private final`.
- Controllers stay thin: resolve `userId` from
  `((User) authentication.getPrincipal()).getId()`, delegate to a
  service, no business logic in controllers.
- External-system types (Qdrant gRPC, NVIDIA JSON) never leak past
  their service implementation classes.
- Ownership is always double-enforced (vector-store filter AND SQL
  predicate); never trust a client-supplied ID.
- Any Qdrant payload field used in a search filter must have a keyword
  payload index created in `QdrantCollectionInitializer` first
  (section 10.5).
- JSON-tree parsing must use Jackson 3: `tools.jackson.databind.JsonNode`
  + `asString()`; `com.fasterxml.jackson.databind.JsonNode` breaks the
  RestClient converter at runtime (section 14.4).
- Cosine scores may be negative/near-zero; assert ORDER and [-1, 1]
  bounds in tests, never `score > 0` (section 14.6).
