package com.fitness.userservice.service;


import com.fitness.userservice.dto.RegisterRequest;
import com.fitness.userservice.dto.UserResponse;
import com.fitness.userservice.model.User;
import com.fitness.userservice.model.UserRole;
import com.fitness.userservice.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserRepository repository;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserResponse register(@Valid RegisterRequest request) {

        if (repository.existsByEmail(request.getEmail())) {
            log.info("User with email {} already exists, returning existing user.", request.getEmail());
            return repository.findByEmail(request.getEmail())
                    .map(this::mapToUserResponse)
                    .orElseThrow(() -> new RuntimeException("User data inconsistency for email: " + request.getEmail()));
        }

        User user = new User();
        user.setKeycloakId(request.getKeycloakId());  // FIX: was never set — always null in DB
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));  // FIX: BCrypt hash instead of plain text
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setRole(UserRole.USER);

        User savedUser = repository.save(user);

        log.info("Registered new user — email: {}, keycloakId: {}", request.getEmail(), request.getKeycloakId());

        return mapToUserResponse(savedUser);
    }

    public UserResponse getUserProfile(String userId) {
        User user = repository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
        return mapToUserResponse(user);
    }

    public Boolean existsByUserId(String userId) {
        log.info("Validating user by keycloakId: {}", userId);
        return repository.existsByKeycloakId(userId);
    }

    private UserResponse mapToUserResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setKeycloakId(user.getKeycloakId());
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());
        return response;
    }
}
