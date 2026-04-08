package com.fitness.goalservice.controller;

import com.fitness.goalservice.dto.GoalRequest;
import com.fitness.goalservice.dto.GoalResponse;
import com.fitness.goalservice.service.GoalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    @PostMapping
    public ResponseEntity<GoalResponse> createGoal(
            @RequestHeader("X-User-ID") String userId,
            @Valid @RequestBody GoalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(goalService.createGoal(userId, request));
    }

    @GetMapping
    public ResponseEntity<List<GoalResponse>> getUserGoals(
            @RequestHeader("X-User-ID") String userId) {
        return ResponseEntity.ok(goalService.getUserGoals(userId));
    }

    @GetMapping("/{goalId}")
    public ResponseEntity<GoalResponse> getGoalById(
            @PathVariable String goalId,
            @RequestHeader("X-User-ID") String userId) {
        return ResponseEntity.ok(goalService.getGoalById(goalId, userId));
    }

    @PatchMapping("/{goalId}/pause")
    public ResponseEntity<Void> pauseGoal(
            @PathVariable String goalId,
            @RequestHeader("X-User-ID") String userId) {
        goalService.pauseGoal(goalId, userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{goalId}/resume")
    public ResponseEntity<Void> resumeGoal(
            @PathVariable String goalId,
            @RequestHeader("X-User-ID") String userId) {
        goalService.resumeGoal(goalId, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{goalId}")
    public ResponseEntity<Void> deleteGoal(
            @PathVariable String goalId,
            @RequestHeader("X-User-ID") String userId) {
        goalService.deleteGoal(goalId, userId);
        return ResponseEntity.noContent().build();
    }
}
