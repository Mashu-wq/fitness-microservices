package com.fitness.aiservice.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "recommendations")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "AI-generated recommendation for a fitness activity")
public class Recommendation {

    @Schema(description = "Unique identifier", example = "rec123")
    private String id;

    @Schema(description = "Activity ID", example = "act456")
    private String activityId;

    @Schema(description = "User ID", example = "user789")
    private String userId;

    @Schema(description = "Type of activity", example = "RUNNING")
    private String activityType;

    @Schema(description = "Main recommendation text")
    private String recommendation;

    @Schema(description = "List of improvement suggestions")
    private List<String> improvements;

    @Schema(description = "List of workout suggestions")
    private List<String> suggestions;

    @Schema(description = "List of safety notes")
    private List<String> safety;

    @Schema(description = "Creation timestamp", example = "2025-07-11T03:00:00")
    private LocalDateTime createdAt;

}
