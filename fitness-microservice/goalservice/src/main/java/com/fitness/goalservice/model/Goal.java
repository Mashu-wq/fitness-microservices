package com.fitness.goalservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Document(collection = "goals")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Goal {

    @Id
    private String id;

    @Indexed
    private String userId;          // Keycloak sub claim — matches X-User-ID

    private String title;           // e.g. "Run 50km this month"
    private String description;

    private GoalType type;          // DISTANCE | CALORIES | DURATION | FREQUENCY
    private String targetActivityType;  // null = any activity; "RUNNING", "CYCLING", etc.

    private Double targetValue;     // 50.0 (km), 5000 (kcal), 120 (min), 10 (sessions)
    private String unit;            // "KM", "KCAL", "MINUTES", "SESSIONS"

    private GoalPeriod period;      // WEEKLY | MONTHLY | CUSTOM
    private LocalDate startDate;
    private LocalDate endDate;

    @Builder.Default
    private GoalStatus status = GoalStatus.ACTIVE;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
