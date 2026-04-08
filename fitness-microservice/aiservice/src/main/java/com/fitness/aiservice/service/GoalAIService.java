package com.fitness.aiservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitness.aiservice.model.GoalProgressEvent;
import com.fitness.aiservice.model.Recommendation;
import com.fitness.aiservice.repsitory.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Builds an adaptive coaching prompt from a goal milestone event and saves
 * the resulting recommendation.  The goalId is used as the activityId field
 * so existing pagination / lookup endpoints remain unchanged.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoalAIService {

    private final GeminiService geminiService;
    private final ObjectMapper objectMapper;
    private final RecommendationRepository recommendationRepository;

    public void generateGoalRecommendation(GoalProgressEvent event) {
        log.info("Generating goal coaching for goalId={} milestone={}%",
                event.getGoalId(), event.getMilestone() != null ? event.getMilestone().intValue() : "?");

        String prompt = buildPrompt(event);
        String aiResponse = geminiService.getAnswer(prompt);
        Recommendation rec = parseResponse(event, aiResponse);
        recommendationRepository.save(rec);
        log.info("Saved goal coaching recommendation for goalId={}", event.getGoalId());
    }

    private String buildPrompt(GoalProgressEvent event) {
        int milestone = event.getMilestone() != null ? event.getMilestone().intValue() : 0;
        return String.format("""
            A user has reached the %d%% milestone for their fitness goal. \
            Provide adaptive coaching recommendations in the following JSON format:

            {
                "analysis": {
                    "overall": "Overall progress assessment",
                    "pace": "Pace/rate-of-progress analysis",
                    "heartRate": "N/A for goal coaching",
                    "caloriesBurned": "Effort estimation"
                },
                "improvements": [
                    { "area": "Area name", "recommendation": "Detailed recommendation" }
                ],
                "suggestions": [
                    { "workout": "Workout name", "description": "How this supports the goal" }
                ],
                "safety": [
                    "Safety or sustainability point"
                ]
            }

            Goal details:
            - Title: %s
            - Type: %s
            - Target activity: %s
            - Progress: %.1f / %.1f %s (%.1f%%)
            - Days remaining: %d
            - Period: %s → %s

            Focus on motivational coaching, pacing advice for the remaining %d days, \
            and concrete next-step suggestions. Ensure the response follows the EXACT JSON format shown above.
            """,
                milestone,
                event.getGoalTitle(),
                event.getGoalType(),
                event.getTargetActivityType() != null ? event.getTargetActivityType() : "any",
                event.getCurrentValue(), event.getTargetValue(), event.getUnit(),
                event.getPercentageComplete(),
                event.getDaysRemaining(),
                event.getStartDate(), event.getEndDate(),
                event.getDaysRemaining());
    }

    private Recommendation parseResponse(GoalProgressEvent event, String aiResponse) {
        try {
            JsonNode root = objectMapper.readTree(aiResponse);
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                log.warn("Gemini returned no candidates for goalId {}", event.getGoalId());
                return defaultRecommendation(event);
            }

            String text = candidates.get(0).path("content").path("parts").get(0).path("text").asText();
            String json = text.replaceAll("```json\\n?", "").replaceAll("\\n?```", "").trim();

            JsonNode analysisJson = objectMapper.readTree(json);
            JsonNode analysisNode = analysisJson.path("analysis");

            StringBuilder analysis = new StringBuilder();
            appendSection(analysis, analysisNode, "overall", "Overall:");
            appendSection(analysis, analysisNode, "pace", "Pace:");
            appendSection(analysis, analysisNode, "caloriesBurned", "Effort:");

            List<String> improvements = extractList(analysisJson.path("improvements"), "area", "recommendation");
            List<String> suggestions = extractList(analysisJson.path("suggestions"), "workout", "description");
            List<String> safety = extractTextArray(analysisJson.path("safety"));

            return Recommendation.builder()
                    .activityId(event.getGoalId())   // reuse activityId field for goalId
                    .userId(event.getUserId())
                    .activityType("GOAL_COACHING")
                    .recommendation(analysis.toString().trim())
                    .improvements(improvements)
                    .suggestions(suggestions)
                    .safety(safety)
                    .createdAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse Gemini response for goalId {}: {}", event.getGoalId(), e.getMessage(), e);
            return defaultRecommendation(event);
        }
    }

    private Recommendation defaultRecommendation(GoalProgressEvent event) {
        return Recommendation.builder()
                .activityId(event.getGoalId())
                .userId(event.getUserId())
                .activityType("GOAL_COACHING")
                .recommendation("Keep pushing — you're making progress!")
                .improvements(Collections.singletonList("Continue consistent training."))
                .suggestions(Collections.singletonList("Maintain your current routine."))
                .safety(Collections.singletonList("Listen to your body and rest when needed."))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private void appendSection(StringBuilder sb, JsonNode node, String key, String prefix) {
        if (!node.path(key).isMissingNode()) {
            sb.append(prefix).append(node.path(key).asText()).append("\n\n");
        }
    }

    private List<String> extractList(JsonNode arr, String keyField, String valueField) {
        if (!arr.isArray() || arr.isEmpty()) {
            return Collections.singletonList("None available.");
        }
        List<String> result = new java.util.ArrayList<>();
        arr.forEach(item -> result.add(item.path(keyField).asText() + ": " + item.path(valueField).asText()));
        return result;
    }

    private List<String> extractTextArray(JsonNode arr) {
        if (!arr.isArray() || arr.isEmpty()) {
            return Collections.singletonList("None available.");
        }
        List<String> result = new java.util.ArrayList<>();
        arr.forEach(item -> result.add(item.asText()));
        return result;
    }
}
