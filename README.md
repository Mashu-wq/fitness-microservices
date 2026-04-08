# Fitness AI Microservices Platform

A production-grade, cloud-native fitness tracking platform built on Spring Boot and Spring Cloud. Users log activities and wearable device data; the system tracks goal progress, maintains competitive leaderboards, and generates AI-powered coaching recommendations in real-time using Google Gemini.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
  - [System Diagram](#system-diagram)
  - [Request Flow](#request-flow)
  - [Messaging Architecture](#messaging-architecture)
  - [Database per Service](#database-per-service)
- [Services](#services)
- [Feature Modules](#feature-modules)
- [Tech Stack](#tech-stack)
- [Infrastructure & DevOps](#infrastructure--devops)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Environment Setup](#environment-setup)
- [Keycloak Setup](#keycloak-setup)
- [Running the Application](#running-the-application)
- [API Reference](#api-reference)
- [Configuration](#configuration)
- [Observability](#observability)
- [MCP Server](#mcp-server)
- [Security Model](#security-model)

---

## Overview

This platform demonstrates a real-world microservices architecture applied to a fitness domain. It solves the challenge of building a scalable system where multiple independent services must collaborate — through synchronous REST, asynchronous RabbitMQ events, and high-throughput Kafka streams — while remaining observable, secure, and maintainable.

**Key capabilities:**

| Capability | Description |
|---|---|
| AI Coaching | Google Gemini analyzes every logged activity and goal milestone |
| Goal Tracking | Set distance/calorie/duration/frequency goals with real-time SSE progress push |
| Leaderboards | Weekly, monthly, and all-time rankings across 4 metrics with Redis caching |
| Wearable Ingest | Kafka-based high-throughput device event pipeline with auto workout aggregation |
| Observability | Distributed tracing (Zipkin), metrics (Prometheus/Micrometer), structured logging |
| Security | Keycloak OAuth2/JWT, rate limiting (Redis), CORS, DLX/DLQ dead-letter patterns |
| Developer Tooling | Custom MCP server with 15 tools for MongoDB, RabbitMQ, health, JWT, and leaderboard inspection |

---

## Architecture

### System Diagram

```
┌────────────────────────────────────────────────────────────────────────────────────┐
│                                  CLIENT LAYER                                      │
│                    Web App / Mobile App / Device SDK / Postman                     │
└───────────────────────────────────┬────────────────────────────────────────────────┘
                                    │  HTTPS (JWT Bearer)
                                    ▼
┌────────────────────────────────────────────────────────────────────────────────────┐
│                              API GATEWAY  :8080                                    │
│                                                                                    │
│  1. Keycloak JWT validation  (OAuth2 Resource Server)                              │
│  2. KeycloakUserSyncFilter   (auto-register new users → inject X-User-ID header)   │
│  3. Redis Rate Limiting      (10 req/s per user, burst 20)                         │
│  4. CORS + Route Matching    (lb:// Eureka-resolved backends)                      │
└──────┬──────────┬──────────┬──────────┬──────────┬──────────┬──────────┬──────────┘
       │          │          │          │          │          │          │
       ▼          ▼          ▼          ▼          ▼          ▼          ▼
   USER      ACTIVITY    AI SVC    GOAL SVC  LEADER-   WEARABLE  (future)
   :8081      :8082       :8083     :8084     BOARD     :8086
                                              :8085
       │          │
       ▼          ▼
  MongoDB     MongoDB ─────────── RabbitMQ (fitness.exchange)
  fitnessuser fitnessactivity          │ routing key: activity.tracking
                                       │
                    ┌──────────────────┼──────────────────────┐
                    ▼                  ▼                       ▼
              activity.queue    goal.activity.queue    leaderboard.activity.queue
                    │                  │                       │
              AI SERVICE          GOAL SERVICE          LEADERBOARD SERVICE
              Gemini API ◄────    progress update        score upsert
              save Rec.           SSE push               Redis cache evict
                    │
              goal.progress.exchange ─► goal.ai.queue
                                            │
                                       AI SERVICE
                                       goal coaching Rec.

┌─────────────────────────────────────────────────────────────────────────────────┐
│                            WEARABLE PIPELINE (Kafka)                            │
│                                                                                 │
│  Device ──► POST /api/wearables/events ──► MongoDB (fitnesswearable)            │
│                                        └──► Kafka: wearable.raw.events          │
│                                                 │ (keyed by userId, 3 partitions)│
│                                                 ▼                               │
│                                       WearableKafkaConsumer                     │
│                                       (3 retries, DLT on failure)               │
│                                                 │ on WORKOUT_END                │
│                                                 ▼                               │
│                                       WorkoutAggregationService                 │
│                                       (sum metrics in session window)           │
│                                                 │                               │
│                                                 ▼  POST /api/activities         │
│                                       ACTIVITY SERVICE ──► RabbitMQ pipeline   │
└─────────────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│                     INFRASTRUCTURE LAYER                         │
│                                                                  │
│  Eureka :8761      Config Server :8888    Keycloak :7080 (ext)  │
│  Redis  :6379      RabbitMQ :5672/:15672  MongoDB :27017 (ext)  │
│  Kafka  :9092      Zipkin   :9411                               │
└──────────────────────────────────────────────────────────────────┘
```

### Request Flow

#### Standard Activity Log Flow

```
1. Client          POST /api/activities  (Authorization: Bearer <JWT>)
        │
2. Gateway         ─ Validates JWT signature against Keycloak JWKS endpoint
                   ─ KeycloakUserSyncFilter: extracts sub claim → upserts user in MongoDB
                   ─ Injects X-User-ID: <keycloak-sub> header
                   ─ Rate limiter checks Redis token bucket
        │
3. ActivityService ─ Validates request body (@Valid)
                   ─ Verifies user exists (UserValidationService with 5-min Redis cache)
                   ─ Saves Activity to MongoDB (fitnessactivity)
                   ─ Publishes ActivityEvent to fitness.exchange (routing key: activity.tracking)
        │
4. RabbitMQ        ─ DirectExchange fan-out to three queues simultaneously:
                     ├── activity.queue          → AIService
                     ├── goal.activity.queue     → GoalService
                     └── leaderboard.activity.queue → LeaderboardService
        │
5a. AIService      ─ Builds structured Gemini prompt from activity data
                   ─ Calls Google Gemini API (30s timeout, connection pool)
                   ─ Parses JSON response (analysis, improvements, suggestions, safety)
                   ─ Saves Recommendation to MongoDB (fitnessrecommendation)

5b. GoalService    ─ Finds all ACTIVE goals for the user within date range
                   ─ Extracts metric contribution (DISTANCE/CALORIES/DURATION/FREQUENCY)
                   ─ Atomically updates GoalProgress document
                   ─ Checks 25/50/75/100% milestones → publishes GoalProgressEvent
                   ─ Pushes SSE update to all open browser connections
                   ─ If milestone crossed → goal.progress.exchange → goal.ai.queue
                                           → AIService generates adaptive coaching

5c. LeaderboardService ─ Upserts score for WEEKLY, MONTHLY, ALL_TIME buckets
                       ─ All 4 metrics updated atomically via MongoTemplate $inc
                       ─ Evicts Redis leaderboard cache
```

#### Wearable Device Flow

```
1. Device SDK      POST /api/wearables/events  (HEART_RATE, STEPS, GPS_TRACK...)
        │
2. WearableService ─ Persists WearableEvent to MongoDB (fitnesswearable)
                   ─ Publishes to Kafka: wearable.raw.events (key=userId → same partition)
        │
3. Kafka Consumer  ─ On WORKOUT_END: queries session window (WORKOUT_START → WORKOUT_END)
                   ─ Aggregates: sum calories/distance/steps, avg heartRate, calc duration
                   ─ @RetryableTopic: 3 retries (1s/2s/4s backoff) → DLT on failure
                   ─ Marks all session events as processed
        │
4. ActivityService ─ WebClient POST via Eureka (lb://ACTIVITY-SERVICE)
                   ─ Enters standard Activity Log Flow above (step 3 onwards)
```

### Messaging Architecture

#### RabbitMQ — Command & Integration Events

```
fitness.exchange (DirectExchange)
  ├── activity.tracking → activity.queue              [AIService consumer]
  │                    → goal.activity.queue           [GoalService consumer]
  │                    → leaderboard.activity.queue    [LeaderboardService consumer]
  │
  └── (DLX pattern on every queue)
        activity.queue          ──x──► fitness.dlx         → activity.queue.dlq
        goal.activity.queue     ──x──► goal.activity.dlx   → goal.activity.dlq
        leaderboard.activity.queue ──x──► leaderboard.activity.dlx → leaderboard.activity.dlq

goal.progress.exchange (DirectExchange)
  └── goal.progress → goal.ai.queue                   [AIService goal coaching consumer]
                                   ──x──► goal.ai.dlx → goal.ai.dlq
```

#### Kafka — Wearable Streaming Events

```
Topic: wearable.raw.events
  ├── Partitions: 3  (keyed by userId — per-user ordering guaranteed)
  ├── Replication: 1 (single-node dev; set to 3 in production)
  ├── Producer: idempotent, acks=all, retries=3
  ├── Consumer group: wearable-processor (3 concurrent threads)
  └── Failure path: wearable.raw.events.dlt (Dead Letter Topic)
```

#### Why Two Brokers?

| Concern | RabbitMQ | Kafka |
|---|---|---|
| Pattern | Command / integration events | Event streaming / data pipeline |
| Message ordering | Per-queue | Per-partition (keyed) |
| Backpressure | Consumer-pull | Consumer-lag visibility |
| Replay | No (TTL) | Yes (configurable retention) |
| Use here | Activity → AI/Goal/Leaderboard | Wearable high-frequency ingest |

### Database per Service

| Service | Database | Collections |
|---|---|---|
| UserService | `fitnessuser` | `users` |
| ActivityService | `fitnessactivity` | `activities` |
| AIService | `fitnessrecommendation` | `recommendations` |
| GoalService | `fitnessgoal` | `goals`, `goal_progress` |
| LeaderboardService | `fitnessleaderboard` | `leaderboard_scores` |
| WearableService | `fitnesswearable` | `wearable_events` |

No cross-service database reads. Each service owns its data completely.

---

## Services

| Service | Port | Description |
|---|---|---|
| **Eureka** | 8761 | Service registry — all services self-register; gateway resolves `lb://` URIs |
| **Config Server** | 8888 | Native profile serves YAMLs from classpath; all services import on startup |
| **API Gateway** | 8080 | Reactive (WebFlux) — JWT auth, user sync filter, rate limiting, CORS, routing |
| **User Service** | 8081 | User CRUD, Keycloak ID mapping, existence validation endpoint |
| **Activity Service** | 8082 | Activity CRUD, validation, RabbitMQ publisher |
| **AI Service** | 8083 | Gemini API client, recommendation storage + pagination, Swagger UI |
| **Goal Service** | 8084 | Goal CRUD (pause/resume), real-time progress tracking, SSE stream, milestone events |
| **Leaderboard Service** | 8085 | Score aggregation, top-N ranking, user percentile, Redis-cached queries |
| **Wearable Service** | 8086 | Device event ingest, Kafka producer/consumer, workout session aggregation |

---

## Feature Modules

### 1. Core Activity + AI Pipeline
Users log workouts. Every activity automatically triggers a Gemini AI analysis covering performance, improvement areas, next workout suggestions, and safety precautions. Stored and retrievable via paginated API.

### 2. Goal Tracking & Progress Engine
Users set typed goals (DISTANCE/CALORIES/DURATION/FREQUENCY) with weekly, monthly, or custom periods. Every incoming activity is evaluated against all active goals. Progress is stored atomically, and milestone events (25/50/75/100%) trigger adaptive AI coaching. Real-time progress updates are pushed via Server-Sent Events.

### 3. Social Leaderboards
All activities automatically update a multi-dimensional leaderboard. Scores are maintained per metric (Distance, Calories, Duration, Frequency) × per period (Weekly, Monthly, All-Time) using atomic MongoDB `$inc` upserts. The top-N leaderboard is cached in Redis (60s TTL) and individual user rank includes percentile and gap-to-leader.

### 4. Wearable Device Integration
A dedicated Kafka-backed pipeline ingests high-frequency device events (heart rate, steps, GPS, sleep). When a WORKOUT_END event arrives, the service aggregates the session window — summing metrics, computing duration, averaging heart rate — and creates a full Activity via the ActivityService REST API, feeding seamlessly into the AI and goal pipelines.

---

## Tech Stack

### Backend Frameworks

| Technology | Version | Role |
|---|---|---|
| **Java** | 21 (17 for userservice) | Primary language |
| **Spring Boot** | 3.4.5 | Application framework |
| **Spring Cloud** | 2024.0.1 | Microservices toolkit |
| **Spring Cloud Gateway** | 4.x (WebFlux) | Reactive API gateway |
| **Spring Cloud Netflix Eureka** | — | Service discovery & registry |
| **Spring Cloud Config** | — | Centralized configuration server |
| **Spring Cloud LoadBalancer** | — | Client-side load balancing (`lb://`) |
| **Spring Security OAuth2** | — | JWT resource server |
| **Spring Data MongoDB** | — | MongoDB repositories + MongoTemplate |
| **Spring AMQP** | — | RabbitMQ producer/consumer |
| **Spring Kafka** | — | Kafka producer/consumer + `@RetryableTopic` |
| **Spring WebFlux / WebClient** | — | Reactive HTTP client (non-blocking) |
| **Spring Validation** | — | Bean Validation (`@Valid`, `@NotNull`, `@Min`) |
| **Spring Cache** | — | `@Cacheable` / `@CacheEvict` abstraction |
| **Spring Actuator** | — | Health, metrics, info endpoints |

### Messaging & Streaming

| Technology | Role |
|---|---|
| **RabbitMQ 3.x** | Integration events — Activity→AI/Goal/Leaderboard fan-out, DLX/DLQ |
| **Apache Kafka 3.7** | Wearable event streaming — keyed partitions, DLT, replay |

### Databases & Caching

| Technology | Role |
|---|---|
| **MongoDB 6+** | Document store — one database per service (polyglot persistence) |
| **Redis 7** | API rate limiting (Gateway) + leaderboard cache (LeaderboardService) + user validation cache (ActivityService) |

### AI & External APIs

| Technology | Role |
|---|---|
| **Google Gemini API** | Natural language fitness recommendations and goal coaching |

### Authentication & Security

| Technology | Role |
|---|---|
| **Keycloak 24+** | OAuth2 authorization server, JWT issuer, user management |
| **JWT (RS256)** | Stateless authentication — validated against Keycloak JWKS |
| **Nimbus JOSE JWT** | JWT parsing in KeycloakUserSyncFilter |

### Observability

| Technology | Role |
|---|---|
| **Micrometer Tracing** | Trace context propagation (traceId/spanId in logs) |
| **Brave (Micrometer bridge)** | Zipkin-compatible trace generation |
| **Zipkin 3** | Distributed trace collection and visualization |
| **Micrometer Prometheus** | Metrics exposition (`/actuator/prometheus`) |
| **Spring Boot Actuator** | `/actuator/health`, `/actuator/info`, `/actuator/metrics` |

### Developer Tools & Build

| Technology | Role |
|---|---|
| **Lombok** | Boilerplate reduction (`@Data`, `@Builder`, `@RequiredArgsConstructor`) |
| **SpringDoc OpenAPI** | Swagger UI on AI Service (`/swagger-ui.html`) |
| **Apache Maven** | Build system (all services) |
| **Docker** | Service containerization |
| **Docker Compose** | Full-stack orchestration with health checks and startup ordering |

---

## Infrastructure & DevOps

### Docker Compose Services

| Container | Image | Port(s) | Health Check |
|---|---|---|---|
| `eureka` | Custom build | 8761 | `/actuator/health` |
| `configserver` | Custom build | 8888 | `/actuator/health` |
| `gateway` | Custom build | 8080 | `/actuator/health` |
| `userservice` | Custom build | 8081 | `/actuator/health` |
| `activityservice` | Custom build | 8082 | `/actuator/health` |
| `aiservice` | Custom build | 8083 | `/actuator/health` |
| `goalservice` | Custom build | 8084 | `/actuator/health` |
| `leaderboardservice` | Custom build | 8085 | `/actuator/health` |
| `wearableservice` | Custom build | 8086 | `/actuator/health` |
| `rabbitmq` | `rabbitmq:3-management` | 5672, 15672 | `rabbitmq-diagnostics ping` |
| `redis` | `redis:7-alpine` | 6379 | `redis-cli ping` |
| `kafka` | `bitnami/kafka:3.7` | 9092 | `kafka-topics.sh --list` |
| `zipkin` | `openzipkin/zipkin:3` | 9411 | HTTP `/health` |

> **Not in docker-compose (run externally):**
> - **MongoDB** — `localhost:27017`
> - **Keycloak** — `http://localhost:7080`

### Startup Order

```
Eureka (must be healthy)
    └──► Config Server (must be healthy)
              └──► [Gateway, UserService, ActivityService, AIService,
                    GoalService, LeaderboardService] + RabbitMQ + Redis + Zipkin
                        └──► WearableService  (also needs Kafka healthy)
```

Spring Boot services have `start_period: 90s` in health checks to allow JVM warm-up.

### Dead Letter Pattern

Every RabbitMQ consumer queue has a corresponding dead-letter exchange and DLQ:

```
queue ──(reject/nack)──► DLX ──► DLQ   (parked, retrievable via RabbitMQ Management UI)
```

Kafka failures after 3 retries route to `wearable.raw.events.dlt`.

### Rate Limiting

The API Gateway uses Redis-backed token bucket rate limiting per user:
- **10 requests/second** steady-state throughput
- **20 requests burst** capacity
- Falls back to remote IP when X-User-ID is unavailable (unauthenticated paths)

---

## Project Structure

```
Spring-AI-Microservices-Fitness/
│
├── README.md
├── CLAUDE.md                          # Claude Code development guidance
│
├── fitness-microservice/
│   ├── docker-compose.yml             # Full stack orchestration
│   ├── .env.example                   # Required environment variables
│   ├── .gitignore
│   │
│   ├── eureka/                        # Service Registry
│   ├── configserver/                  # Centralized Config
│   │   └── src/main/resources/config/
│   │       ├── api-gateway.yml
│   │       ├── user-service.yml
│   │       ├── activity-service.yml
│   │       ├── ai-service.yml
│   │       ├── goal-service.yml
│   │       ├── leaderboard-service.yml
│   │       └── wearable-service.yml
│   │
│   ├── gateway/                       # API Gateway (WebFlux)
│   │   └── src/main/java/.../gateway/
│   │       ├── KeycloakUserSyncFilter.java   # JWT → X-User-ID injection
│   │       ├── SecurityConfig.java           # OAuth2 + CORS
│   │       └── config/RateLimiterConfig.java # Redis token bucket
│   │
│   ├── userservice/                   # User Profiles
│   │   └── src/main/java/.../userservice/
│   │       ├── model/User.java
│   │       ├── service/UserService.java
│   │       └── controller/UserController.java
│   │
│   ├── activityservice/               # Activity Tracking
│   │   └── src/main/java/.../activityservice/
│   │       ├── model/Activity.java
│   │       ├── service/ActivityService.java
│   │       ├── service/UserValidationService.java  # Redis-cached user check
│   │       └── config/RabitmqConfig.java           # DLX/DLQ setup
│   │
│   ├── aiservice/                     # AI Recommendations
│   │   └── src/main/java/.../aiservice/
│   │       ├── service/GeminiService.java           # Gemini API client
│   │       ├── service/ActivityAIService.java        # Prompt builder + parser
│   │       ├── service/ActivityMessageListener.java  # RabbitMQ consumer
│   │       ├── service/GoalAIService.java            # Goal coaching prompts
│   │       └── service/GoalProgressEventListener.java
│   │
│   ├── goalservice/                   # Goal Tracking + SSE
│   │   └── src/main/java/.../goalservice/
│   │       ├── model/{Goal,GoalProgress,GoalType,GoalPeriod,GoalStatus}.java
│   │       ├── service/GoalService.java              # CRUD + ownership checks
│   │       ├── service/GoalProgressService.java      # Milestone detection + events
│   │       ├── service/ActivityEventListener.java    # RabbitMQ consumer
│   │       ├── service/SseEmitterService.java        # Real-time push (SSE)
│   │       └── controller/{GoalController,GoalProgressController}.java
│   │
│   ├── leaderboardservice/            # Social Leaderboards
│   │   └── src/main/java/.../leaderboardservice/
│   │       ├── model/{LeaderboardEntry,LeaderboardMetric,LeaderboardPeriod}.java
│   │       ├── service/ScoreUpdateService.java       # Atomic MongoTemplate upsert
│   │       ├── service/LeaderboardService.java       # Top-N + user rank + percentile
│   │       ├── service/ActivityEventListener.java
│   │       └── controller/LeaderboardController.java
│   │
│   └── wearableservice/               # Wearable Device Ingest (Kafka)
│       └── src/main/java/.../wearableservice/
│           ├── model/{WearableEvent,DeviceType,EventType}.java
│           ├── config/KafkaConfig.java               # Producer + consumer + topics
│           ├── service/WearableKafkaProducer.java    # Keyed publish to Kafka
│           ├── service/WearableKafkaConsumer.java    # @RetryableTopic consumer
│           ├── service/WorkoutAggregationService.java # Session window aggregation
│           ├── service/ActivityPublisher.java         # WebClient → ActivityService
│           └── controller/WearableController.java
│
└── mcp-servers/
    └── fitness-mcp/                   # Custom MCP Server (Node.js)
        └── src/tools/
            ├── mongodb-tools.js        # 10 tools: users, activities, recs, goals, leaderboard, wearables
            ├── rabbitmq-tools.js       # 3 tools: queue stats, DLQ peek, DLQ purge
            ├── health-tools.js         # 3 tools: all-service health, metrics, Eureka registry
            └── jwt-tools.js            # 1 tool: JWT decode + X-User-ID cross-reference
```

---

## Prerequisites

| Dependency | Version | Notes |
|---|---|---|
| Java | 21+ | (userservice compatible with 17+) |
| Maven | 3.8+ | |
| Docker | 24+ | |
| Docker Compose | 2.x | |
| MongoDB | 6+ | Must run locally at `localhost:27017` — not in docker-compose |
| Keycloak | 24+ | Must run at `http://localhost:7080` — not in docker-compose |
| Google Gemini API Key | — | Free tier available at Google AI Studio |
| Node.js | 18+ | Only required for the MCP server |

---

## Environment Setup

Copy `.env.example` to `.env` in `fitness-microservice/`:

```bash
cd fitness-microservice
cp .env.example .env
```

Edit `.env` with your actual values:

```env
# Google Gemini AI
GEMINI_API_URL=https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent
GEMINI_API_KEY=your_gemini_api_key_here

# RabbitMQ (defaults work for local dev)
RABBITMQ_USER=guest
RABBITMQ_PASS=guest

# Redis (defaults work for docker-compose)
REDIS_HOST=redis
REDIS_PORT=6379
```

---

## Keycloak Setup

1. Start Keycloak at `http://localhost:7080`
2. Log in to the Admin Console
3. Use the `master` realm (or create a new one and update `api-gateway.yml`)
4. Create a client named `fitness-app-gateway`:
   - **Client Protocol:** `openid-connect`
   - **Access Type:** `confidential`
   - **Valid Redirect URIs:** `http://localhost:8080/*`
   - **Web Origins:** `http://localhost:3000` (or your frontend origin)
5. Note the client secret — add it to `api-gateway.yml` if using authorization_code flow
6. Create test users in Keycloak — they are auto-registered in MongoDB on first API call

To obtain a JWT for testing:

```bash
curl -X POST http://localhost:7080/realms/master/protocol/openid-connect/token \
  -d "client_id=fitness-app-gateway" \
  -d "username=testuser" \
  -d "password=testpass" \
  -d "grant_type=password" \
  | jq .access_token
```

---

## Running the Application

### With Docker Compose (recommended)

```bash
# 1. Ensure MongoDB and Keycloak are running externally

# 2. Set up environment
cd fitness-microservice
cp .env.example .env
# Edit .env with your Gemini API key

# 3. Build all services
mvn clean package -DskipTests

# 4. Start the full stack
docker-compose up --build

# 5. Start a single service (for development)
docker-compose up --build activityservice
```

### Running a Service Locally (dev mode)

```bash
# Start infrastructure first
docker-compose up eureka configserver rabbitmq redis kafka zipkin

# Then run any service
cd fitness-microservice/goalservice
mvn spring-boot:run
```

### Build Commands

```bash
# Build a single service
cd fitness-microservice/<service-name>
mvn clean package -DskipTests

# Build all services
cd fitness-microservice
mvn clean package -DskipTests

# Run tests
mvn test

# Run a specific test class
mvn test -Dtest=GoalServiceTest
```

---

## API Reference

All routes are accessed through the Gateway at `http://localhost:8080`.  
Include `Authorization: Bearer <JWT>` in every request.  
The gateway injects `X-User-ID` automatically — do not set this manually.

### User Service — `/api/users`

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/users/register` | Register a new user profile |
| `GET` | `/api/users/{userId}` | Get user profile by ID |
| `GET` | `/api/users/{userId}/validate` | Validate user existence (used internally) |

### Activity Service — `/api/activities`

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/activities` | Log a workout — triggers full AI + Goal + Leaderboard pipeline |
| `GET` | `/api/activities` | Get all activities for the authenticated user |
| `GET` | `/api/activities/{activityId}` | Get a specific activity (ownership enforced) |

**Supported activity types:** `RUNNING`, `WALKING`, `CYCLING`, `SWIMMING`, `WEIGHT_TRAINING`, `YOGA`, `HIIT`, `CARDIO`, `STRETCHING`, `OTHER`

```bash
curl -X POST http://localhost:8080/api/activities \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "type": "RUNNING",
    "duration": 45,
    "caloriesBurned": 480,
    "startTime": "2026-04-08T07:00:00",
    "additionalMetrics": {
      "distanceKm": 7.5,
      "avgHeartRate": 148
    }
  }'
```

### AI Service — `/api/recommendations`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/recommendations/user/{userId}` | Get paginated recommendations (newest first) |
| `GET` | `/api/recommendations/activity/{activityId}` | Get recommendation for a specific activity |

Query params: `page=0&size=10` (max 50 per page)

Swagger UI: `http://localhost:8083/swagger-ui.html`

### Goal Service — `/api/goals`

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/goals` | Create a new goal |
| `GET` | `/api/goals` | List all goals for the authenticated user |
| `GET` | `/api/goals/{goalId}` | Get a specific goal |
| `PATCH` | `/api/goals/{goalId}/pause` | Pause an active goal |
| `PATCH` | `/api/goals/{goalId}/resume` | Resume a paused goal |
| `DELETE` | `/api/goals/{goalId}` | Delete a goal and its progress |
| `GET` | `/api/goals/{goalId}/progress` | Get progress for a specific goal |
| `GET` | `/api/goals/progress` | Get progress across all goals |
| `GET` | `/api/goals/progress/stream` | **SSE stream** — real-time goal progress push |

```bash
# Create a goal
curl -X POST http://localhost:8080/api/goals \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Run 50km this month",
    "type": "DISTANCE",
    "targetActivityType": "RUNNING",
    "targetValue": 50.0,
    "unit": "KM",
    "period": "MONTHLY"
  }'

# Subscribe to real-time SSE updates
curl -N http://localhost:8080/api/goals/progress/stream \
  -H "Authorization: Bearer $TOKEN" \
  -H "Accept: text/event-stream"
```

**Goal types:** `DISTANCE`, `CALORIES`, `DURATION`, `FREQUENCY`  
**Goal periods:** `WEEKLY`, `MONTHLY`, `CUSTOM`

### Leaderboard Service — `/api/leaderboard`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/leaderboard` | Get ranked top-N users (cached 60s) |
| `GET` | `/api/leaderboard/me` | Get my rank, score, percentile, gap to leader |
| `GET` | `/api/leaderboard/metrics` | List available metrics |
| `GET` | `/api/leaderboard/periods` | List available periods |

Query params for `/api/leaderboard`: `metric=DISTANCE&period=WEEKLY&limit=10`  
Query params for `/api/leaderboard/me`: `metric=CALORIES&period=ALL_TIME`

```bash
# Top 5 monthly distance leaders
curl "http://localhost:8080/api/leaderboard?metric=DISTANCE&period=MONTHLY&limit=5" \
  -H "Authorization: Bearer $TOKEN"

# My rank this week
curl "http://localhost:8080/api/leaderboard/me?metric=FREQUENCY&period=WEEKLY" \
  -H "Authorization: Bearer $TOKEN"
```

**Metrics:** `DISTANCE` (KM), `CALORIES` (KCAL), `DURATION` (MIN), `FREQUENCY` (SESSIONS)  
**Periods:** `WEEKLY`, `MONTHLY`, `ALL_TIME`

### Wearable Service — `/api/wearables`

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/wearables/events` | Ingest a raw device event |
| `GET` | `/api/wearables/events` | List all events for the user |
| `POST` | `/api/wearables/sync` | Manually aggregate unprocessed events → Activity |
| `GET` | `/api/wearables/pending` | Count of unprocessed events awaiting sync |
| `GET` | `/api/wearables/devices` | List supported device types |
| `GET` | `/api/wearables/event-types` | List event types with descriptions |

```bash
# Start workout session
curl -X POST http://localhost:8080/api/wearables/events \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"deviceType":"GARMIN","eventType":"WORKOUT_START","activityType":"RUNNING","timestamp":"2026-04-08T09:00:00"}'

# Stream mid-workout data
curl -X POST http://localhost:8080/api/wearables/events \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"deviceType":"GARMIN","eventType":"HEART_RATE","heartRate":158,"timestamp":"2026-04-08T09:15:00"}'

# End workout — triggers automatic aggregation and Activity creation
curl -X POST http://localhost:8080/api/wearables/events \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"deviceType":"GARMIN","eventType":"WORKOUT_END","activityType":"RUNNING","caloriesBurned":520,"distanceKm":8.1,"timestamp":"2026-04-08T09:48:00"}'
```

---

## Configuration

All service configs are served by Config Server from `fitness-microservice/configserver/src/main/resources/config/`:

| File | Service | Key settings |
|---|---|---|
| `api-gateway.yml` | Gateway | Keycloak OIDC, Redis rate limiter, all route definitions |
| `user-service.yml` | UserService | MongoDB URI, auto-index, tracing |
| `activity-service.yml` | ActivityService | MongoDB, Redis cache, RabbitMQ |
| `ai-service.yml` | AIService | MongoDB, RabbitMQ, Gemini env vars |
| `goal-service.yml` | GoalService | MongoDB, RabbitMQ, port 8084 |
| `leaderboard-service.yml` | LeaderboardService | MongoDB, Redis, RabbitMQ, port 8085 |
| `wearable-service.yml` | WearableService | MongoDB, Kafka bootstrap, ActivityService URL, port 8086 |

---

## Observability

### Distributed Tracing — Zipkin

Every request carries a `traceId` and `spanId` propagated across service boundaries (via HTTP headers and RabbitMQ message headers). Visualize traces at:

```
http://localhost:9411
```

All services log in the pattern:
```
INFO [service-name,traceId,spanId] Message
```

### Metrics — Prometheus / Actuator

Every service exposes:
- `GET /actuator/health` — liveness/readiness with component details
- `GET /actuator/metrics` — JVM, HTTP, RabbitMQ, Kafka, MongoDB metrics
- `GET /actuator/prometheus` — Prometheus scrape endpoint

### RabbitMQ Management

```
http://localhost:15672  (guest / guest by default)
```

Monitor queue depths, DLQ contents, consumer counts, and message rates.

### Kafka

```bash
# List topics
docker exec -it kafka kafka-topics.sh --bootstrap-server localhost:9092 --list

# Check consumer lag
docker exec -it kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe --group wearable-processor

# Peek DLT messages
docker exec -it kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic wearable.raw.events.dlt \
  --from-beginning --max-messages 10
```

---

## MCP Server

A custom Model Context Protocol server at `mcp-servers/fitness-mcp/` provides **15 tools** for AI-assisted debugging and inspection.

### Setup

```bash
cd mcp-servers/fitness-mcp
npm install
cp .env.example .env
# Edit .env with MongoDB URI and service URLs
```

### Available Tools

| Category | Tool | Description |
|---|---|---|
| **MongoDB** | `list_users` | List users with optional email/keycloakId filter |
| | `get_user` | Get full user profile by MongoDB ID or Keycloak sub |
| | `list_activities` | List activities with userId/type filter |
| | `get_activity` | Get activity + check if AI recommendation exists |
| | `list_recommendations` | List AI recommendations (paginated) |
| | `get_recommendation` | Full recommendation with analysis/improvements/safety |
| | `list_goals` | List goals with userId/status filter |
| | `list_goal_progress` | Progress docs with ASCII progress bar + milestones |
| | `list_leaderboard` | Query scores by metric/periodKey/userId |
| | `list_wearable_events` | Raw device events with processed status |
| **RabbitMQ** | `get_queue_stats` | All queue depths + DLQ sizes |
| | `get_dlq_messages` | Peek at DLQ messages without consuming |
| | `purge_dlq` | Purge DLQ (requires `confirm: true`) |
| **Health** | `check_all_health` | Parallel health check for all 9 services |
| | `get_service_metrics` | Any Micrometer metric from any service |
| | `get_eureka_registry` | All registered instances from Eureka |
| **JWT** | `decode_jwt` | Decode header + payload, show sub/email/roles/expiry |

---

## Security Model

### Authentication Flow

```
1. User authenticates with Keycloak → receives JWT (RS256, signed with Keycloak private key)
2. Client sends JWT in Authorization: Bearer header
3. Gateway validates JWT signature against Keycloak JWKS endpoint (cached)
4. KeycloakUserSyncFilter:
     a. Parses JWT — extracts sub (Keycloak ID), email, name
     b. Calls UserService to upsert user profile (on first login only)
     c. Strips any incoming X-User-ID header (prevents spoofing)
     d. Injects X-User-ID: <keycloak-sub> into downstream request
5. Backend services read X-User-ID as the authoritative user identifier
```

### Authorization

- All backend services enforce resource ownership (`getGoalAndVerifyOwnership`, `getActivityById` with userId check)
- `SecurityException` → HTTP 403 (never reveals existence of other users' data)
- Public paths: `/actuator/health` (no auth required for load balancer checks)

### Defense in Depth

| Layer | Mechanism |
|---|---|
| Network | All services on isolated Docker network (`fitness-net`) |
| Gateway | Rate limiting (Redis), CORS policy, JWT validation |
| Application | `@Valid` input validation, typed exceptions, no raw SQL |
| Database | Unique indexes prevent duplicate users; no cross-DB queries |
| Messaging | DLX/DLQ prevents poison-pill message loops |
| Secrets | Environment variables only; `.env` in `.gitignore`; no hardcoded credentials |

---

## Author

**Mayesha Marzia Zaman**

Built as a comprehensive demonstration of production-grade Spring Boot microservices — covering service discovery, centralized configuration, async messaging with two brokers, AI integration, real-time streaming, observability, and developer tooling.
