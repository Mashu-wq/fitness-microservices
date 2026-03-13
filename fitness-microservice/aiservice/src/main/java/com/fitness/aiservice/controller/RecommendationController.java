package com.fitness.aiservice.controller;

import com.fitness.aiservice.model.Recommendation;
import com.fitness.aiservice.service.RecommendationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recommendations")
@Tag(name = "Recommendations", description = "Endpoints for AI-generated fitness recommendations")
public class RecommendationController {
    private final RecommendationService recommendationService;

    @Operation(
            summary = "Get recommendations for a user",
            description = "Returns a list of AI-generated recommendations for the specified user.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "List of recommendations",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = Recommendation.class)
                            )
                    ),
                    @ApiResponse(responseCode = "404", description = "No recommendations found for user")
            }
    )

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Recommendation>> getUserRecommendation(@PathVariable String userId) {
        return ResponseEntity.ok(recommendationService.getUserRecommendation(userId));

    }

    @Operation(
            summary = "Get recommendation for an activity",
            description = "Returns a recommendation for a specific activity.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Recommendation found",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = Recommendation.class)
                            )
                    ),
                    @ApiResponse(responseCode = "404", description = "Recommendation not found for activity")
            }
    )

    @GetMapping("/activity/{activityId}")
    public ResponseEntity<Recommendation> getActivityRecommendation(@PathVariable String activityId) {
        return ResponseEntity.ok(recommendationService.getActivityRecommendation(activityId));

    }
}
