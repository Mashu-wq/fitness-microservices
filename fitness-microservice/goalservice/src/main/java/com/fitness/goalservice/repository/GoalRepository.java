package com.fitness.goalservice.repository;

import com.fitness.goalservice.model.Goal;
import com.fitness.goalservice.model.GoalStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GoalRepository extends MongoRepository<Goal, String> {

    List<Goal> findByUserId(String userId);

    List<Goal> findByUserIdAndStatus(String userId, GoalStatus status);
}
