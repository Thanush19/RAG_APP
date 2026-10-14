# Todo Backend — Development Plan

> Industry-standard Todo application backend built with Spring Boot, Java, PostgreSQL, JWT authentication, and a layered architecture.
>
> **Primary goal:** Build a production-oriented backend that demonstrates practical backend engineering skills, clean architecture, security, database design, API design, testing, scalability, and distributed-system concepts.

---

# 1. Project Overview

## Objective

Build a full-featured Todo application backend that supports:

- User authentication
- JWT access tokens
- Refresh tokens
- User profiles
- Tasks
- Task completion
- Priorities
- Due dates
- Categories
- Tags
- Subtasks
- Search/filtering/pagination
- Reminders
- Notifications
- Recurring tasks
- Audit/history
- Production hardening
- Scalable search
- Caching
- Background processing

The backend is designed to support an Android application built separately using:

- Kotlin
- Jetpack Compose
- MVI
- ViewModel
- Molecules

---

# 2. Current Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1.1 |
| Build | Gradle 9.7.1 |
| Database | PostgreSQL |
| Database hosting | Neon |
| ORM | Spring Data JPA / Hibernate |
| Database migrations | Flyway |
| Authentication | Spring Security |
| Authentication mechanism | JWT |
| API documentation | Swagger / OpenAPI |
| Validation | Jakarta Bean Validation |
| Testing | JUnit / Spring Boot Test |
| API style | REST |
| Architecture | Layered / Domain-oriented |
| Time handling | UTC / `OffsetDateTime` |

---

# 3. High-Level Architecture

Current backend follows a layered architecture:

```text
Controller
    ↓
Service
    ↓
Repository
    ↓
JPA / Hibernate
    ↓
PostgreSQL
```

Cross-cutting concerns:

```text
                    ┌─────────────────────┐
                    │    Spring Security  │
                    │      JWT Filter     │
                    └──────────┬──────────┘
                               │
                               ▼
Client → Controller → Service → Repository → PostgreSQL
             │           │
             │           ├── Validation
             │           ├── Business Rules
             │           └── Authorization
             │
             └── DTOs
```

Exception flow:

```text
Controller
    ↓
Service
    ↓
Exception
    ↓
GlobalExceptionHandler
    ↓
Standard API error response
```

---

# 4. Package Structure

Base package:

```text
com.example.backend
```

Current domain-oriented structure:

```text
com.example.backend
│
├── auth
│   ├── controller
│   ├── dto
│   │   └── user
│   ├── entity
│   ├── repository
│   ├── security
│   └── service
│
├── category
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── repository
│   └── service
│
├── tag
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── repository
│   └── service
│
├── task
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── repository
│   ├── service
│   └── specification
│
└── common
    ├── dto
    └── exception
```

Future domains:

```text
reminder
notification
device
recurrence
audit
```

---

# 5. Database Migration Strategy

Flyway is used for versioned schema migrations.

Current migration history:

```text
V1  → Initial schema / users
V2  → Authentication-related changes
V3  → Refresh tokens
...
V9  → Task subtasks
```

## Important rule

Once a migration has been applied, **do not modify it**.

Instead:

```text
Existing migration
       ↓
New requirement
       ↓
New migration
```

Example:

```text
V9__add_parent_task_id_to_tasks.sql
V10__...
V11__...
```

Database changes must always be represented by Flyway migrations.

---

# 6. Authentication & Security

## Completed

### Registration

```http
POST /api/v1/auth/register
```

### Login

```http
POST /api/v1/auth/login
```

### Access JWT

Used for authenticated API requests:

```http
Authorization: Bearer <access-token>
```

### Refresh token

Refresh-token architecture is implemented.

Refresh token expiration:

```text
30 days
```

### Logout

Refresh-token based logout is implemented.

### JWT authentication filter

The backend:

```text
Request
  ↓
Authorization header
  ↓
Bearer JWT
  ↓
JWT validation
  ↓
User authentication
  ↓
SecurityContext
  ↓
Controller
```

### Current-user endpoint

```http
GET /api/v1/auth/me
```

Returns authenticated user information.

### Swagger

Bearer JWT authentication is configured for Swagger/OpenAPI.

---

# 7. User / Profile

## Completed

User profile functionality has been added.

Current profile capabilities include:

- Get authenticated user
- Update user name
- Validation
- Updated timestamp

Profile DTOs:

```text
UserProfileResponse
UpdateProfileRequest
```

Name validation:

```text
@NotBlank
@Size(max = 100)
```

---

# 8. Task Domain

Task is the primary domain entity.

Current task model includes:

```text
Task
├── id
├── user
├── category
├── parentTask
├── tags
├── title
├── description
├── completed
├── priority
├── dueAt
├── createdAt
└── updatedAt
```

---

# 9. Task CRUD

## Create

```http
POST /api/v1/tasks
```

Supports:

- title
- description
- priority
- due date
- category
- tags
- parent task

## Get all

```http
GET /api/v1/tasks
```

### Important decision

This endpoint is intentionally kept simple.

It currently remains:

```text
GET /api/v1/tasks
```

and returns:

```text
List<TaskResponse>
```

It will **not** be changed into a paginated/search endpoint.

## Get single task

```http
GET /api/v1/tasks/{taskId}
```

## Update

```http
PUT /api/v1/tasks/{taskId}
```

## Completion

```http
PATCH /api/v1/tasks/{taskId}/completion
```

## Delete

```http
DELETE /api/v1/tasks/{taskId}
```

---

# 10. Task Priorities

Supported priorities:

```text
LOW
MEDIUM
HIGH
```

Default:

```text
MEDIUM
```

---

# 11. Task Due Dates

Tasks support:

```text
dueAt
```

Time values are handled using:

```text
OffsetDateTime
```

Backend timestamps are maintained in UTC.

---

# 12. Categories

Categories support two types:

```text
SYSTEM
CUSTOM
```

## System categories

System categories are globally available.

They cannot be modified by normal users.

## Custom categories

Custom categories belong to a specific user.

Ownership is enforced by the service layer.

Tasks can reference either:

```text
System category
        OR
User's custom category
```

---

# 13. Tags

Tags are user-owned.

Database relationship:

```text
User
 │
 └── Tags
```

Task ↔ Tag:

```text
Task
 │
 ├──── task_tags ──── Tag
 │
 └──── task_tags ──── Tag
```

This is a `ManyToMany` relationship.

Tags support:

- Creation
- Retrieval
- Update
- Delete
- Assignment to tasks
- Removal from tasks

Tag names are unique per user.

Database constraint:

```text
(user_id, name)
```

---

# 14. Subtasks

Subtasks were implemented in V9.

Database relationship:

```text
Task
 │
 ├── parentTask = null
 │
 └── Subtask
       parentTask → Task
```

Database column:

```text
parent_task_id
```

Foreign key:

```text
fk_tasks_parent_task
```

Delete behavior:

```text
ON DELETE CASCADE
```

---

# 15. Subtask Rules

Current design intentionally supports only **one level** of subtasks.

Valid:

```text
Task
 ├── Subtask 1
 ├── Subtask 2
 └── Subtask 3
```

Invalid:

```text
Task
 └── Subtask
      └── Sub-subtask
```

Also invalid:

```text
Task
 └── itself
```

The service prevents:

- Self-parenting
- Nested subtasks
- Cross-user parent tasks

---

# 16. Ownership / Authorization

Every user-owned resource must be isolated by user.

For example:

```text
User A
 └── Task A

User B
 └── Task B
```

User A must never be able to:

```text
GET Task B
UPDATE Task B
DELETE Task B
assign User B's tag
assign User B's category
use User B's task as parent
```

The service layer therefore consistently uses user-scoped repository methods such as:

```java
findByIdAndUserId(...)
```

This is a critical security rule.

---

# 17. Global Exception Handling

The backend uses centralized exception handling.

Current application exceptions include concepts such as:

```text
EntityNotFoundException
ConflictException
```

Examples:

```text
Task not found → 404
Category not found → 404
Tag not found → 404
Parent task not found → 404
Self-parenting → 409
Nested subtask → 409
```

Controllers should not manually build error responses for business exceptions.

Business exceptions should propagate to the global handler.

---

# 18. V10 — Search / Filtering / Pagination

## Status

**DEFERRED**

This feature was considered but intentionally postponed.

Potential future endpoint:

```http
GET /api/v1/tasks/search
```

Planned capabilities:

- Pagination
- Search
- Completion filter
- Priority filter
- Category filter
- Tag filter
- Parent task filter
- Due-date range
- Sorting
- Top-level task filtering
- Overdue filtering

Potential architecture:

```text
TaskController
      ↓
TaskService
      ↓
TaskSpecification
      ↓
JpaSpecificationExecutor
      ↓
PostgreSQL
```

However, for a serious search implementation, Elasticsearch/OpenSearch can be introduced later.

---

# 19. Why Search Is Deferred

The current PostgreSQL implementation is sufficient for normal task CRUD.

Introducing Elasticsearch too early would add:

- another service
- another deployment
- indexing synchronization
- failure handling
- eventual consistency
- operational complexity

Therefore:

```text
Core application
       ↓
PostgreSQL
       ↓
Complete domain features
       ↓
Production hardening
       ↓
Elasticsearch
```

is preferred.

---

# 20. Future Search Architecture

Eventually:

```text
                  PostgreSQL
                 Source of Truth
                       │
                       │ Task change
                       ▼
                 Indexing Pipeline
                       │
                       ▼
                Elasticsearch
                       │
                       ▼
                Search API
```

Elasticsearch can provide:

- Full-text search
- Fuzzy search
- Typo tolerance
- Relevance ranking
- Autocomplete
- Filtering
- Facets
- Efficient large-scale search

PostgreSQL remains the source of truth.

---

# 21. Reminders & Notifications

## Status

**Deferred / Future Feature**

This should be implemented as a proper notification subsystem rather than directly coupling reminders to FCM.

Target architecture:

```text
Task
 ↓
Reminder
 ↓
Scheduler
 ↓
Notification Service
 ├── Notification DB
 └── Push Notification Service
          ↓
         FCM
          ↓
     Android App
```

---

# 22. Reminder Domain

Future `Reminder` entity:

```text
Reminder
├── id
├── user
├── task
├── remindAt
├── status
├── createdAt
└── updatedAt
```

Possible statuses:

```text
SCHEDULED
SENT
CANCELLED
```

Future APIs:

```http
POST   /api/v1/tasks/{taskId}/reminders
GET    /api/v1/reminders
DELETE /api/v1/reminders/{reminderId}
```

Initial implementation should support **one-time reminders**.

Recurring reminders can be added later.

---

# 23. Push Notification Architecture

The Android application should use Firebase Cloud Messaging (FCM).

The backend does not directly connect to Android devices.

Architecture:

```text
Android
   │
   │ FCM registration token
   ▼
Backend
   │
   │ Firebase Admin / FCM API
   ▼
Firebase Cloud Messaging
   │
   ▼
Android device
```

---

# 24. Device Tokens

Future domain:

```text
device_tokens
```

Conceptual model:

```text
DeviceToken
├── id
├── userId
├── token
├── platform
├── createdAt
├── updatedAt
└── lastSeenAt
```

A user can have multiple devices:

```text
User
 ├── Android phone
 ├── Android tablet
 └── Other device
```

Therefore:

```text
1 user ≠ 1 FCM token
```

---

# 25. Notifications

Notifications should be persisted separately from push delivery.

Future model:

```text
Notification
├── id
├── userId
├── type
├── title
├── body
├── taskId
├── reminderId
├── read
└── createdAt
```

Potential APIs:

```http
GET   /api/v1/notifications
PATCH /api/v1/notifications/{notificationId}/read
```

This allows the application to show notification history even when the user was offline.

---

# 26. Reminder Scheduler

Initial implementation can use Spring scheduling.

Conceptually:

```text
Every N seconds/minutes
        ↓
Find SCHEDULED reminders
where remindAt <= now
        ↓
Create Notification
        ↓
Send FCM
        ↓
Mark Reminder SENT
```

Important production concerns:

- duplicate processing
- concurrent scheduler execution
- failed FCM requests
- retry
- invalid device tokens
- missed reminders
- application restarts

These should be addressed before calling the feature production-ready.

---

# 27. Recurring Tasks

## Status

**Future**

Recurring tasks should not simply be represented by:

```java
boolean recurring;
```

A proper recurrence model should eventually support:

```text
RecurrenceRule
├── frequency
├── interval
├── daysOfWeek
├── dayOfMonth
├── startDate
├── endDate
└── timezone
```

Possible frequencies:

```text
DAILY
WEEKLY
MONTHLY
YEARLY
```

The preferred conceptual model is:

```text
Recurrence Rule
      │
      ├── Task Instance #1
      ├── Task Instance #2
      ├── Task Instance #3
      └── ...
```

rather than continuously modifying one task.

---

# 28. Archive / Soft Delete

## Status

**Future**

Potential functionality:

```text
active
archived
deleted
```

Potential model:

```text
archivedAt
deletedAt
```

This allows:

- Restore
- Trash
- History
- Safer deletion
- Better UX

This should be designed carefully because subtasks currently use cascade deletion.

---

# 29. Task Ordering / Pinning

## Status

**Future**

Potential capabilities:

```text
Pinned task
Custom ordering
```

Possible fields:

```text
isPinned
position
```

Ordering should be designed independently from creation time.

---

# 30. Audit / History

## Status

**Future**

Potential audit system:

```text
Task created
Task updated
Task completed
Task deleted
Priority changed
Category changed
Tag added
Tag removed
```

Possible model:

```text
TaskHistory
├── id
├── taskId
├── userId
├── action
├── oldValue
├── newValue
└── createdAt
```

This is useful for demonstrating event/history-oriented backend design.

---

# 31. Background Processing

The application will eventually need background processing for:

- Reminders
- Notifications
- Recurring task generation
- Cleanup
- Token cleanup
- Email
- Search indexing

Initial approach:

```text
Spring Scheduler
```

Future scalable architecture:

```text
Application
    ↓
Message Queue
    ↓
Worker
    ↓
Background processing
```

Possible technologies to evaluate later:

- Redis
- RabbitMQ
- Kafka
- AWS SQS

Do not introduce these until there is a real use case.

---

# 32. Redis / Caching

## Status

**Future**

Redis can eventually be introduced for:

- Caching
- Rate limiting
- Distributed locks
- Scheduler coordination
- Temporary data
- Cache-aside patterns

Potential architecture:

```text
Client
  ↓
Backend
  ↓
Redis
  ↓ cache miss
PostgreSQL
```

Caching should only be introduced where there is a measurable benefit.

---

# 33. Rate Limiting

## Status

**Future**

Potentially protect:

```text
Login
Registration
Refresh token
Password-related APIs
Public APIs
```

Possible implementation:

```text
Redis
   ↓
Rate limiter
   ↓
Spring Security / Filter
```

---

# 34. API Versioning

Current API:

```text
/api/v1/...
```

Continue using explicit API versions.

Example:

```text
/api/v1/tasks
/api/v1/categories
/api/v1/tags
```

Breaking API changes should move to:

```text
/api/v2/...
```

rather than silently breaking clients.

---

# 35. API Design Rules

## Controllers

Controllers should:

- Accept HTTP requests
- Validate request DTOs
- Extract authenticated user
- Call services
- Return HTTP responses

Controllers should **not** contain business logic.

## Services

Services own:

- Business rules
- Authorization checks
- Entity relationships
- Validation that requires database state
- Transactions

## Repositories

Repositories own:

- Database access
- Query methods
- Specifications
- Persistence

## DTOs

Entities should not be directly exposed through REST APIs.

Use:

```text
Request DTO
    ↓
Service
    ↓
Entity
    ↓
Response DTO
```

---

# 36. Time Handling

The backend should consistently use UTC for persisted timestamps.

Preferred type:

```java
OffsetDateTime
```

Example:

```java
OffsetDateTime.now(ZoneOffset.UTC)
```

Client-local timezone handling should happen at the API/application boundary when appropriate.

This becomes especially important for:

- Due dates
- Reminders
- Recurring tasks
- Notifications

---

# 37. Testing Strategy

Testing should be added progressively.

## Unit tests

Focus on business rules:

```text
TaskService
CategoryService
TagService
ReminderService
```

Examples:

```text
Cannot use another user's category
Cannot use another user's tag
Cannot use another user's task as parent
Cannot make task its own parent
Cannot create nested subtask
```

## Integration tests

Verify:

```text
Controller
   ↓
Service
   ↓
Repository
   ↓
Database
```

Important areas:

- Authentication
- Authorization
- Task CRUD
- Tags
- Categories
- Subtasks
- Reminders
- Notifications

---

# 38. Testing Philosophy

Do not write tests merely for line coverage.

Prioritize:

1. Security
2. Business rules
3. Data integrity
4. Edge cases
5. API contracts

Tests should protect important behavior.

---

# 39. Production Hardening

After core functionality is complete:

## Security

- JWT hardening
- Refresh token rotation
- Token revocation
- Password security
- CORS configuration
- Security headers
- Rate limiting
- Input validation

## Database

- Query optimization
- Index review
- Connection pool tuning
- Transaction boundaries
- Migration validation

## API

- Consistent error format
- Pagination
- API documentation
- Request validation
- Versioning

## Observability

Eventually add:

```text
Structured logging
Metrics
Health checks
Tracing
```

Potential stack:

```text
Spring Boot Actuator
Micrometer
Prometheus
Grafana
OpenTelemetry
```

---

# 40. Observability Architecture

Future:

```text
Spring Boot
    │
    ├── Logs ─────→ Log aggregation
    │
    ├── Metrics ──→ Prometheus
    │                  ↓
    │               Grafana
    │
    └── Traces ───→ OpenTelemetry
```

Important metrics:

```text
HTTP request latency
HTTP error rate
Database latency
JVM memory
CPU
Scheduler execution
Notification failures
FCM failures
```

---

# 41. Deployment

Future production deployment:

```text
Android App
     ↓
HTTPS
     ↓
Load Balancer
     ↓
Spring Boot
     ↓
PostgreSQL
```

Potential future:

```text
                Load Balancer
                     │
          ┌──────────┴──────────┐
          ↓                     ↓
    Spring Boot #1        Spring Boot #2
          │                     │
          └──────────┬──────────┘
                     ↓
                 PostgreSQL
```

Application instances should remain stateless.

JWT authentication helps support this architecture.

---

# 42. Containerization

Docker can eventually be used for:

```text
Spring Boot
PostgreSQL
Redis
Elasticsearch
```

Local development:

```text
docker compose
```

Production deployment can use containers depending on the hosting platform.

---

# 43. CI/CD

Future GitHub Actions pipeline:

```text
git push
   ↓
Build
   ↓
Unit tests
   ↓
Integration tests
   ↓
Static analysis
   ↓
Build Docker image
   ↓
Deploy
```

Potential build check:

```text
./gradlew clean build
```

---

# 44. Recommended Final Roadmap

## Phase 1 — Foundation

- [x] Spring Boot project
- [x] Gradle
- [x] PostgreSQL
- [x] Flyway
- [x] Base project structure
- [x] Global exception handling
- [x] Validation

## Phase 2 — Authentication

- [x] Registration
- [x] Login
- [x] JWT access token
- [x] JWT authentication filter
- [x] Refresh tokens
- [x] Logout
- [x] `/auth/me`
- [x] Swagger JWT authentication

## Phase 3 — User

- [x] User profile response
- [x] Update profile
- [x] Name validation
- [x] Updated timestamps

## Phase 4 — Tasks

- [x] Task CRUD
- [x] Completion
- [x] Priority
- [x] Due dates

## Phase 5 — Categories

- [x] System categories
- [x] Custom categories
- [x] Category ownership
- [x] Task/category relationship

## Phase 6 — Tags

- [x] Tag CRUD
- [x] User ownership
- [x] Unique tag names per user
- [x] Task/tag relationship
- [x] Tag assignment/removal

## Phase 7 — Subtasks

- [x] Parent task relationship
- [x] Self-parent prevention
- [x] Nested-subtask prevention
- [x] Parent ownership validation
- [x] Cascade delete
- [x] Parent task response

## Phase 8 — Task Search

- [ ] Search
- [ ] Filtering
- [ ] Pagination
- [ ] Sorting

**Status: Deferred**

Potential future implementation using Elasticsearch/OpenSearch.

## Phase 9 — Recurring Tasks

- [ ] Recurrence rule model
- [ ] Daily recurrence
- [ ] Weekly recurrence
- [ ] Monthly recurrence
- [ ] End date
- [ ] Timezone support
- [ ] Task instance generation

**Status: Future**

## Phase 10 — Reminders

- [ ] Reminder entity
- [ ] Reminder migration
- [ ] Create reminder
- [ ] List reminders
- [ ] Cancel reminder
- [ ] Reminder validation
- [ ] Scheduler
- [ ] Duplicate prevention
- [ ] Retry handling

**Status: Future**

## Phase 11 — Notifications

- [ ] Notification entity
- [ ] Notification repository
- [ ] Notification service
- [ ] Read/unread
- [ ] Notification APIs
- [ ] Notification history

**Status: Future**

## Phase 12 — Push Notifications

- [ ] Firebase project
- [ ] FCM integration
- [ ] Firebase Admin SDK
- [ ] Device token registration
- [ ] Multiple-device support
- [ ] Push notification service
- [ ] Invalid-token cleanup
- [ ] Retry handling

**Status: Future**

## Phase 13 — Background Processing

- [ ] Scheduler
- [ ] Background jobs
- [ ] Job failure handling
- [ ] Retry
- [ ] Idempotency
- [ ] Distributed execution strategy

**Status: Future**

## Phase 14 — Advanced Task Features

- [ ] Archive
- [ ] Restore
- [ ] Trash
- [ ] Pinning
- [ ] Custom ordering
- [ ] Task history

**Status: Future**

## Phase 15 — Caching / Distributed Systems

- [ ] Redis
- [ ] Cache-aside
- [ ] Distributed locking
- [ ] Rate limiting
- [ ] Cache invalidation

**Status: Future**

## Phase 16 — Search Infrastructure

- [ ] Elasticsearch/OpenSearch
- [ ] Task indexing
- [ ] Index synchronization
- [ ] Full-text search
- [ ] Fuzzy search
- [ ] Autocomplete
- [ ] Relevance ranking

**Status: Future**

## Phase 17 — Production Hardening

- [ ] Actuator
- [ ] Metrics
- [ ] Structured logging
- [ ] Tracing
- [ ] Rate limiting
- [ ] Security hardening
- [ ] DB optimization
- [ ] Connection pool tuning

**Status: Future**

## Phase 18 — Deployment / DevOps

- [ ] Docker
- [ ] Docker Compose
- [ ] CI/CD
- [ ] GitHub Actions
- [ ] Production deployment
- [ ] Environment management
- [ ] Secrets management
- [ ] Monitoring

**Status: Future**

---

# 45. Important Architecture Principles

### 1. PostgreSQL is the source of truth

External systems such as:

```text
Redis
Elasticsearch
FCM
```

should not become the authoritative task database.

### 2. Business logic belongs in services

Avoid putting business rules inside controllers or repositories.

### 3. User ownership must always be enforced

Every user-owned resource must be scoped to the authenticated user.

### 4. Database migrations are immutable

Never edit an already-applied Flyway migration.

Create a new migration.

### 5. Don't add infrastructure without a reason

Do not introduce:

```text
Redis
Kafka
RabbitMQ
Elasticsearch
```

just because they are popular technologies.

Introduce them when the application has a genuine requirement.

### 6. Prefer simple architecture first

Start with:

```text
Spring Boot
+
PostgreSQL
```

Then evolve:

```text
PostgreSQL
     ↓
Redis
     ↓
Queue
     ↓
Elasticsearch
```

only when justified.

### 7. APIs should remain backward compatible

Existing APIs should not be unnecessarily changed.

For example:

```http
GET /api/v1/tasks
```

remains the existing simple endpoint.

Advanced search can later use:

```http
GET /api/v1/tasks/search
```

---

# 46. Current Status

```text
Authentication        ████████████████████ Complete
User/Profile          ████████████████████ Complete
Tasks                 ████████████████████ Complete
Categories            ████████████████████ Complete
Tags                  ████████████████████ Complete
Subtasks              ████████████████████ Complete
Search                ░░░░░░░░░░░░░░░░░░░░ Deferred
Recurring Tasks       ░░░░░░░░░░░░░░░░░░░░ Future
Reminders             ░░░░░░░░░░░░░░░░░░░░ Future
Notifications         ░░░░░░░░░░░░░░░░░░░░ Future
Push / FCM            ░░░░░░░░░░░░░░░░░░░░ Future
Background Jobs       ░░░░░░░░░░░░░░░░░░░░ Future
Redis                 ░░░░░░░░░░░░░░░░░░░░ Future
Elasticsearch         ░░░░░░░░░░░░░░░░░░░░ Future
Observability         ░░░░░░░░░░░░░░░░░░░░ Future
CI/CD                 ░░░░░░░░░░░░░░░░░░░░ Future
Deployment            ░░░░░░░░░░░░░░░░░░░░ Future
```

---

# 47. Development Workflow

For each new feature:

```text
Choose one feature
      ↓
Design
      ↓
Database migration
      ↓
Entity
      ↓
Repository
      ↓
DTO
      ↓
Service
      ↓
Controller
      ↓
Validation
      ↓
Business-rule testing
      ↓
Manual API verification
      ↓
Mark feature complete
      ↓
Move to next feature
```

This keeps the project maintainable while gradually introducing more advanced backend concepts.

---

# 48. Long-Term Target Architecture

```text
                         Android App
                             │
                             │ HTTPS
                             ▼
                     ┌───────────────┐
                     │ Load Balancer │
                     └───────┬───────┘
                             │
                ┌────────────┴────────────┐
                ▼                         ▼
         Spring Boot #1           Spring Boot #2
                │                         │
                └────────────┬────────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
              ▼              ▼              ▼
         PostgreSQL        Redis       Message Queue
              │                             │
              │                             ▼
              │                       Background
              │                        Workers
              │                             │
              ▼                             ├── Reminders
        Source of Truth                     ├── Notifications
                                            ├── Recurrence
                                            └── Search Indexing
                                                   │
                                                   ▼
                                            Elasticsearch

Push notifications:

Background Worker
       ↓
Firebase FCM
       ↓
Android
```

This is the **long-term architecture**, not something that should be implemented immediately.

---

# 49. Development Philosophy

The purpose of this project is not to use as many technologies as possible.

The purpose is to demonstrate understanding of:

- REST API design
- Java/Spring Boot
- Authentication
- Authorization
- Relational database design
- Transactions
- JPA/Hibernate
- Database indexing
- API validation
- Exception handling
- Background processing
- Caching
- Search
- Distributed systems
- Scalability
- Observability
- Production engineering

Every new technology should therefore have a clear reason for being introduced.
