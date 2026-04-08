package com.fitness.aiservice.service;

import com.fitness.aiservice.exception.RecommendationNotFoundException;
import com.fitness.aiservice.model.Recommendation;
import com.fitness.aiservice.repsitory.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecommendationService {
    private final RecommendationRepository recommendationRepository;

    public Page<Recommendation> getUserRecommendation(String userId, Pageable pageable) {
        return recommendationRepository.findByUserId(userId, pageable);
    }

    public Recommendation getActivityRecommendation(String activityId) {
        return recommendationRepository.findByActivityId(activityId)
                .orElseThrow(() -> new RecommendationNotFoundException(activityId));
    }
}
