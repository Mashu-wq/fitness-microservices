package com.fitness.goalservice.repository;

import com.fitness.goalservice.model.GoalProgress;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GoalProgressRepository extends MongoRepository<GoalProgress, String> {

    // One progress document per goal (unique index on goalId enforces this)
    Optional<GoalProgress> findByGoalId(String goalId);

    List<GoalProgress> findByUserId(String userId);
}
