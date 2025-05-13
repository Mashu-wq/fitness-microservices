package com.fitness.userservice.repository;

import com.fitness.userservice.model.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    boolean existsByEmail(@NotBlank(message = "Email is required") @Email(message = "Invalid email format") String email);


    User findByEmail(@NotBlank(message = "Email is required") @Email(message = "Invalid email format") String email);

    Boolean existsByKeycloakId(String userId);
}
