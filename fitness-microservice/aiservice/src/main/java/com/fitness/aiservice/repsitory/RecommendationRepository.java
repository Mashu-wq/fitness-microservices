package com.fitness.aiservice.repsitory;

import com.fitness.aiservice.model.Recommendation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RecommendationRepository extends MongoRepository<Recommendation, String> {

    // Paginated — users accumulate recommendations over time; return page-by-page.
    Page<Recommendation> findByUserId(String userId, Pageable pageable);

    Optional<Recommendation> findByActivityId(String activityId);
}
