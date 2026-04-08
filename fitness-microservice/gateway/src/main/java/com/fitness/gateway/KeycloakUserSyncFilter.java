package com.fitness.gateway;

import com.fitness.gateway.user.RegisterRequest;
import com.fitness.gateway.user.UserService;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Extracts the authenticated user's Keycloak ID (sub claim) directly from the JWT
 * and injects it as an X-User-ID header for downstream services.
 *
 * SECURITY: userId is always derived from the verified JWT — never trusted from
 * client-supplied headers, which would allow spoofing.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KeycloakUserSyncFilter implements WebFilter {

    private final UserService userService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String token = exchange.getRequest().getHeaders().getFirst("Authorization");

        if (token != null && token.startsWith("Bearer ")) {
            // FIX: Extract userId from JWT — never trust client-supplied X-User-ID header
            String userId = extractUserIdFromToken(token);

            if (userId != null) {
                return userService.validateUser(userId)
                        .flatMap(exists -> {
                            if (!exists) {
                                RegisterRequest registerRequest = buildRegisterRequest(token, userId);
                                if (registerRequest != null) {
                                    return userService.registerUser(registerRequest)
                                            .doOnSuccess(u -> log.info("Auto-registered new user: {}", userId))
                                            .onErrorResume(e -> {
                                                log.error("Failed to auto-register user {}: {}", userId, e.getMessage());
                                                return Mono.empty(); // allow request to proceed anyway
                                            })
                                            .then();
                                }
                            } else {
                                log.debug("User {} already exists, skipping sync.", userId);
                            }
                            return Mono.empty();
                        })
                        .onErrorResume(e -> {
                            log.warn("User validation failed for {}: {}. Proceeding without sync.", userId, e.getMessage());
                            return Mono.empty();
                        })
                        .then(Mono.defer(() -> {
                            // Strip any client-supplied X-User-ID to prevent spoofing,
                            // then inject the value derived from the verified JWT sub claim.
                            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                                    .headers(headers -> {
                                        headers.remove("X-User-ID");
                                        headers.add("X-User-ID", userId);
                                    })
                                    .build();
                            return chain.filter(exchange.mutate().request(mutatedRequest).build());
                        }));
            }
        }

        return chain.filter(exchange);
    }

    /**
     * Extracts the 'sub' (Keycloak user ID) claim from the JWT Bearer token.
     * Returns null if the token cannot be parsed.
     */
    private String extractUserIdFromToken(String token) {
        try {
            String rawToken = token.replace("Bearer", "").trim();
            SignedJWT signedJWT = SignedJWT.parse(rawToken);
            String sub = signedJWT.getJWTClaimsSet().getStringClaim("sub");
            if (sub == null) {
                log.error("JWT is missing 'sub' claim.");
            }
            return sub;
        } catch (Exception e) {
            log.error("Failed to extract sub from JWT: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Builds a RegisterRequest from JWT claims for first-time user auto-registration.
     */
    private RegisterRequest buildRegisterRequest(String token, String keycloakId) {
        try {
            String rawToken = token.replace("Bearer", "").trim();
            SignedJWT signedJWT = SignedJWT.parse(rawToken);
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            log.info("JWT claims for new user registration: {}", claims.toJSONObject());

            String email = claims.getStringClaim("email");
            String firstName = claims.getStringClaim("given_name");
            String lastName = claims.getStringClaim("family_name");

            if (email == null) {
                log.error("Cannot register user — 'email' claim missing from JWT.");
                return null;
            }

            RegisterRequest request = new RegisterRequest();
            request.setKeycloakId(keycloakId);
            request.setEmail(email);
            // Auth is via Keycloak; this field is stored hashed and never used for login.
            // Use a random UUID so no two accounts share a known password hash.
            request.setPassword(java.util.UUID.randomUUID().toString());
            request.setFirstName(firstName);
            request.setLastName(lastName);

            return request;

        } catch (Exception e) {
            log.error("Failed to build RegisterRequest from JWT: {}", e.getMessage());
            return null;
        }
    }
}
