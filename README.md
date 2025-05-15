# AI Fitness App Microservices
This project is an AI-powered fitness app built using a microservices architecture. It includes multiple services for user management, activity tracking, AI recommendations, and authentication. The backend is built with Spring Boot, using technologies like OAuth2 with Keycloak, RabbitMQ, and Docker for containerization. The frontend is built with React and Redux for state management.
## Project Overview
The AI Fitness App includes the following microservices:
 - ##### User Service: Manages user data and authentication.
 - ##### Activity Service: Tracks user fitness activities and stores them in MongoDB.
 - ##### AI Service: Provides personalized recommendations based on user activities using AI.
 - ##### Gateway: Routes incoming requests to the appropriate service.
 - ##### Config Server: Centralized configuration management for the application.
 - ##### Eureka: Service discovery for registering and locating services.
 - ##### RabbitMQ: Message broker used for communication between services.
 - ##### Frontend: React app with Redux for state management.
## Tech Stack
- Spring Boot
- OAuth2, Keycloak
- Eureka
- RabbitMQ
- MongoDB
- Docker
- Spring Cloud Config
- React, Redux
## Getting Started
### Prerequisites
To get started with this project, you need:
 - Java 17 or later
 - Maven or Gradle (Maven is recommended)
 - Docker for containerization
 - Keycloak for authentication
 - RabbitMQ for messaging
 - MongoDB for data storage
 - Eureka for registering and locating services
