package com.fitness.activityservice.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserValidationService {

    private final WebClient userServiceWebClient;

    @CircuitBreaker(name = "userService", fallbackMethod = "validateUserFallback")
    public boolean validateUser(String userId) {
        log.info("Calling User Validation API for userId: {}", userId);
        try {
            return Boolean.TRUE.equals(
                    userServiceWebClient.get()
                            .uri("/api/users/{userId}/validate", userId)
                            .retrieve()
                            .bodyToMono(Boolean.class)
                            .block()
            );
        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new RuntimeException("User not found: " + userId);
            } else if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                throw new RuntimeException("Invalid request for userId: " + userId);
            }
            throw e;
        }
    }

    /**
     * Fallback when UserService is unavailable (circuit open or timeout).
     * Fails safe by rejecting the operation rather than allowing unknown users.
     */
    public boolean validateUserFallback(String userId, Throwable t) {
        log.warn("UserService circuit is open for userId {}. Reason: {}. Denying access.", userId, t.getMessage());
        return false;
    }
}
