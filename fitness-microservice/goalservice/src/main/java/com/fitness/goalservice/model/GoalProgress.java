package com.fitness.goalservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Tracks the cumulative progress for one Goal.
 * One document per Goal — updated atomically each time a relevant activity arrives.
 */
@Document(collection = "goal_progress")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GoalProgress {

    @Id
    private String id;

    @Indexed(unique = true)
    private String goalId;

    @Indexed
    private String userId;

    private Double targetValue;
    private Double currentValue;
    private Double percentageComplete;  // 0.0 – 100.0
    private Boolean completed;

    // Milestones (25, 50, 75, 100) already published — prevents duplicate AI events
    @Builder.Default
    private List<Double> notifiedMilestones = new ArrayList<>();

    private LocalDateTime lastUpdated;

    @CreatedDate
    private LocalDateTime createdAt;
}
