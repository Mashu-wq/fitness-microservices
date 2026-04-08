package com.fitness.userservice.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserResponse {

    private String id;
    private String keycloakId;
    private String email;
    // password intentionally omitted — never expose credentials in API responses
    private String firstName;
    private String lastName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
