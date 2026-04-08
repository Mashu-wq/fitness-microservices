import { z } from "zod";

/**
 * Decodes a JWT without verifying its signature.
 * Safe for dev-time inspection — never use this for auth decisions.
 */
function decodeJwt(token) {
  const raw = token.startsWith("Bearer ") ? token.slice(7) : token;
  const parts = raw.split(".");
  if (parts.length !== 3) {
    throw new Error("Not a valid JWT — expected exactly 3 dot-separated parts (header.payload.signature)");
  }

  const decode = (b64url) => {
    // JWT uses base64url encoding (- instead of +, _ instead of /)
    const b64 = b64url.replace(/-/g, "+").replace(/_/g, "/");
    return JSON.parse(Buffer.from(b64, "base64").toString("utf-8"));
  };

  return {
    header: decode(parts[0]),
    payload: decode(parts[1]),
    signaturePresent: parts[2].length > 0,
  };
}

function formatClaims(payload) {
  const now = Math.floor(Date.now() / 1000);

  const exp = payload.exp ? new Date(payload.exp * 1000).toISOString() : null;
  const iat = payload.iat ? new Date(payload.iat * 1000).toISOString() : null;
  const nbf = payload.nbf ? new Date(payload.nbf * 1000).toISOString() : null;

  const isExpired = payload.exp ? now > payload.exp : null;
  const expiresInSec = payload.exp ? payload.exp - now : null;

  const lines = [];

  // Identity claims (what this project uses)
  lines.push("── Identity ──────────────────────────────────────────────");
  lines.push(`  sub           : ${payload.sub ?? "—"}         ← this is the X-User-ID injected by KeycloakUserSyncFilter`);
  lines.push(`  email         : ${payload.email ?? "—"}`);
  lines.push(`  given_name    : ${payload.given_name ?? "—"}`);
  lines.push(`  family_name   : ${payload.family_name ?? "—"}`);
  lines.push(`  preferred_usr : ${payload.preferred_username ?? "—"}`);

  // Token lifecycle
  lines.push("\n── Token Lifecycle ───────────────────────────────────────");
  lines.push(`  iat (issued)  : ${iat ?? "—"}`);
  lines.push(`  exp (expires) : ${exp ?? "—"}`);
  if (nbf) lines.push(`  nbf (not bef) : ${nbf}`);

  if (isExpired === true) {
    lines.push(`  ⚠  TOKEN IS EXPIRED (expired ${Math.abs(expiresInSec)}s ago)`);
  } else if (isExpired === false) {
    lines.push(`  ✅ Token valid for ${expiresInSec}s more (~${(expiresInSec / 60).toFixed(1)} min)`);
  }

  // Issuer / audience
  lines.push("\n── Issuer & Audience ─────────────────────────────────────");
  lines.push(`  iss           : ${payload.iss ?? "—"}`);
  lines.push(`  aud           : ${Array.isArray(payload.aud) ? payload.aud.join(", ") : payload.aud ?? "—"}`);
  lines.push(`  azp           : ${payload.azp ?? "—"}`);

  // Keycloak roles
  const realmRoles = payload.realm_access?.roles ?? [];
  const resourceRoles = payload.resource_access
    ? Object.entries(payload.resource_access)
        .map(([client, v]) => `${client}: [${(v.roles ?? []).join(", ")}]`)
        .join("; ")
    : null;

  lines.push("\n── Roles ─────────────────────────────────────────────────");
  lines.push(`  realm_access  : [${realmRoles.join(", ") || "none"}]`);
  if (resourceRoles) lines.push(`  resource_accs : ${resourceRoles}`);

  // Scope
  lines.push("\n── Scope ─────────────────────────────────────────────────");
  lines.push(`  scope         : ${payload.scope ?? "—"}`);

  // All remaining claims (anything not already shown above)
  const shownKeys = new Set([
    "sub", "email", "given_name", "family_name", "preferred_username",
    "iat", "exp", "nbf", "iss", "aud", "azp", "realm_access", "resource_access", "scope",
  ]);
  const extra = Object.entries(payload).filter(([k]) => !shownKeys.has(k));
  if (extra.length > 0) {
    lines.push("\n── Other Claims ──────────────────────────────────────────");
    for (const [k, v] of extra) {
      lines.push(`  ${k.padEnd(14)}: ${JSON.stringify(v)}`);
    }
  }

  return lines.join("\n");
}

// ── Tool registrations ────────────────────────────────────────────────────────

export function registerJwtTools(server) {

  // ── 1. decode_jwt ────────────────────────────────────────────────────────────
  server.tool(
    "decode_jwt",
    "Decode and inspect a Keycloak JWT (Bearer token) without verifying its signature. Shows sub (= X-User-ID), email, roles, expiry, issuer, and all other claims. Use this to debug auth issues, verify what claims the token carries, and cross-reference the sub with MongoDB user records.",
    {
      token: z
        .string()
        .describe("The raw JWT string. May include or omit the 'Bearer ' prefix."),
    },
    async ({ token }) => {
      try {
        const { header, payload, signaturePresent } = decodeJwt(token);

        const text =
          "─".repeat(60) + "\n  JWT Inspector\n" + "─".repeat(60) + "\n\n" +
          `Algorithm  : ${header.alg ?? "—"}   Type: ${header.typ ?? "—"}   kid: ${header.kid ?? "—"}\n` +
          `Signature  : ${signaturePresent ? "present (not verified — this tool only decodes)" : "missing"}\n\n` +
          formatClaims(payload) +
          "\n\n── Quick Cross-Reference ──────────────────────────────────\n" +
          `  To find this user in MongoDB, run:\n` +
          `    list_users with keycloakId="${payload.sub ?? "<sub not found>"}"\n` +
          `  To list their activities:\n` +
          `    list_activities with userId="${payload.sub ?? "<sub not found>"}"`;

        return { content: [{ type: "text", text }] };
      } catch (err) {
        return {
          content: [
            {
              type: "text",
              text: `Failed to decode JWT: ${err.message}\n\nMake sure you're passing a complete JWT string (three base64url parts separated by dots), optionally prefixed with 'Bearer '.`,
            },
          ],
        };
      }
    }
  );
}
