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

- top-K tuning ✔
- similarity threshold ✔ (`minScore`, section 21.4)
- metadata filtering
- document-level filtering ✔ (`documentId` filter, section 21.4)
- chunk-size tuning
- overlap tuning
- optional reranking

### RAG response quality

- context limits
- prompt hardening ✔ (section 21 + `ContextBuilderTest`)
- citation/source references ✔ (section 14)
- hallucination reduction
- "answer only from provided context" behavior ✔ (section 14 + 21)

### Production hardening

- failure handling between PostgreSQL and Qdrant ✔ (section 21.3)
- retry/idempotency strategy ✔ (section 21.3)
- cleanup of orphaned vectors ✔ (orphan sweep, section 21.3)
- document deletion → Qdrant deletion ✔ (section 17)
- ingestion status tracking ✔ (section 21.2)
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

> RESOLVED (section 21.3): idempotent re-ingestion (pre-clean +
> chunkId-keyed upserts) + write retry + scheduled orphan sweep. No
> distributed transaction, no outbox.

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
- prompt-injection hardening in `ContextBuilder` (section 21 + tests) ✔
- SSE streaming ask `POST /api/v1/rag/ask/stream` (section 22) ✔
- production-hardening batch — typed errors/503 + timeouts,
  PROCESSING/READY/FAILED + reprocess, idempotent ingestion +
  orphan sweep, minScore + document-scoped search (ALL of section 19's
  old 4-item roadmap) ✔ → full record in section 21.

Current live state (as of 2026-10-31): the real test document
`00109296-ee93-491b-810c-42c395dbcaf4` is status READY with chunks
`f50768ae-…e1ab` / `573f998c-…4fc1` (see incident 21.6). Tests that
assert against it must stay dynamic (derive expectations from
`DocumentChunkRepository.findAllByDocumentId`).

Remaining candidates, in recommended order (all from section 15 — pick
one, don't batch):

1. **Observability**: structured request/trace logging around the
   RAG path (retrieval latency, LLM token usage), maybe actuator
   metrics for upstream 503s and sweep outcomes.
2. **Retrieval quality further**: optional reranking, chunk-size/
   overlap tuning, hybrid keyword+vector search.
3. **Async ingestion**: upload currently blocks on extraction +
   embedding + Qdrant; move `DocumentIngestionService` onto a task
   executor/queue and expose status polling.
4. **Auth/scope polish**: per-user rate limits on
   `/api/v1/rag/ask`, request-size caps aligned with model context.

Follow section 20 coding conventions (especially 20.3 transaction
boundaries) while doing it.

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

## 20.3 Transaction boundaries (learned the hard way — see section 21)

- NEVER let a class-level `@Transactional(readOnly = true)` wrap a
  method that orchestrates external-system writes (upload/reprocess).
  A readOnly transaction silently makes the whole orchestration one
  rollback-able PG transaction while non-transactional Qdrant/NVIDIA
  calls still take effect — status writes vanish and vectors orphan.
  Such methods must declare
  `@Transactional(propagation = Propagation.NOT_SUPPORTED)`.
- In PG, writes to the same table that must land in a specific order
  (delete-then-insert on `(document_id, chunk_index)`) require a
  `@Modifying` bulk JPQL delete. A derived `deleteByDocumentId` queues
  the deletes in the persistence context and Hibernate flushes them
  AFTER the new inserts → duplicate-key violation.

---

# 21. PRODUCTION HARDENING BATCH — COMPLETE (ERROR HANDLING, STATUS, CONSISTENCY, RETRIEVAL QUALITY)

Four roadmap items implemented and verified in one pass. Section 16's
"address later" consistency problem now has a deliberate, simple,
interview-explainable answer.

## 21.1 Typed upstream errors + timeouts → 503

- New `common/exception/UpstreamServiceException` (abstract) with
  subclasses `LlmUnavailableException`, `EmbeddingUnavailableException`,
  `VectorStoreUnavailableException`, plus `DocumentProcessingException`.
- `GlobalExceptionHandler`: `UpstreamServiceException` → HTTP 503
  `"UPSTREAM_UNAVAILABLE"` (WARN + requestId MDC);
  `DocumentProcessingException` → HTTP 500
  `"DOCUMENT_PROCESSING_FAILED"`.
- NVIDIA RestClient timeouts via `RestClientConfig`
  (`SimpleClientHttpRequestFactory`):
  `app.ai.nvidia.connect-timeout-seconds: 10`,
  `app.ai.nvidia.read-timeout-seconds: 120`.
- Qdrant gRPC deadlines: every `searchAsync`/`upsertAsync`/`deleteAsync`
  call passes `Duration.ofSeconds(app.vector.qdrant.timeout-seconds: 30)`
  and awaits with `get(timeoutSeconds + 5)` — no unbounded blocking.
- `NvidiaLlmClient`/`NvidiaEmbeddingService` wrap `RestClientException`
  into the typed exceptions; Qdrant write/delete paths retry
  (3 attempts, 300ms exponential backoff) then throw
  `VectorStoreUnavailableException`. Search intentionally does NOT
  retry (user-facing latency).

## 21.2 Ingestion status + reprocess endpoint

- `DocumentStatus` UPLOADED/PROCESSING/READY/FAILED now actually used.
- `POST /api/v1/documents/upload`: saves the document as PROCESSING,
  then runs ingestion OUTSIDE any transaction
  (`Propagation.NOT_SUPPORTED` — see 20.3). On any failure the PG
  document row and Cloudinary file are KEPT, row is marked FAILED, and
  the API returns 500 `DOCUMENT_PROCESSING_FAILED` pointing at:
- `POST /api/v1/documents/{documentId}/reprocess` — the recovery path:
  - READY → `ConflictException` ("already processed")
  - foreign/nonexistent → `EntityNotFoundException`
  - otherwise: set PROCESSING → ingest → set READY.
  Ingestion is idempotent, so reprocess is always safe (21.3).
- Ingestion logic moved out of `DocumentService` into
  `document/service/DocumentIngestionService` (single `@Transactional`
  method: pre-clean → extract → chunk → save/embed/store per chunk →
  READY).

## 21.3 PostgreSQL↔Qdrant consistency (answers section 16)

Strategy chosen: **idempotent re-ingestion + scheduled orphan sweep**
— no distributed transaction, no outbox. Explicit failure semantics:

- Pre-clean first: reprocess deletes ALL of the document's PG chunks
  (`@Modifying` bulk JPQL — see 20.3) and its Qdrant points
  (`deleteDocumentVectors`) before inserting, so partial or crashed
  ingestion is healed by replay. Upserts are keyed on
  `chunkId == pointId` so re-ingestion is naturally idempotent too.
- Write retry: `storeChunk`/`deleteDocumentVectors` retry
  (3 attempts / backoff) before surfacing 503.
- Orphan sweep: `QdrantOrphanSweepService` (`@Scheduled`, enabled by
  `app.vector.qdrant.orphan-sweep.enabled: true`, runs 5 min after boot
  then hourly): `VectorStoreService.scanStoredDocumentRefs()` scrolls
  the collection (256/page, dedup `documentId → userId`), and any
  documentId with no PG row gets `deleteDocumentVectors`. Whole sweep
  is try/catch — a failure logs ERROR and never affects serving.
- Deletion (section 17) unchanged: PG first, Qdrant best-effort;
  leftovers are unreachable and get swept.
- Known accepted gap: a document left FAILED whose vectors were partly
  written stays reachable ONLY until reprocess/sweep runs; retrieval
  joins PG ownership rows so no cross-user leak is possible.

## 21.4 Retrieval quality knobs

- `POST /api/v1/search` and `POST /api/v1/rag/ask` accept optional:
  - `documentId` (UUID) → Qdrant filter gains
    `must documentId keyword match` (index pre-created in
    `QdrantCollectionInitializer`).
  - `minScore` (float, validated [-1.0, 1.0]) → applied to raw cosine
    scores AFTER search, BEFORE the PG ownership lookup.
- `VectorStoreService.searchSimilar` has a 4-arg overload
  (userId, embedding, topK, documentId); `RetrievalService.retrieve`
  a 5-arg overload (…, documentId, minScore); 3-arg forms delegate with
  nulls so existing callers/tests are untouched.

## 21.5 New config keys (application.yaml)

```yaml
app:
  ai:
    nvidia:
      connect-timeout-seconds: 10
      read-timeout-seconds: 120
      chat-max-tokens: 2048
      stream-timeout-seconds: 180   # SSE, section 22
  vector:
    qdrant:
      timeout-seconds: 30
      orphan-sweep:
        enabled: true
        initial-delay-millis: 300000   # default
        interval-millis: 3600000       # default
```

## 21.6 The readOnly-transaction incident (live-data near-loss, recovered)

Symptom: after first wiring reprocess, the live test suite went 6/23
red; the real indexed document still existed but retrieval returned
nothing and status was stuck at UPLOADED with the OLD chunk rows.
Root causes:

1. `DocumentService` class-level `@Transactional(readOnly = true)`
   wrapped `uploadDocument`/`reprocessDocument`: PROCESSING/READY
   writes were silently discarded, and the whole multi-system
   orchestration ran inside one rollback-able transaction while the
   non-transactional Qdrant deletes/upserts persisted.
2. `IngestionConsistencyTest.shouldRejectReprocessOfReadyDocument`
   assumed the real doc was READY; it was legacy UPLOADED, so the
   guard passed and the test REPROCESSes the real document — with
   derived-delete flush ordering (20.3) that left it inconsistent.

Recovery: fixed propagation + bulk JPQL delete, then re-ran reprocess
on the real document — ingestion succeeded, status READY, 2 new chunks
(`f50768ae-2368-4e74-b68e-60f18950e1ab`,
`573f998c-30ac-4bbb-9b9f-e004cc6d4fc1`) with the same scores as before.
`RagServiceTest.shouldOnlyCiteOwnedChunks` was made dynamic
(`findAllByDocumentId`) so future re-ingestions can't break it.

Rules recorded in 20.3. Also: never judge a gradle run by a piped
command's exit code (`| tail` masks failures) — check
`BUILD SUCCESSFUL` / the XML in `build/test-results/test/`.

## 21.7 Verification

- Full `./gradlew test` suite green (live Neon + Qdrant Cloud + NVIDIA),
  including `ContextBuilderTest` prompt-injection assertions,
  `RetrievalServiceTest` (documentId scoping + minScore),
  `IngestionConsistencyTest` (reprocess guards, scan finds the real
  document, sweep is non-destructive), `DocumentVectorDeletionTest`.
- Temporary diagnostic tests (`StateDumpTest`,
  `ReprocessRecoveryTest`) deleted after recovery.

Note: in the 2026-10-31 batch run,
`RagServiceTest.shouldAnswerGroundedInRealDocument` flaked once with
`SocketTimeoutException: Read timed out` at the 120s NVIDIA read
timeout (gateway slowness behind the reasoning model) — the new typed
error path mapped it correctly. It passed on the next full run; treat
single LLM-timeout failures as transient, re-run before investigating
code. (Final state: sections 21 AND 22 verified together, 25/25 green.)

---

# 22. SSE STREAMING FOR RAG ASK — COMPLETE

`POST /api/v1/rag/ask/stream` delivers the same pipeline as `/ask`
(retrieval → prompt guards → generation) but token-by-token over
Server-Sent Events.

## 22.1 Event protocol

Request body: identical `AskRequest` (`question`, `topK`, `minScore`).
Response: `text/event-stream`, named events, JSON data payloads:

```text
event: sources   data: [RagSource...]        (fires BEFORE generation)
event: token     data: {"t": "..."}          (N times; fallback answer
                                             is one token if no context)
event: done      data: {"answer": "<full>"}
event: error     data: {"code","message"}    (only if something failed
                                             after the stream opened)
```

Because HTTP status is already 200 once streaming starts, mid-stream
failures CANNOT surface as 503 — they are `error` events with
`UPSTREAM_UNAVAILABLE` (typed `UpstreamServiceException`) or
`INTERNAL_ERROR`.

## 22.2 Implementation record

- `LlmClient.chatStream(systemPrompt, userPrompt, Consumer<String>)`
  added; returns accumulated full text.
- `NvidiaLlmClient.chatStream`: same guards/params as `chat()` but
  `"stream": true` and POSTs with the **JDK `HttpClient`** (not the
  shared `RestClient`): `SimpleClientHttpRequestFactory` applies its
  read timeout to the FIRST packet only, so a long token stream would
  hang or cut early. Whole-exchange bound =
  `read-timeout-seconds + stream-timeout-seconds` (new key, default
  180). Parses upstream SSE lines `data: {...}`, takes
  `choices[0].delta.content` (Jackson 3, `tools.jackson`), skips
  `data: [DONE]`; non-200 → `LlmUnavailableException` (stream opened
  only on 200).
- `RagService.askStream(userId, question, topK, minScore, listener)`
  with inner `RagService.RagStreamListener`
  (onSources/onToken/onDone/onError, all default no-ops). Same
  short-circuit: no chunks → sources emitted, fallback answer as a
  single token, no LLM call. Retrieval + prompt building unchanged,
  so section 21/14 injection guards apply verbatim.
- `RagController.askStream`: builds `SseEmitter(300_000ms)`, runs the
  orchestration on `applicationTaskExecutor` (boot auto-configured;
  servlet thread returns immediately), serializes events via the
  Jackson 3 `ObjectMapper` bean. Send failure (client disconnect) →
  `completeWithError`, no retry.
- `rag/ask/dto/StreamErrorPayload` — Lombok DTO per 20.1.
- No new auth rules needed: endpoint sits under the existing
  authenticated `/api/v1/rag/**` chain.

## 22.3 Tests

- `RagStreamServiceTest` (live): grounded question → sources all on the
  real document, `tokens.size() > 1` (proves true streaming), done
  answer non-blank, no error; random user → exactly one fallback token,
  no LLM call. VERIFIED: final 2026-10-31 run 25/25 green
  (`BUILD SUCCESSFUL`); the earlier `RagServiceTest` timeout flake also
  passed on re-run, confirming it was transient gateway slowness.
- Manual check with curl:
  `curl -N -X POST /api/v1/rag/ask/stream -H "Authorization: Bearer <jwt>" -H "Content-Type: application/json" -d '{"question":"What is this document about?"}`


