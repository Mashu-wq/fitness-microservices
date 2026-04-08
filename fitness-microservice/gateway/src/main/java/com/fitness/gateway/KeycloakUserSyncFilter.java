package com.fitness.gateway;

import com.fitness.gateway.user.RegisterRequest;
import com.fitness.gateway.user.UserService;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Runs AFTER Spring Security (which is at order -100) has already validated the JWT
 * signature. This filter extracts verified claims, auto-registers new users in MongoDB,
 * and injects X-User-ID into downstream requests.
 *
 * SECURITY: userId is always derived from the verified JWT sub claim.
 * Client-supplied X-User-ID headers are stripped before injection.
 */
@Component
@Order(1)
@Slf4j
@RequiredArgsConstructor
public class KeycloakUserSyncFilter implements WebFilter {

    private final UserService userService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return chain.filter(exchange);
        }

        // Parse the JWT exactly once — extract all claims we need in a single call.
        JWTClaimsSet claims = parseClaims(authHeader);
        if (claims == null) {
            return chain.filter(exchange);
        }

        String userId = getStringClaim(claims, "sub");
        if (userId == null) {
            log.error("JWT is missing 'sub' claim — cannot identify user.");
            return chain.filter(exchange);
        }

        return userService.validateUser(userId)
                .flatMap(exists -> {
                    if (!exists) {
                        RegisterRequest registerRequest = buildRegisterRequest(claims, userId);
                        if (registerRequest != null) {
                            return userService.registerUser(registerRequest)
                                    .doOnSuccess(u -> log.info("Auto-registered new user: {}", userId))
                                    .onErrorResume(e -> {
                                        log.error("Failed to auto-register user {}: {}", userId, e.getMessage());
                                        return Mono.empty();
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
                    // then inject the JWT-derived value.
                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                            .headers(headers -> {
                                headers.remove("X-User-ID");
                                headers.add("X-User-ID", userId);
                            })
                            .build();
                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                }));
    }

    /**
     * Parses the raw JWT Bearer token into a JWTClaimsSet in a single operation.
     * Spring Security has already verified the signature before this filter runs.
     * Returns null if parsing fails (malformed token structure).
     */
    private JWTClaimsSet parseClaims(String authHeader) {
        try {
            String rawToken = authHeader.replace("Bearer", "").trim();
            return SignedJWT.parse(rawToken).getJWTClaimsSet();
        } catch (Exception e) {
            log.error("Failed to parse JWT claims: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Builds a RegisterRequest from already-parsed JWT claims.
     * No second JWT parse — reuses the JWTClaimsSet from parseClaims().
     */
    private RegisterRequest buildRegisterRequest(JWTClaimsSet claims, String keycloakId) {
        String email = getStringClaim(claims, "email");
        if (email == null) {
            log.error("Cannot register user {} — 'email' claim missing from JWT.", keycloakId);
            return null;
        }

        RegisterRequest request = new RegisterRequest();
        request.setKeycloakId(keycloakId);
        request.setEmail(email);
        // Auth is exclusively via Keycloak. This field is BCrypt-hashed and never used
        // for login. A random UUID ensures no two accounts share a guessable hash.
        request.setPassword(UUID.randomUUID().toString());
        request.setFirstName(getStringClaim(claims, "given_name"));
        request.setLastName(getStringClaim(claims, "family_name"));

        log.info("Building RegisterRequest for new user — email: {}, keycloakId: {}", email, keycloakId);
        return request;
    }

    private String getStringClaim(JWTClaimsSet claims, String name) {
        try {
            return claims.getStringClaim(name);
        } catch (Exception e) {
            log.warn("Could not read JWT claim '{}': {}", name, e.getMessage());
            return null;
        }
    }
}
