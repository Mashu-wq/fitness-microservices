# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

All services use Apache Maven. Run from within each service directory (`fitness-microservice/<service>/`):

```bash
# Build a service JAR
mvn clean package -DskipTests

# Run tests for a service
mvn test

# Run a single test class
mvn test -Dtest=MyServiceTest

# Build all services from the root
cd fitness-microservice && mvn clean package -DskipTests
```

## Running the Full Stack

```bash
# Start all services with Docker Compose (from fitness-microservice/)
cd fitness-microservice
docker-compose up --build

# Start a single service container
docker-compose up --build <service-name>
# e.g.: eureka, configserver, gateway, userservice, activityservice, aiservice, rabbitmq
```

**Required external dependency:** Keycloak must be running separately at `http://localhost:7080` (realm: `master`). It is not in docker-compose.

## Service Ports

| Service        | Port  |
|----------------|-------|
| Eureka         | 8761  |
| Config Server  | 8888  |
| API Gateway    | 8080  |
| User Service   | 8081  |
| Activity Service | 8082 |
| AI Service     | 8083  |
| RabbitMQ AMQP  | 5672  |
| RabbitMQ UI    | 15672 |
| Keycloak (ext) | 7080  |

## Architecture Overview

This is a Spring Cloud microservices application for fitness tracking with AI-powered recommendations.

### Service Startup Order

Eureka → ConfigServer → [Gateway, UserService, ActivityService, AIService] + RabbitMQ

All services register with Eureka for discovery. Configuration is served centrally from ConfigServer (port 8888 externally, 9999 internally); service configs live at `fitness-microservice/configserver/src/main/resources/config/`.

### Request Flow

All client traffic enters through the **API Gateway** (port 8080), which:
1. Validates JWT tokens via Keycloak OAuth2 Resource Server
2. Runs `KeycloakUserSyncFilter` — extracts JWT claims, auto-registers users in MongoDB if new, then injects `X-User-ID` header downstream
3. Routes to backend services via load-balanced Eureka discovery

Routes:
- `/api/users/**` → USER-SERVICE
- `/api/activities/**` → ACTIVITY-SERVICE
- `/api/recommendations/**` → AI-SERVICE

### Asynchronous Activity → AI Pipeline

1. Client posts activity to ActivityService → saved to MongoDB
2. ActivityService publishes event to RabbitMQ (`fitness.exchange`, routing key `activity.tracking`)
3. AIService `ActivityMessageListener` consumes from `activity.queue`
4. `ActivityAIService` builds a prompt and calls Google Gemini API
5. Parsed recommendation saved to MongoDB (aiservice DB)

### Database per Service

Each service owns a separate MongoDB database:
- `userservice` → `fitnessuser`
- `activityservice` → `fitnessactivity`
- `aiservice` → `fitnessrecommendation`

MongoDB is not in docker-compose — it must be running locally at `mongodb://localhost:27017`.

### AI Integration

`GeminiService` calls the Google Gemini API. Required environment variables for aiservice:
- `GEMINI_API_URL`
- `GEMINI_API_KEY`

The service expects a structured JSON response with fields: `analysis`, `improvements`, `suggestions`, `safety`. Falls back to defaults on parse errors.

## Technology Stack

- **Java 21** (all services except userservice which uses Java 17)
- **Spring Boot 3.4.5**, **Spring Cloud 2024.0.1**
- **Spring Cloud Gateway** (WebFlux/reactive) for the API Gateway
- **Spring Security OAuth2** + Keycloak for authentication
- **Spring Data MongoDB** for persistence
- **Spring AMQP** + RabbitMQ for async messaging
- **Lombok** for boilerplate reduction
- **SpringDoc OpenAPI** (aiservice only) for Swagger UI at `/swagger-ui.html`

## Key Files

- `fitness-microservice/docker-compose.yml` — full stack orchestration
- `fitness-microservice/configserver/src/main/resources/config/` — all service configs
- `fitness-microservice/gateway/src/main/java/com/fitness/gateway/filter/KeycloakUserSyncFilter.java` — user sync logic
- `fitness-microservice/gateway/src/main/java/com/fitness/gateway/config/SecurityConfig.java` — OAuth2/JWT security
- `fitness-microservice/aiservice/src/main/java/com/fitness/aiservice/service/GeminiService.java` — Gemini API client
- `fitness-microservice/aiservice/src/main/java/com/fitness/aiservice/service/ActivityAIService.java` — AI recommendation logic
