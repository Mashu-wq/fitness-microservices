package com.fitness.userservice.model;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import com.fitness.userservice.model.UserRole;

import java.time.LocalDateTime;

@Data
@Document(collection = "users")
public class User {

    @Id
    private String id;
    private String email;
    private String keycloakId;
    private String password;
    private String firstName;
    private String lastName;

    private UserRole role = UserRole.USER;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
