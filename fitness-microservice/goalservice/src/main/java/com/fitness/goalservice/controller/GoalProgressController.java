package com.fitness.goalservice.controller;

import com.fitness.goalservice.dto.GoalProgressResponse;
import com.fitness.goalservice.service.GoalService;
import com.fitness.goalservice.service.SseEmitterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
public class GoalProgressController {

    private final GoalService goalService;
    private final SseEmitterService sseEmitterService;

    /** Progress for a single goal */
    @GetMapping("/{goalId}/progress")
    public ResponseEntity<GoalProgressResponse> getGoalProgress(
            @PathVariable String goalId,
            @RequestHeader("X-User-ID") String userId) {
        return ResponseEntity.ok(goalService.getGoalProgress(goalId, userId));
    }

    /** Aggregated progress across all goals for the user */
    @GetMapping("/progress")
    public ResponseEntity<List<GoalProgressResponse>> getAllProgress(
            @RequestHeader("X-User-ID") String userId) {
        return ResponseEntity.ok(goalService.getAllProgressForUser(userId));
    }

    /**
     * SSE stream — client subscribes here to receive real-time goal-progress events.
     * Event name: "goal-progress", data: GoalProgressEvent JSON.
     * Connection auto-closes after 5 minutes; client should reconnect.
     */
    @GetMapping(value = "/progress/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamProgress(@RequestHeader("X-User-ID") String userId) {
        return sseEmitterService.createEmitter(userId);
    }
}
