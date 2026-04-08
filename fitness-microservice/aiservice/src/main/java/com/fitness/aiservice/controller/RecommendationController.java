package com.fitness.aiservice.controller;

import com.fitness.aiservice.model.Recommendation;
import com.fitness.aiservice.service.RecommendationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recommendations")
@Tag(name = "Recommendations", description = "Endpoints for AI-generated fitness recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    @Operation(
            summary = "Get paginated recommendations for a user",
            description = "Returns a page of AI-generated recommendations for the authenticated user, newest first.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Page of recommendations",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = Page.class))),
                    @ApiResponse(responseCode = "403", description = "Access denied — cannot view another user's recommendations")
            }
    )
    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<Recommendation>> getUserRecommendation(
            @RequestHeader("X-User-ID") String requestingUserId,
            @PathVariable String userId,
            @Parameter(description = "0-based page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 50)") @RequestParam(defaultValue = "20") int size) {

        if (!requestingUserId.equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Cap page size to 50 to prevent excessive data fetches.
        int safeSize = Math.min(size, 50);
        Pageable pageable = PageRequest.of(page, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        return ResponseEntity.ok(recommendationService.getUserRecommendation(userId, pageable));
    }

    @Operation(
            summary = "Get recommendation for a specific activity",
            description = "Returns the AI-generated recommendation for the given activity.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Recommendation found",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = Recommendation.class))),
                    @ApiResponse(responseCode = "404", description = "Recommendation not found for activity")
            }
    )
    @GetMapping("/activity/{activityId}")
    public ResponseEntity<Recommendation> getActivityRecommendation(@PathVariable String activityId) {
        return ResponseEntity.ok(recommendationService.getActivityRecommendation(activityId));
    }
}
