import { z } from "zod";

// ── Service registry ──────────────────────────────────────────────────────────
// All URLs are read from env vars with sensible localhost defaults
const SERVICES = {
  gateway: {
    label: "API Gateway",
    url: process.env.GATEWAY_URL || "http://localhost:8080",
    healthPath: "/actuator/health",
    metricsPath: "/actuator/metrics",
  },
  userservice: {
    label: "User Service",
    url: process.env.USERSERVICE_URL || "http://localhost:8081",
    healthPath: "/actuator/health",
    metricsPath: "/actuator/metrics",
  },
  activityservice: {
    label: "Activity Service",
    url: process.env.ACTIVITYSERVICE_URL || "http://localhost:8082",
    healthPath: "/actuator/health",
    metricsPath: "/actuator/metrics",
  },
  aiservice: {
    label: "AI Service",
    url: process.env.AISERVICE_URL || "http://localhost:8083",
    healthPath: "/actuator/health",
    metricsPath: "/actuator/metrics",
  },
  eureka: {
    label: "Eureka",
    url: process.env.EUREKA_URL || "http://localhost:8761",
    healthPath: "/actuator/health",
    metricsPath: "/actuator/metrics",
  },
  configserver: {
    label: "Config Server",
    url: process.env.CONFIGSERVER_URL || "http://localhost:8888",
    healthPath: "/actuator/health",
    metricsPath: "/actuator/metrics",
  },
  zipkin: {
    label: "Zipkin",
    url: process.env.ZIPKIN_URL || "http://localhost:9411",
    healthPath: "/health",    // Zipkin uses its own /health endpoint
    metricsPath: null,        // No actuator
  },
};

const SERVICE_KEYS = Object.keys(SERVICES);

async function fetchJson(url) {
  const res = await fetch(url, {
    signal: AbortSignal.timeout(4000),
    headers: { Accept: "application/json" },
  });
  return { status: res.status, body: await res.json() };
}

function statusIcon(status) {
  if (status === "UP") return "✅";
  if (status === "DOWN") return "❌";
  if (status === "OUT_OF_SERVICE") return "⚠️";
  return "❓";
}

function formatHealthBody(label, url, data, httpStatus) {
  const overallStatus = data?.status ?? (httpStatus < 400 ? "UP" : "DOWN");
  const icon = statusIcon(overallStatus);
  const lines = [`${icon}  ${label}  (${url})`];
  lines.push(`   Status: ${overallStatus}`);

  if (data?.components) {
    for (const [comp, detail] of Object.entries(data.components)) {
      const s = detail?.status ?? "UNKNOWN";
      lines.push(`   ${statusIcon(s)} ${comp}: ${s}`);
    }
  }
  return lines.join("\n");
}

// ── Tool registrations ────────────────────────────────────────────────────────

export function registerHealthTools(server) {

  // ── 1. check_all_health ─────────────────────────────────────────────────────
  server.tool(
    "check_all_health",
    "Hit /actuator/health on every microservice (Gateway, UserService, ActivityService, AIService, ConfigServer, Eureka) and the Zipkin tracing UI in parallel. Returns a status summary showing which services are UP, DOWN, or unreachable.",
    {},
    async () => {
      const results = await Promise.allSettled(
        SERVICE_KEYS.map(async (key) => {
          const svc = SERVICES[key];
          const url = svc.url + svc.healthPath;
          try {
            const { status, body } = await fetchJson(url);
            return { key, svc, ok: true, httpStatus: status, body };
          } catch (err) {
            return { key, svc, ok: false, error: err.message };
          }
        })
      );

      const header = ["─".repeat(60), "  Service Health Overview", "─".repeat(60), ""].join("\n");

      const lines = results.map((r) => {
        const { key, svc, ok, httpStatus, body, error } = r.value ?? r.reason ?? {};
        if (!ok) {
          return `❌  ${svc.label}  (${svc.url})\n   UNREACHABLE — ${error}`;
        }
        return formatHealthBody(svc.label, svc.url, body, httpStatus);
      });

      const upCount = results.filter(
        (r) => r.value?.ok && (r.value?.body?.status === "UP" || r.value?.httpStatus < 400)
      ).length;

      const footer = `\n${"─".repeat(60)}\n  ${upCount}/${SERVICE_KEYS.length} services healthy`;

      return {
        content: [{ type: "text", text: header + lines.join("\n\n") + footer }],
      };
    }
  );

  // ── 2. get_service_metrics ──────────────────────────────────────────────────
  server.tool(
    "get_service_metrics",
    "Fetch a specific Micrometer metric from one microservice's /actuator/metrics endpoint. Use this to inspect JVM memory, HTTP request counts, active DB connections, cache hit rates, and more.",
    {
      service: z
        .enum(SERVICE_KEYS)
        .describe("Which service to query (gateway | userservice | activityservice | aiservice | eureka | configserver)"),
      metric: z
        .string()
        .default("jvm.memory.used")
        .describe(
          "Micrometer metric name. Examples: jvm.memory.used, http.server.requests, jvm.threads.live, process.uptime, cache.gets, spring.rabbitmq.listener.seconds"
        ),
      tag: z
        .string()
        .optional()
        .describe("Optional tag filter e.g. 'area:heap' or 'uri:/api/activities'"),
    },
    async ({ service, metric, tag }) => {
      const svc = SERVICES[service];
      if (!svc.metricsPath) {
        return {
          content: [{ type: "text", text: `${svc.label} does not expose an Actuator metrics endpoint.` }],
        };
      }

      try {
        // First fetch the list of available metrics if we need it
        const encodedMetric = encodeURIComponent(metric);
        const tagParam = tag ? `?tag=${encodeURIComponent(tag)}` : "";
        const url = `${svc.url}${svc.metricsPath}/${encodedMetric}${tagParam}`;

        const { status, body } = await fetchJson(url).catch(async (e) => {
          // If specific metric not found, list available metrics
          if (e.message.includes("404") || e.message.includes("Not Found")) {
            const list = await fetchJson(`${svc.url}${svc.metricsPath}`);
            return { status: 404, body: list.body };
          }
          throw e;
        });

        if (status === 404 || body?.names) {
          const names = body?.names ?? [];
          return {
            content: [
              {
                type: "text",
                text:
                  `Metric '${metric}' not found on ${svc.label}.\n\n` +
                  `Available metrics (${names.length}):\n` +
                  names.map((n) => `  • ${n}`).join("\n"),
              },
            ],
          };
        }

        const measurements = (body.measurements ?? [])
          .map((m) => `  ${m.statistic}: ${m.value}`)
          .join("\n");

        const availableTags = (body.availableTags ?? [])
          .map((t) => `  ${t.tag}: [${t.values.slice(0, 5).join(", ")}${t.values.length > 5 ? "..." : ""}]`)
          .join("\n");

        const text =
          `─`.repeat(60) + `\n  ${svc.label} — ${body.name}\n` + `─`.repeat(60) + "\n" +
          `Description : ${body.description ?? "—"}\n` +
          `Unit        : ${body.baseUnit ?? "—"}\n\n` +
          `Measurements:\n${measurements || "  (none)"}\n\n` +
          (availableTags ? `Available tags (for filtering):\n${availableTags}` : "");

        return { content: [{ type: "text", text }] };
      } catch (err) {
        return {
          content: [
            {
              type: "text",
              text: `Failed to fetch metric from ${svc.label} (${svc.url}${svc.metricsPath}):\n${err.message}\n\nIs the service running and Actuator exposed?`,
            },
          ],
        };
      }
    }
  );

  // ── 3. get_eureka_registry ───────────────────────────────────────────────────
  server.tool(
    "get_eureka_registry",
    "Fetch the Eureka service registry to see all registered instances, their IP addresses, ports, and health status. Useful for debugging service discovery issues.",
    {},
    async () => {
      const eurekaUrl = (process.env.EUREKA_URL || "http://localhost:8761") + "/eureka/apps";
      try {
        const res = await fetch(eurekaUrl, {
          headers: { Accept: "application/json" },
          signal: AbortSignal.timeout(5000),
        });

        if (!res.ok) {
          return {
            content: [{ type: "text", text: `Eureka returned HTTP ${res.status}. Is it running at ${eurekaUrl}?` }],
          };
        }

        const data = await res.json();
        const apps = data?.applications?.application ?? [];

        if (apps.length === 0) {
          return {
            content: [{ type: "text", text: "Eureka registry is empty — no services have registered yet." }],
          };
        }

        const header =
          "─".repeat(60) + `\n  Eureka Registry (${apps.length} applications)\n` + "─".repeat(60);

        const body = apps
          .map((app) => {
            const instances = Array.isArray(app.instance) ? app.instance : [app.instance];
            const instLines = instances.map((inst) => {
              const status = inst.status ?? "UNKNOWN";
              const icon = status === "UP" ? "✅" : "❌";
              return (
                `    ${icon} ${inst.instanceId ?? inst.hostName}\n` +
                `       host=${inst.ipAddr}  port=${inst.port?.$ ?? "—"}\n` +
                `       status=${status}  healthUrl=${inst.healthCheckUrl ?? "—"}`
              );
            });
            return `\n▶ ${app.name} (${instances.length} instance${instances.length !== 1 ? "s" : ""})\n${instLines.join("\n")}`;
          })
          .join("\n");

        return { content: [{ type: "text", text: header + body }] };
      } catch (err) {
        return {
          content: [
            {
              type: "text",
              text: `Could not reach Eureka at ${eurekaUrl}.\n\nError: ${err.message}`,
            },
          ],
        };
      }
    }
  );
}
