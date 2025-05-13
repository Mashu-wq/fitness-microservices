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

@Component
@Slf4j
@RequiredArgsConstructor
public class KeycloakUserSyncFilter implements WebFilter {
    private final UserService userService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain){
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-ID");
        String token = exchange.getRequest().getHeaders().getFirst("Authorization");

        if(userId != null && token != null) {
            return userService.validateUser(userId)
                    .flatMap(exist -> {
                        if(!exist){
                            //Register user
                            RegisterRequest registerRequest = getUserDetails(token);
                            if(registerRequest != null){
                                return userService.registerUser(registerRequest)
                                        .then(Mono.empty());
                            }else {
                                return Mono.empty();
                            }

                        }
                        else{
                            log.info("User already exist, Skipping sync.");
                            return Mono.empty();
                        }
                    })
                    .then(Mono.defer(() -> {
                ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                        .header("X-User-ID", userId)
                        .build();
                return chain.filter(exchange.mutate().request(mutatedRequest).build());
            }));

        }

        return chain.filter(exchange);
    }

//    private RegisterRequest getUserDetails(String token) {
//        try{
//            String tokenWithoutBearer = token.replace("Bearer", "").trim();
//            SignedJWT signedJWT = SignedJWT.parse(tokenWithoutBearer);
//            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
//
//            // Log claims for debugging
//            log.info("JWT Claims: {}", claims.toJSONObject());
//
//            RegisterRequest registerRequest = new RegisterRequest();
//            registerRequest.setEmail(claims.getStringClaim("email"));
//            registerRequest.setKeycloakId(claims.getStringClaim("sub"));
//            registerRequest.setPassword("dummy@123123");
//            registerRequest.setFirstName(claims.getStringClaim("given_name"));
//            registerRequest.setLastName(claims.getStringClaim("family_name"));
//
//            return registerRequest;
//
//        }catch (Exception e){
//            e.printStackTrace();
//            return null;
//        }
//    }

    private RegisterRequest getUserDetails(String token) {
        try {
            // Remove the "Bearer " prefix if present
            String tokenWithoutBearer = token.replace("Bearer", "").trim();

            // Parse the JWT
            SignedJWT signedJWT = SignedJWT.parse(tokenWithoutBearer);
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            // Log the entire JWT claims for debugging purposes
            log.info("JWT Claims: {}", claims.toJSONObject());

            // Retrieve claims from the JWT
            String email = claims.getStringClaim("email");
            String keycloakId = claims.getStringClaim("sub");  // Keycloak user ID (sub)
            String firstName = claims.getStringClaim("given_name");
            String lastName = claims.getStringClaim("family_name");

            // Ensure that keycloakId and email are available, if not log an error
            if (keycloakId == null) {
                log.error("Keycloak ID (sub) not found in the token.");
            }
            if (email == null) {
                log.error("Email not found in the token.");
            }

            // Construct the RegisterRequest
            RegisterRequest registerRequest = new RegisterRequest();
            registerRequest.setEmail(email);
            registerRequest.setKeycloakId(keycloakId);  // Set the keycloakId (sub)
            registerRequest.setPassword("dummy@123123");  // Temporary password (set as per your logic)
            registerRequest.setFirstName(firstName);
            registerRequest.setLastName(lastName);

            return registerRequest;

        } catch (Exception e) {
            // Log the exception for debugging
            log.error("Error while parsing the JWT token: ", e);
            return null;  // Return null if there's an error in parsing the JWT
        }
    }


}
