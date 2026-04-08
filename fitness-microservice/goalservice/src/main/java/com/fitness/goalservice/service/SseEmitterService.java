package com.fitness.goalservice.service;

import com.fitness.goalservice.dto.GoalProgressEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Maintains a registry of active SSE connections per user.
 * Multiple browser tabs / clients per user are supported via CopyOnWriteArrayList.
 */
@Service
@Slf4j
public class SseEmitterService {

    // userId → list of active emitters (one per open browser connection)
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> emitters =
            new ConcurrentHashMap<>();

    /**
     * Creates and registers a new SSE emitter for the given user.
     * Timeout is set to 5 minutes; the client should reconnect after that.
     */
    public SseEmitter createEmitter(String userId) {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L); // 5 min

        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable cleanup = () -> {
            CopyOnWriteArrayList<SseEmitter> list = emitters.get(userId);
            if (list != null) {
                list.remove(emitter);
                if (list.isEmpty()) emitters.remove(userId);
            }
        };

        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        log.debug("SSE emitter registered for user {} ({} total)", userId,
                emitters.getOrDefault(userId, new CopyOnWriteArrayList<>()).size());
        return emitter;
    }

    /**
     * Pushes a progress update to all active SSE connections for the given user.
     * Stale/broken emitters are removed automatically on send failure.
     */
    public void pushProgressUpdate(String userId, GoalProgressEvent event) {
        List<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) return;

        List<SseEmitter> dead = new CopyOnWriteArrayList<>();
        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("goal-progress")
                        .data(event));
            } catch (IOException e) {
                log.debug("SSE emitter dead for user {}, removing", userId);
                dead.add(emitter);
            }
        }

        if (!dead.isEmpty()) {
            userEmitters.removeAll(dead);
            if (userEmitters.isEmpty()) emitters.remove(userId);
        }
    }
}
