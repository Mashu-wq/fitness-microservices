/**
 * Fitness MCP Server
 *
 * A Model Context Protocol server for the Spring AI Fitness Microservices project.
 * Provides Claude Code with 13 tools across 4 categories:
 *
 *   MongoDB (6 tools)
 *     list_users              — browse fitnessuser DB
 *     get_user                — lookup user by id or keycloakId
 *     list_activities         — browse fitnessactivity DB with filters
 *     get_activity            — full activity doc + recommendation status
 *     list_recommendations    — browse fitnessrecommendation DB
 *     get_recommendation      — full AI recommendation for an activity
 *
 *   RabbitMQ (3 tools)
 *     get_queue_stats         — depth + throughput of main queue & DLQ
 *     get_dlq_messages        — peek at failed messages in the DLQ
 *     purge_dlq               — permanently delete all DLQ messages
 *
 *   Service Health (3 tools)
 *     check_all_health        — /actuator/health for every service
 *     get_service_metrics     — any Micrometer metric from any service
 *     get_eureka_registry     — list all registered Eureka instances
 *
 *   JWT (1 tool)
 *     decode_jwt              — decode Keycloak token, show all claims
 *
 * Configuration: set env vars in .claude/settings.json or copy .env.example → .env
 */

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";

import { registerMongoTools } from "./tools/mongodb-tools.js";
import { registerRabbitMqTools } from "./tools/rabbitmq-tools.js";
import { registerHealthTools } from "./tools/health-tools.js";
import { registerJwtTools } from "./tools/jwt-tools.js";

const server = new McpServer({
  name: "fitness-mcp",
  version: "1.0.0",
});

// Register all tool categories
registerMongoTools(server);
registerRabbitMqTools(server);
registerHealthTools(server);
registerJwtTools(server);

// Connect via stdio — the transport Claude Code uses for MCP
const transport = new StdioServerTransport();
await server.connect(transport);
