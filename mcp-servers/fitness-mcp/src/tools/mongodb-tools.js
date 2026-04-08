import { z } from "zod";
import { collection } from "../lib/mongo.js";

// ── Database + collection names ───────────────────────────────────────────────
const DB = {
  users: "fitnessuser",
  activities: "fitnessactivity",
  recommendations: "fitnessrecommendation",
  goals: "fitnessgoal",
  leaderboard: "fitnessleaderboard",
  wearable: "fitnesswearable",
};

// ── Formatting helpers ────────────────────────────────────────────────────────

function fmt(obj) {
  return JSON.stringify(obj, null, 2);
}

function header(title) {
  const line = "─".repeat(60);
  return `\n${line}\n  ${title}\n${line}\n`;
}

// ── Tool registrations ────────────────────────────────────────────────────────

export function registerMongoTools(server) {

  // ── 1. list_users ───────────────────────────────────────────────────────────
  server.tool(
    "list_users",
    "List users stored in MongoDB (fitnessuser DB). Optionally filter by email or keycloakId. Returns id, email, firstName, lastName, keycloakId, role, createdAt.",
    {
      email: z.string().optional().describe("Filter by exact email address"),
      keycloakId: z.string().optional().describe("Filter by Keycloak user ID (sub claim)"),
      limit: z.number().int().min(1).max(100).default(20).describe("Max results to return (default 20, max 100)"),
    },
    async ({ email, keycloakId, limit }) => {
      try {
        const col = await collection(DB.users, "users");
        const query = {};
        if (email) query.email = email;
        if (keycloakId) query.keycloakId = keycloakId;

        const users = await col
          .find(query, {
            projection: { password: 0 }, // never return password hash
          })
          .limit(limit)
          .sort({ createdAt: -1 })
          .toArray();

        if (users.length === 0) {
          return { content: [{ type: "text", text: "No users found matching the filter." }] };
        }

        const text =
          header(`Users (${users.length} of max ${limit})`) +
          users
            .map(
              (u, i) =>
                `[${i + 1}] id=${u._id}\n    email=${u.email}\n    keycloakId=${u.keycloakId ?? "—"}\n    name=${u.firstName ?? ""} ${u.lastName ?? ""}\n    role=${u.role}\n    created=${u.createdAt ?? "—"}`
            )
            .join("\n\n");

        return { content: [{ type: "text", text }] };
      } catch (err) {
        return { content: [{ type: "text", text: `MongoDB error: ${err.message}` }] };
      }
    }
  );

  // ── 2. get_user ─────────────────────────────────────────────────────────────
  server.tool(
    "get_user",
    "Get a single user's full profile from MongoDB by their MongoDB _id or Keycloak ID (sub claim). Useful for cross-referencing JWT sub claims with database records.",
    {
      id: z.string().optional().describe("MongoDB _id of the user document"),
      keycloakId: z.string().optional().describe("Keycloak sub claim (preferred — matches what X-User-ID header carries)"),
    },
    async ({ id, keycloakId }) => {
      if (!id && !keycloakId) {
        return { content: [{ type: "text", text: "Provide at least one of: id, keycloakId" }] };
      }
      try {
        const col = await collection(DB.users, "users");
        const query = keycloakId ? { keycloakId } : { _id: id };
        const user = await col.findOne(query, { projection: { password: 0 } });

        if (!user) {
          return { content: [{ type: "text", text: "User not found." }] };
        }

        return { content: [{ type: "text", text: header("User Profile") + fmt(user) }] };
      } catch (err) {
        return { content: [{ type: "text", text: `MongoDB error: ${err.message}` }] };
      }
    }
  );

  // ── 3. list_activities ──────────────────────────────────────────────────────
  server.tool(
    "list_activities",
    "List fitness activities from MongoDB (fitnessactivity DB). Filter by userId or activity type. Sorted newest first.",
    {
      userId: z.string().optional().describe("Filter by Keycloak user ID (matches X-User-ID header value)"),
      type: z
        .enum(["RUNNING", "WALKING", "CYCLING", "SWIMMING", "WEIGHT_TRAINING", "YOGA", "HIIT", "CARDIO", "STRETCHING", "OTHER"])
        .optional()
        .describe("Filter by activity type"),
      limit: z.number().int().min(1).max(100).default(20).describe("Max results (default 20, max 100)"),
    },
    async ({ userId, type, limit }) => {
      try {
        const col = await collection(DB.activities, "activities");
        const query = {};
        if (userId) query.userId = userId;
        if (type) query.type = type;

        const activities = await col
          .find(query)
          .limit(limit)
          .sort({ createdAt: -1 })
          .toArray();

        if (activities.length === 0) {
          return { content: [{ type: "text", text: "No activities found." }] };
        }

        const text =
          header(`Activities (${activities.length} of max ${limit})`) +
          activities
            .map(
              (a, i) =>
                `[${i + 1}] id=${a._id}\n    userId=${a.userId}\n    type=${a.type}  duration=${a.duration}min  calories=${a.caloriesBurned}kcal\n    startTime=${a.startTime ?? "—"}\n    metrics=${fmt(a.additionalMetrics ?? {})}\n    created=${a.createdAt ?? "—"}`
            )
            .join("\n\n");

        return { content: [{ type: "text", text }] };
      } catch (err) {
        return { content: [{ type: "text", text: `MongoDB error: ${err.message}` }] };
      }
    }
  );

  // ── 4. get_activity ─────────────────────────────────────────────────────────
  server.tool(
    "get_activity",
    "Get a single activity document from MongoDB by its ID. Also shows whether an AI recommendation has been generated for it.",
    {
      id: z.string().describe("MongoDB _id of the activity document"),
    },
    async ({ id }) => {
      try {
        const actCol = await collection(DB.activities, "activities");
        const activity = await actCol.findOne({ _id: id });

        if (!activity) {
          return { content: [{ type: "text", text: `Activity '${id}' not found.` }] };
        }

        // Also check if a recommendation exists for this activity
        const recCol = await collection(DB.recommendations, "recommendations");
        const hasRec = await recCol.findOne({ activityId: id }, { projection: { _id: 1 } });

        const text =
          header("Activity") +
          fmt(activity) +
          `\n\nAI Recommendation generated: ${hasRec ? "YES  (activityId=" + id + ")" : "NO — still pending or failed"}`;

        return { content: [{ type: "text", text }] };
      } catch (err) {
        return { content: [{ type: "text", text: `MongoDB error: ${err.message}` }] };
      }
    }
  );

  // ── 5. list_recommendations ─────────────────────────────────────────────────
  server.tool(
    "list_recommendations",
    "List AI-generated recommendations from MongoDB (fitnessrecommendation DB). Filter by userId. Sorted newest first.",
    {
      userId: z.string().optional().describe("Filter by Keycloak user ID"),
      limit: z.number().int().min(1).max(50).default(10).describe("Max results (default 10, max 50)"),
    },
    async ({ userId, limit }) => {
      try {
        const col = await collection(DB.recommendations, "recommendations");
        const query = userId ? { userId } : {};

        const recs = await col
          .find(query)
          .limit(limit)
          .sort({ createdAt: -1 })
          .toArray();

        if (recs.length === 0) {
          return { content: [{ type: "text", text: "No recommendations found." }] };
        }

        const text =
          header(`Recommendations (${recs.length} of max ${limit})`) +
          recs
            .map(
              (r, i) =>
                `[${i + 1}] id=${r._id}\n    activityId=${r.activityId}\n    userId=${r.userId}\n    type=${r.activityType}\n    created=${r.createdAt ?? "—"}\n    analysis:\n      ${(r.recommendation ?? "").replace(/\n/g, "\n      ")}`
            )
            .join("\n\n");

        return { content: [{ type: "text", text }] };
      } catch (err) {
        return { content: [{ type: "text", text: `MongoDB error: ${err.message}` }] };
      }
    }
  );

  // ── 6. get_recommendation ───────────────────────────────────────────────────
  server.tool(
    "get_recommendation",
    "Get the full AI recommendation document for a specific activity. Shows the complete analysis, improvements, suggestions, and safety notes.",
    {
      activityId: z.string().describe("The activity ID to look up the recommendation for"),
    },
    async ({ activityId }) => {
      try {
        const col = await collection(DB.recommendations, "recommendations");
        const rec = await col.findOne({ activityId });

        if (!rec) {
          return {
            content: [
              {
                type: "text",
                text: `No recommendation found for activityId '${activityId}'.\n\nPossible reasons:\n  1. The activity was just posted — RabbitMQ consumer may still be processing it\n  2. The Gemini API call failed — check the DLQ with get_dlq_messages\n  3. The activityId is incorrect`,
              },
            ],
          };
        }

        const text =
          header("AI Recommendation") +
          `Activity ID : ${rec.activityId}\nUser ID     : ${rec.userId}\nType        : ${rec.activityType}\nCreated     : ${rec.createdAt ?? "—"}\n\n` +
          `── Analysis ──────────────────────────────────────────────\n${rec.recommendation ?? "—"}\n\n` +
          `── Improvements ──────────────────────────────────────────\n${(rec.improvements ?? []).map((i, n) => `  ${n + 1}. ${i}`).join("\n")}\n\n` +
          `── Workout Suggestions ───────────────────────────────────\n${(rec.suggestions ?? []).map((s, n) => `  ${n + 1}. ${s}`).join("\n")}\n\n` +
          `── Safety Notes ──────────────────────────────────────────\n${(rec.safety ?? []).map((s, n) => `  ${n + 1}. ${s}`).join("\n")}`;

        return { content: [{ type: "text", text }] };
      } catch (err) {
        return { content: [{ type: "text", text: `MongoDB error: ${err.message}` }] };
      }
    }
  );

  // ── 7. list_goals ───────────────────────────────────────────────────────────
  server.tool(
    "list_goals",
    "List fitness goals from MongoDB (fitnessgoal DB). Filter by userId and/or status. Sorted newest first.",
    {
      userId: z.string().optional().describe("Filter by Keycloak user ID"),
      status: z.enum(["ACTIVE", "COMPLETED", "FAILED", "PAUSED"]).optional().describe("Filter by goal status"),
      limit: z.number().int().min(1).max(100).default(20).describe("Max results (default 20, max 100)"),
    },
    async ({ userId, status, limit }) => {
      try {
        const col = await collection(DB.goals, "goals");
        const query = {};
        if (userId) query.userId = userId;
        if (status) query.status = status;

        const goals = await col
          .find(query)
          .limit(limit)
          .sort({ createdAt: -1 })
          .toArray();

        if (goals.length === 0) {
          return { content: [{ type: "text", text: "No goals found matching the filter." }] };
        }

        const text =
          header(`Goals (${goals.length} of max ${limit})`) +
          goals
            .map(
              (g, i) =>
                `[${i + 1}] id=${g._id}\n    userId=${g.userId}\n    title="${g.title}"\n    type=${g.type}  period=${g.period}  status=${g.status}\n    target=${g.targetValue} ${g.unit}  activityType=${g.targetActivityType ?? "any"}\n    range=${g.startDate} → ${g.endDate}`
            )
            .join("\n\n");

        return { content: [{ type: "text", text }] };
      } catch (err) {
        return { content: [{ type: "text", text: `MongoDB error: ${err.message}` }] };
      }
    }
  );

  // ── 8. list_goal_progress ───────────────────────────────────────────────────
  server.tool(
    "list_goal_progress",
    "List goal progress documents from MongoDB (fitnessgoal DB). Shows current vs target values, percentage complete, and notified milestones.",
    {
      userId: z.string().optional().describe("Filter by Keycloak user ID"),
      goalId: z.string().optional().describe("Fetch progress for a specific goal ID"),
    },
    async ({ userId, goalId }) => {
      try {
        const progressCol = await collection(DB.goals, "goal_progress");
        const goalCol = await collection(DB.goals, "goals");

        const query = {};
        if (userId) query.userId = userId;
        if (goalId) query.goalId = goalId;

        const progressDocs = await progressCol
          .find(query)
          .sort({ createdAt: -1 })
          .toArray();

        if (progressDocs.length === 0) {
          return { content: [{ type: "text", text: "No progress documents found." }] };
        }

        const lines = await Promise.all(
          progressDocs.map(async (p, i) => {
            const goal = await goalCol.findOne({ _id: p.goalId }, { projection: { title: 1, type: 1, unit: 1 } });
            const title = goal ? `"${goal.title}" (${goal.type})` : `goalId=${p.goalId}`;
            const bar = buildProgressBar(p.percentageComplete ?? 0);
            return (
              `[${i + 1}] ${title}\n` +
              `    goalId=${p.goalId}  userId=${p.userId}\n` +
              `    progress: ${p.currentValue}/${p.targetValue} ${goal?.unit ?? ""}\n` +
              `    ${bar} ${(p.percentageComplete ?? 0).toFixed(1)}%\n` +
              `    completed=${p.completed}  milestones notified=${JSON.stringify(p.notifiedMilestones ?? [])}\n` +
              `    lastUpdated=${p.lastUpdated ?? "—"}`
            );
          })
        );

        return {
          content: [{ type: "text", text: header(`Goal Progress (${progressDocs.length} docs)`) + lines.join("\n\n") }],
        };
      } catch (err) {
        return { content: [{ type: "text", text: `MongoDB error: ${err.message}` }] };
      }
    }
  );

  // ── 9. list_leaderboard ─────────────────────────────────────────────────────
  server.tool(
    "list_leaderboard",
    "Query the leaderboard scores from MongoDB (fitnessleaderboard DB). Filter by metric (DISTANCE/CALORIES/DURATION/FREQUENCY), period key (e.g. '2026-W14', '2026-04', 'ALL'), or userId to inspect a specific user's standing.",
    {
      metric: z.enum(["DISTANCE", "CALORIES", "DURATION", "FREQUENCY"]).optional()
        .describe("Filter by leaderboard metric"),
      periodKey: z.string().optional()
        .describe("Filter by period key: 'ALL' | 'YYYY-MM' | 'YYYY-Www' (e.g. '2026-W14')"),
      userId: z.string().optional()
        .describe("Filter by a specific user's Keycloak ID"),
      limit: z.number().int().min(1).max(100).default(20)
        .describe("Max results (default 20, max 100)"),
    },
    async ({ metric, periodKey, userId, limit }) => {
      try {
        const col = await collection(DB.leaderboard, "leaderboard_scores");
        const query = {};
        if (metric) query.metric = metric;
        if (periodKey) query.periodKey = periodKey;
        if (userId) query.userId = userId;

        const entries = await col
          .find(query)
          .sort({ score: -1 })
          .limit(limit)
          .toArray();

        if (entries.length === 0) {
          return { content: [{ type: "text", text: "No leaderboard entries found." }] };
        }

        const text =
          header(`Leaderboard Scores (${entries.length} of max ${limit})`) +
          entries
            .map(
              (e, i) =>
                `[${i + 1}] userId=${e.userId}\n    metric=${e.metric}  period=${e.periodKey}  score=${e.score}  activities=${e.activityCount}\n    updated=${e.updatedAt ?? "—"}`
            )
            .join("\n\n");

        return { content: [{ type: "text", text }] };
      } catch (err) {
        return { content: [{ type: "text", text: `MongoDB error: ${err.message}` }] };
      }
    }
  );
}

  // ── 10. list_wearable_events ─────────────────────────────────────────────────
  server.tool(
    "list_wearable_events",
    "List raw wearable device events from MongoDB (fitnesswearable DB). Filter by userId, eventType, deviceType, or processed status. Useful for debugging device integrations and checking pending sync queue.",
    {
      userId:      z.string().optional().describe("Filter by Keycloak user ID"),
      eventType:   z.enum(["HEART_RATE","STEPS","GPS_TRACK","SLEEP","CALORIES","WORKOUT_START","WORKOUT_END"]).optional(),
      deviceType:  z.enum(["FITBIT","APPLE_WATCH","GARMIN","WHOOP","POLAR","SAMSUNG","GENERIC"]).optional(),
      processed:   z.boolean().optional().describe("Filter by processed flag (false = pending sync)"),
      limit:       z.number().int().min(1).max(100).default(20),
    },
    async ({ userId, eventType, deviceType, processed, limit }) => {
      try {
        const col = await collection(DB.wearable, "wearable_events");
        const query = {};
        if (userId)     query.userId     = userId;
        if (eventType)  query.eventType  = eventType;
        if (deviceType) query.deviceType = deviceType;
        if (processed !== undefined) query.processed = processed;

        const events = await col
          .find(query)
          .sort({ timestamp: -1 })
          .limit(limit)
          .toArray();

        if (events.length === 0) {
          return { content: [{ type: "text", text: "No wearable events found." }] };
        }

        const text =
          header(`Wearable Events (${events.length} of max ${limit})`) +
          events
            .map(
              (e, i) =>
                `[${i + 1}] id=${e._id}\n    userId=${e.userId}  device=${e.deviceType}  type=${e.eventType}\n    timestamp=${e.timestamp ?? "—"}  processed=${e.processed}\n    hr=${e.heartRate ?? "—"}bpm  steps=${e.steps ?? "—"}  cal=${e.caloriesBurned ?? "—"}  dist=${e.distanceKm ?? "—"}km\n    receivedAt=${e.receivedAt ?? "—"}`
            )
            .join("\n\n");

        return { content: [{ type: "text", text }] };
      } catch (err) {
        return { content: [{ type: "text", text: `MongoDB error: ${err.message}` }] };
      }
    }
  );
}

function buildProgressBar(pct) {
  const filled = Math.round((pct / 100) * 20);
  return "[" + "█".repeat(filled) + "░".repeat(20 - filled) + "]";
}
