package com.fitness.aiservice.exception;

public class RecommendationNotFoundException extends RuntimeException {

    public RecommendationNotFoundException(String activityId) {
        super("Recommendation not found for activityId: " + activityId);
    }
}
