import { z } from "zod";

const HOST = process.env.RABBITMQ_HOST || "localhost";
const PORT = process.env.RABBITMQ_MGMT_PORT || "15672";
const USER = process.env.RABBITMQ_USER || "guest";
const PASS = process.env.RABBITMQ_PASS || "guest";

const BASE = `http://${HOST}:${PORT}/api`;
const VHOST = "%2F"; // URL-encoded "/"

// Basic-auth header shared by all management API calls
const AUTH = "Basic " + Buffer.from(`${USER}:${PASS}`).toString("base64");

// Known queues in this project
const QUEUES = {
  main: "activity.queue",
  dlq: "activity.queue.dlq",
};

async function mgmtGet(path) {
  const res = await fetch(`${BASE}${path}`, {
    headers: { Authorization: AUTH, Accept: "application/json" },
    signal: AbortSignal.timeout(5000),
  });
  if (!res.ok) {
    throw new Error(`RabbitMQ management API ${path} returned HTTP ${res.status}: ${await res.text()}`);
  }
  return res.json();
}

async function mgmtPost(path, body) {
  const res = await fetch(`${BASE}${path}`, {
    method: "POST",
    headers: {
      Authorization: AUTH,
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(5000),
  });
  if (!res.ok) {
    throw new Error(`RabbitMQ management API ${path} returned HTTP ${res.status}: ${await res.text()}`);
  }
  return res.json();
}

async function mgmtDelete(path) {
  const res = await fetch(`${BASE}${path}`, {
    method: "DELETE",
    headers: { Authorization: AUTH },
    signal: AbortSignal.timeout(5000),
  });
  if (!res.ok) {
    throw new Error(`RabbitMQ management API ${path} returned HTTP ${res.status}: ${await res.text()}`);
  }
}

function fmtQueue(q) {
  const ready = q.messages_ready ?? 0;
  const unacked = q.messages_unacknowledged ?? 0;
  const total = q.messages ?? 0;
  const consumers = q.consumers ?? 0;
  const inRate = q.message_stats?.publish_details?.rate?.toFixed(2) ?? "0.00";
  const outRate = q.message_stats?.deliver_get_details?.rate?.toFixed(2) ?? "0.00";
  const state = q.state ?? "unknown";

  return (
    `  Name       : ${q.name}\n` +
    `  State      : ${state}\n` +
    `  Consumers  : ${consumers}\n` +
    `  Messages   : ${total} total  (${ready} ready, ${unacked} unacked)\n` +
    `  Throughput : in=${inRate} msg/s  out=${outRate} msg/s\n` +
    `  Durable    : ${q.durable}\n` +
    `  DLX args   : ${JSON.stringify(q.arguments ?? {})}`
  );
}

// ── Tool registrations ────────────────────────────────────────────────────────

export function registerRabbitMqTools(server) {

  // ── 1. get_queue_stats ──────────────────────────────────────────────────────
  server.tool(
    "get_queue_stats",
    "Show real-time depth and throughput stats for the activity.queue and activity.queue.dlq via the RabbitMQ management API. Useful for diagnosing message backlogs, consumer lag, or DLQ growth.",
    {},
    async () => {
      try {
        const [mainQ, dlqQ] = await Promise.all([
          mgmtGet(`/queues/${VHOST}/${encodeURIComponent(QUEUES.main)}`).catch(
            (e) => ({ _error: e.message })
          ),
          mgmtGet(`/queues/${VHOST}/${encodeURIComponent(QUEUES.dlq)}`).catch(
            (e) => ({ _error: e.message })
          ),
        ]);

        const lines = ["─".repeat(60), "  RabbitMQ Queue Stats", "─".repeat(60), ""];

        lines.push("▶ Main Queue (activity.queue)");
        if (mainQ._error) {
          lines.push(`  ERROR: ${mainQ._error}`);
        } else {
          lines.push(fmtQueue(mainQ));
        }

        lines.push("");
        lines.push("▶ Dead Letter Queue (activity.queue.dlq)");
        if (dlqQ._error) {
          lines.push(`  Queue does not exist yet (no failed messages) OR: ${dlqQ._error}`);
        } else {
          lines.push(fmtQueue(dlqQ));
          if ((dlqQ.messages ?? 0) > 0) {
            lines.push(`\n  ⚠  DLQ has ${dlqQ.messages} message(s) — use get_dlq_messages to inspect them`);
          }
        }

        return { content: [{ type: "text", text: lines.join("\n") }] };
      } catch (err) {
        return {
          content: [
            {
              type: "text",
              text: `Failed to reach RabbitMQ management API at ${BASE}.\n\nIs RabbitMQ running? (port ${PORT})\nCredentials: ${USER}/**\n\nError: ${err.message}`,
            },
          ],
        };
      }
    }
  );

  // ── 2. get_dlq_messages ─────────────────────────────────────────────────────
  server.tool(
    "get_dlq_messages",
    "Peek at messages in the Dead Letter Queue (activity.queue.dlq) without removing them. Shows the original activity payload and the reason the message was dead-lettered (e.g. rejected, expired, or max-retries exceeded).",
    {
      count: z.number().int().min(1).max(50).default(5).describe("Number of messages to preview (default 5, max 50)"),
    },
    async ({ count }) => {
      try {
        // ackmode "ack_requeue_true" = peek without consuming
        const messages = await mgmtPost(
          `/queues/${VHOST}/${encodeURIComponent(QUEUES.dlq)}/get`,
          { count, ackmode: "ack_requeue_true", encoding: "auto", truncate: 50000 }
        );

        if (!messages || messages.length === 0) {
          return { content: [{ type: "text", text: "Dead Letter Queue is empty — no failed messages." }] };
        }

        const header =
          "─".repeat(60) +
          `\n  DLQ Messages (${messages.length} of ${count} requested)\n` +
          "─".repeat(60);

        const body = messages
          .map((m, i) => {
            const dlReason = m.properties?.headers?.["x-death"]?.[0]?.reason ?? "unknown";
            const dlQueue = m.properties?.headers?.["x-death"]?.[0]?.queue ?? "unknown";
            const dlCount = m.properties?.headers?.["x-death"]?.[0]?.count ?? 1;
            let payload;
            try {
              payload = JSON.stringify(JSON.parse(m.payload), null, 2);
            } catch {
              payload = m.payload;
            }
            return (
              `\n[${i + 1}] routing_key=${m.routing_key}  redelivered=${m.redelivered}\n` +
              `    DL reason : ${dlReason}  (${dlCount}x from queue '${dlQueue}')\n` +
              `    Payload   :\n${payload
                .split("\n")
                .map((l) => "      " + l)
                .join("\n")}`
            );
          })
          .join("\n");

        return { content: [{ type: "text", text: header + body }] };
      } catch (err) {
        if (err.message.includes("404")) {
          return {
            content: [
              {
                type: "text",
                text: "Dead Letter Queue does not exist yet.\n\nThis means no messages have ever been dead-lettered. The DLQ is created automatically when the first rejected/expired message arrives.",
              },
            ],
          };
        }
        return { content: [{ type: "text", text: `RabbitMQ error: ${err.message}` }] };
      }
    }
  );

  // ── 3. purge_dlq ────────────────────────────────────────────────────────────
  server.tool(
    "purge_dlq",
    "Permanently delete ALL messages from the Dead Letter Queue. IRREVERSIBLE. You must pass confirm=true explicitly — this prevents accidental invocation.",
    {
      confirm: z
        .literal(true)
        .describe("Must be exactly true to proceed. Any other value is rejected."),
    },
    async ({ confirm }) => {
      if (confirm !== true) {
        return {
          content: [
            {
              type: "text",
              text: 'Purge cancelled. Pass confirm=true to permanently delete all DLQ messages.',
            },
          ],
        };
      }
      try {
        await mgmtDelete(`/queues/${VHOST}/${encodeURIComponent(QUEUES.dlq)}/contents`);
        return {
          content: [
            {
              type: "text",
              text: `✓ DLQ purged. All messages in '${QUEUES.dlq}' have been permanently deleted.`,
            },
          ],
        };
      } catch (err) {
        if (err.message.includes("404")) {
          return { content: [{ type: "text", text: "DLQ does not exist — nothing to purge." }] };
        }
        return { content: [{ type: "text", text: `Failed to purge DLQ: ${err.message}` }] };
      }
    }
  );
}
