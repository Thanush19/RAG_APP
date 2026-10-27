# RAG Android + Spring Boot Project Plan

## 1. Project Goal

Build a production-oriented AI/RAG application with:

- Android Jetpack Compose
- MVI architecture
- Spring Boot backend
- Java 21
- PostgreSQL / Neon
- Qdrant Cloud vector database
- OpenAI API for embeddings and LLM generation
- OpenAPI / Swagger
- Spring Actuator
- API request tracking
- Cloud deployment
- Play Store-ready Android application

The project should be implemented incrementally: start with a small working V1, validate it, then add production features.

---

# 2. High-Level Architecture

```text
Android App
  |
  | HTTPS / REST
  v
Spring Boot Backend
  |
  +---- Authentication
  |
  +---- PostgreSQL / Neon
  |       - Users
  |       - Documents
  |       - Chunks
  |       - Conversations
  |       - Messages
  |       - API metrics
  |
  +---- Qdrant Cloud
  |       - Vector embeddings
  |       - Semantic retrieval
  |
  +---- OpenAI
  |       - Text embeddings
  |       - LLM answer generation
  |
  +---- Object Storage (later)
          - PDF files
```

Important security rule:

**Android must never directly call OpenAI, Qdrant, PostgreSQL, or other services containing server-side secrets.**

---

# 3. Core Design Principle

Keep authentication independent from the RAG domain.

```text
auth/
    Authentication
    Users
    JWT
    Refresh tokens
    Security

document/
    PDF metadata
    Upload
    Processing status
    Ownership

rag/
    Text extraction
    Chunking
    Embeddings
    Qdrant retrieval
    LLM generation

chat/
    Conversations
    Messages

api/
    API tracking
    Metrics

common/
    Exceptions
    Validation
    Utilities
```

RAG services should only consume the authenticated user's identity. They should not contain authentication logic.

---

# 4. Implementation Roadmap

## Phase 1 - Backend Foundation

### Objective

Create the Spring Boot foundation before implementing RAG.

### Stack

- Java 21
- Spring Boot
- Gradle
- Spring Web
- Spring Data JPA
- PostgreSQL
- Flyway
- Lombok
- Bean Validation
- Spring Security
- Springdoc OpenAPI
- Spring Actuator

### Tasks

- [ ] Create Spring Boot project
- [ ] Configure Java 21
- [ ] Configure Gradle
- [ ] Configure PostgreSQL / Neon
- [ ] Configure Flyway
- [ ] Configure JPA
- [ ] Add Lombok
- [ ] Add validation
- [ ] Add global exception handling
- [ ] Add API versioning
- [ ] Add Swagger/OpenAPI
- [ ] Add Actuator
- [ ] Add basic application configuration
- [ ] Add environment-based configuration
- [ ] Create initial database migration
- [ ] Verify application starts successfully

Initial API prefix:

```text
/api/v1
```

---

# 5. Phase 2 - Authentication

Authentication is an independent foundation.

## Features

- [ ] Register
- [ ] Login
- [ ] Access JWT
- [ ] Refresh token
- [ ] Logout
- [ ] Current user
- [ ] Password update
- [ ] Password hashing
- [ ] JWT validation
- [ ] Authentication filter
- [ ] Authorization
- [ ] Swagger authentication

## APIs

```http
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
GET  /api/v1/auth/me
```

### Initial User Model

```text
users
-----
id
email
password_hash
name
created_at
updated_at
```

Later:

```text
email verification
password reset
OAuth
account locking
RBAC
```

Do not implement these initially.

---

# 6. Phase 3 - Android Foundation

## Stack

- Kotlin
- Jetpack Compose
- MVI
- ViewModel
- Hilt
- Retrofit
- OkHttp
- Coroutines
- Secure token storage

## Architecture

```text
Compose UI
    |
Intent
    |
ViewModel
    |
MVI State
    |
Use Case
    |
Repository
    |
Retrofit
    |
Spring Boot
```

## Initial Screens

- [ ] Login
- [ ] Register
- [ ] Documents
- [ ] Upload document
- [ ] Ask AI

## Suggested Android structure

```text
app/
├── auth/
├── documents/
├── rag/
├── chat/
└── core/
    ├── network/
    ├── security/
    ├── ui/
    └── common/
```

Do not create many Gradle modules initially. Split modules only when the project actually needs it.

---

# 7. Phase 4 - Document Management

Start with **PDF only**.

## Document Entity

```text
documents
---------
id
user_id
file_name
file_type
file_size
status
created_at
updated_at
```

Statuses:

```text
UPLOADED
PROCESSING
READY
FAILED
```

## APIs

```http
POST   /api/v1/documents
GET    /api/v1/documents
GET    /api/v1/documents/{id}
DELETE /api/v1/documents/{id}
```

## Development Storage

Initially:

```text
Local filesystem
```

Later:

```text
AWS S3 / Object Storage
```

PostgreSQL stores metadata and the storage key.

Do not store large PDF files inside PostgreSQL or Qdrant.

---

# 8. Phase 5 - PDF Processing

Processing flow:

```text
PDF Upload
    |
    v
Extract text
    |
    v
Split into chunks
    |
    v
Store chunks in PostgreSQL
    |
    v
Generate embeddings
    |
    v
Store vectors in Qdrant
    |
    v
Document = READY
```

## Chunk Entity

```text
document_chunks
---------------
id
document_id
chunk_index
page_number
text
qdrant_point_id
created_at
```

## Initial Chunking Strategy

Start simple.

Example:

```text
Chunk size: ~500-1000 tokens
Overlap: ~50-150 tokens
```

Tune these values after testing retrieval quality.

Do not start with complex semantic chunking.

---

# 9. Phase 6 - Qdrant Integration

Qdrant Cloud is the dedicated vector database.

## Collection

```text
document_chunks
```

Each point contains:

```text
id
vector
payload
```

Payload:

```json
{
  "userId": "...",
  "documentId": "...",
  "chunkId": "...",
  "pageNumber": 4
}
```

## Critical Security Rule

Every retrieval must be scoped to the authenticated user.

```text
currentUserId
      |
      v
Qdrant filter
      |
      v
only user's document chunks
```

This prevents cross-user document leakage.

---

# 10. Phase 7 - Basic RAG

This is the first major AI feature.

## Query API

```http
POST /api/v1/rag/query
```

Request:

```json
{
  "question": "How does JWT authentication work?"
}
```

## Flow

```text
User Question
      |
      v
Generate question embedding
      |
      v
Qdrant similarity search
      |
      v
Top K relevant chunks
      |
      v
Build LLM prompt
      |
      v
OpenAI LLM
      |
      v
Answer + Sources
```

## Response

```json
{
  "answer": "JWT authentication works by...",
  "sources": [
    {
      "documentId": "123",
      "documentName": "security.pdf",
      "pageNumber": 5
    }
  ]
}
```

---

# 11. RAG Service Responsibilities

Suggested services:

```text
PdfTextExtractionService
ChunkingService
EmbeddingService
VectorStoreService
RetrievalService
PromptService
LlmService
RagService
```

Main orchestration:

```text
RagService
    |
    +--> EmbeddingService
    |
    +--> RetrievalService
    |       |
    |       +--> Qdrant
    |
    +--> PromptService
    |
    +--> LlmService
            |
            +--> OpenAI
```

Keep external integrations behind service interfaces so they can be tested and replaced easily.

---

# 12. Phase 8 - Android RAG UI

Initial user journey:

```text
Login
  |
  v
Documents
  |
  +--> Upload PDF
  |
  +--> Select document
  |
  v
Ask AI
  |
  v
Question
  |
  v
Answer
  |
  v
Sources
```

UI states:

```text
Idle
Loading
Success
Error
```

For document processing:

```text
UPLOADED
PROCESSING
READY
FAILED
```

---

# 13. Phase 9 - Conversations

Once basic RAG works, add persistent chat.

## Tables

```text
conversations
-------------
id
user_id
title
created_at
updated_at
```

```text
messages
--------
id
conversation_id
role
content
created_at
```

Roles:

```text
USER
ASSISTANT
```

## APIs

```http
POST   /api/v1/conversations
GET    /api/v1/conversations
GET    /api/v1/conversations/{id}
DELETE /api/v1/conversations/{id}

POST   /api/v1/conversations/{id}/messages
GET    /api/v1/conversations/{id}/messages
```

Do not build conversation memory before basic single-question RAG is working.

---

# 14. Phase 10 - Observability

Add production-oriented monitoring.

## Actuator

Initial endpoints:

```http
GET /actuator/health
GET /actuator/info
GET /actuator/metrics
```

## API Request Tracking

Capture:

```text
HTTP method
endpoint
status code
latency
timestamp
user
```

Store initial tracking data in PostgreSQL.

Example:

```text
api_requests
------------
id
user_id
method
endpoint
status_code
latency_ms
created_at
```

Track:

- [ ] Request count
- [ ] Error count
- [ ] Average latency
- [ ] Slow endpoints
- [ ] OpenAI latency
- [ ] Qdrant latency

Do not introduce Kafka or Redis for this initially.

---

# 15. Phase 11 - AI Operations

After API tracking is working, add AI-specific metrics.

Track:

```text
OpenAI request count
Embedding requests
LLM requests
Input tokens
Output tokens
Estimated cost
Latency
Failures
```

Potential future API:

```http
GET /api/v1/admin/ai/usage
GET /api/v1/admin/api-performance
```

Later, OpenAI can analyze summarized API metrics.

Example question:

```text
Why is /api/v1/rag/query slower than the other APIs?
```

The system can analyze:

```text
API latency
+
Qdrant latency
+
OpenAI latency
+
database latency
```

Do this only after the basic metrics exist.

---

# 16. Phase 12 - RAG Quality Improvements

After basic RAG is stable:

## Retrieval Improvements

- [ ] Metadata filtering
- [ ] Better chunking
- [ ] Chunk overlap tuning
- [ ] Top-K tuning
- [ ] Similarity threshold
- [ ] Hybrid search
- [ ] Reranking
- [ ] Query rewriting
- [ ] Retrieval evaluation

Potential future flow:

```text
Question
   |
Query rewriting
   |
Hybrid retrieval
   |
Reranking
   |
Context selection
   |
LLM
```

Do not implement all of these in V1.

---

# 17. Phase 13 - Background Processing

PDF processing can eventually become asynchronous.

Current:

```text
Upload
  |
  v
Process immediately
```

Future:

```text
Upload
  |
  v
Create processing job
  |
  v
Background worker
  |
  +--> Extract
  +--> Chunk
  +--> Embed
  +--> Qdrant
  |
  v
READY
```

Add:

```text
processing_jobs
----------------
id
document_id
status
attempts
error_message
created_at
updated_at
```

Support:

- [ ] Retry
- [ ] Idempotency
- [ ] Failure handling
- [ ] Job status

A queue can be introduced later if scale requires it.

---

# 18. Phase 14 - Cloud File Storage

Replace local filesystem with object storage.

Example:

```text
Android
   |
   v
Spring Boot
   |
   v
Object Storage
   |
   +--> PDF
```

PostgreSQL:

```text
document
   |
   +--> storage_key
```

Possible service:

```text
AWS S3
```

Do not put PDFs in Qdrant.

---

# 19. Phase 15 - Production Hardening

## Security

- [ ] HTTPS
- [ ] Secure JWT handling
- [ ] Refresh token rotation
- [ ] Input validation
- [ ] File size limits
- [ ] PDF type validation
- [ ] Rate limiting
- [ ] User ownership checks
- [ ] Secrets management
- [ ] CORS configuration
- [ ] Security headers

## Reliability

- [ ] OpenAI timeout
- [ ] Qdrant timeout
- [ ] Retry policies
- [ ] Failure handling
- [ ] Database connection tuning
- [ ] Idempotent document processing
- [ ] Graceful degradation

---

# 20. Phase 16 - Deployment

Target architecture:

```text
                         +----------------+
                         |   Android App  |
                         +-------+--------+
                                 |
                              HTTPS
                                 |
                         +-------v--------+
                         | Spring Boot   |
                         | Cloud Server  |
                         +---+--------+--+
                             |        |
              +--------------+        +---------------+
              |                                       |
       +------v------+                         +------v------+
       | Neon Postgres|                         | Qdrant Cloud|
       +-------------+                         +-------------+
              |
       +------v------+
       | Object Store |
       +-------------+

                 OpenAI API
                     ^
                     |
              Spring Boot only
```

Deployment checklist:

- [ ] Dockerize backend
- [ ] Configure production environment
- [ ] Production Neon database
- [ ] Qdrant Cloud
- [ ] OpenAI API key
- [ ] Object storage
- [ ] Secrets
- [ ] HTTPS
- [ ] Health checks
- [ ] GitHub Actions
- [ ] CI build
- [ ] Test pipeline
- [ ] Deployment pipeline

---

# 21. Phase 17 - Play Store

Android release checklist:

- [ ] Release build
- [ ] App signing
- [ ] Application ID
- [ ] App icon
- [ ] Splash screen
- [ ] Screenshots
- [ ] App description
- [ ] Privacy policy
- [ ] Data safety declaration
- [ ] Internal testing
- [ ] Closed testing
- [ ] Production release

---

# 22. Testing Strategy

## Unit Tests

Backend:

- [ ] Authentication service
- [ ] Document service
- [ ] Chunking service
- [ ] Embedding service
- [ ] Retrieval service
- [ ] RAG service
- [ ] Conversation service

Important cases:

```text
Invalid PDF
Unauthorized document
Wrong user accessing document
Chunking behavior
Empty extracted text
Embedding failure
Qdrant failure
OpenAI failure
```

## Integration Tests

Test:

```text
Controller
   |
Service
   |
Repository
```

Mock external OpenAI/Qdrant calls where appropriate.

---

# 23. Database Migration Plan

Use Flyway.

Example:

```text
V1__create_users.sql
V2__create_documents.sql
V3__create_document_chunks.sql
V4__create_conversations.sql
V5__create_messages.sql
V6__create_api_requests.sql
V7__create_processing_jobs.sql
```

Never manually modify production schema.

---

# 24. Suggested Backend Package Structure

```text
com.example.backend
│
├── auth
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── repository
│   ├── security
│   └── service
│
├── document
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── repository
│   └── service
│
├── rag
│   ├── controller
│   ├── dto
│   ├── service
│   ├── embedding
│   ├── retrieval
│   ├── qdrant
│   └── openai
│
├── chat
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── repository
│   └── service
│
├── api
│   ├── filter
│   ├── entity
│   ├── repository
│   └── service
│
└── common
    ├── exception
    ├── config
    ├── validation
    └── util
```

---

# 25. API Overview

## Authentication

```text
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
GET  /api/v1/auth/me
```

## Documents

```text
POST   /api/v1/documents
GET    /api/v1/documents
GET    /api/v1/documents/{id}
DELETE /api/v1/documents/{id}
```

## RAG

```text
POST /api/v1/rag/query
```

## Conversations

```text
POST   /api/v1/conversations
GET    /api/v1/conversations
GET    /api/v1/conversations/{id}
DELETE /api/v1/conversations/{id}

POST   /api/v1/conversations/{id}/messages
GET    /api/v1/conversations/{id}/messages
```

## Observability

```text
GET /actuator/health
GET /actuator/info
GET /actuator/metrics
```

---

# 26. V1 Scope

Do NOT try to build everything at once.

## V1 Must Have

### Backend

- [ ] Spring Boot
- [ ] Java 21
- [ ] PostgreSQL / Neon
- [ ] Flyway
- [ ] Authentication
- [ ] JWT
- [ ] PDF upload
- [ ] PDF text extraction
- [ ] Chunking
- [ ] OpenAI embeddings
- [ ] Qdrant
- [ ] Similarity search
- [ ] OpenAI LLM
- [ ] RAG response
- [ ] Sources
- [ ] Swagger
- [ ] Actuator
- [ ] Global exception handling

### Android

- [ ] Compose
- [ ] MVI
- [ ] Login
- [ ] Register
- [ ] Documents
- [ ] Upload PDF
- [ ] Ask AI
- [ ] Display answer
- [ ] Display sources

---

# 27. Features to Avoid in V1

Do not add these before the basic RAG product works:

- Microservices
- Kafka
- Redis
- Kubernetes
- Elasticsearch
- Complex agents
- MCP
- Voice assistant
- Web scraping
- Multimodal RAG
- Recommendation engine
- Complex RBAC
- Multi-tenancy
- Advanced workflow engines

Add infrastructure only when the application has a real requirement for it.

---

# 28. Final Project Evolution

```text
V1
Basic RAG
 |
 +--> Authentication
 +--> PDF
 +--> Embeddings
 +--> Qdrant
 +--> LLM
 +--> Android UI
 |
 v
V2
Conversations
 |
 v
V3
Observability
 |
 v
V4
AI API analysis
 |
 v
V5
RAG quality improvements
 |
 v
V6
Background processing
 |
 v
V7
Object storage
 |
 v
V8
Production hardening
 |
 v
V9
Cloud deployment
 |
 v
V10
Play Store
```

---

# 29. Resume Value

The project should eventually demonstrate:

### Android

- Jetpack Compose
- MVI
- ViewModel
- Networking
- Authentication
- Secure token handling
- Production UI state management

### Backend

- Java
- Spring Boot
- REST APIs
- Spring Security
- JWT
- PostgreSQL
- Flyway
- API design
- Exception handling
- Observability

### AI / RAG

- LLM integration
- Embeddings
- Vector databases
- Qdrant
- Semantic search
- Retrieval-Augmented Generation
- Prompt construction
- Source attribution
- RAG evaluation
- AI API usage/cost tracking

### Cloud / Production

- Docker
- Cloud deployment
- Object storage
- CI/CD
- Monitoring
- Secrets management
- Production security

This makes the project substantially stronger than a basic CRUD Android + Spring Boot project.

---

# 30. Recommended Development Order

Follow this exact order:

```text
1. Backend foundation
2. Authentication
3. Android foundation
4. Document upload
5. PDF extraction
6. Chunking
7. OpenAI embeddings
8. Qdrant integration
9. Basic retrieval
10. OpenAI answer generation
11. Android RAG UI
12. Sources
13. Deploy V1
14. Conversations
15. API tracking
16. AI usage/cost tracking
17. RAG improvements
18. Background processing
19. Object storage
20. Production hardening
21. Final cloud deployment
22. Play Store
```

**Important:** Deploy a working V1 before continuing to advanced features. The project should always remain runnable and demonstrable.
