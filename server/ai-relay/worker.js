// LifeOS AI relay: бесплатный ИИ для всех пользователей приложения без ключа у каждого.
// Принимает запрос в формате Anthropic Messages (его шлёт приложение) и переводит в формат OpenAI
// (Groq, OpenRouter, Google Gemini и другие). Ключ провайдера хранится здесь, в секрете, а не в приложении.
//
// Настройка (Cloudflare → Workers → Settings → Variables and Secrets):
//   UPSTREAM_KEY   (секрет, обязательно) ключ провайдера, например с console.groq.com
//   UPSTREAM_URL   адрес чата провайдера, по умолчанию Groq
//   MODEL          модель, по умолчанию openai/gpt-oss-120b
//   UPSTREAM_KIND  "openai" (по умолчанию) или "anthropic" (если это платный ключ Claude)
//   APP_TOKEN      необязательно: общий пароль приложения (мягкая защита от случайных посетителей)
//   HOURLY_LIMIT   запросов в час с одного устройства или аккаунта, по умолчанию 40
//   FIREBASE_PROJECT_ID  необязательно: идентификатор проекта Firebase. Тогда ИИ получают только вошедшие в аккаунт
//                  пользователи, а лимит считается на каждый аккаунт (проверяется подпись входа Google)
//   USER_DAILY_LIMIT     запросов в сутки на один аккаунт, по умолчанию 25
//   LIMITS         необязательно: хранилище KV для суточных счётчиков (иначе счёт в памяти и он мягкий)

const DEFAULT_URL = "https://api.groq.com/openai/v1/chat/completions";
const DEFAULT_MODEL = "openai/gpt-oss-120b";
const MAX_BODY = 120_000;
const MAX_TOKENS = 1500;

const hits = new Map(); // мягкий лимит в памяти: у каждого экземпляра Worker свой счётчик

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "GET") {
      return text(`LifeOS AI relay работает. Ключ провайдера: ${env.UPSTREAM_KEY ? "задан" : "НЕ задан"}.`);
    }
    if (request.method !== "POST" || !url.pathname.endsWith("/v1/messages")) return json({ error: "not_found" }, 404);
    if (env.APP_TOKEN && request.headers.get("x-app-token") !== env.APP_TOKEN) return json({ error: "forbidden" }, 403);
    if (!env.UPSTREAM_KEY) return json({ error: "relay_not_configured" }, 503);

    let device = (request.headers.get("x-device-id") || "").slice(0, 64) || "anon";
    const ip = request.headers.get("cf-connecting-ip") || "";
    const limit = Number(env.HOURLY_LIMIT) || 40;

    if (env.FIREBASE_PROJECT_ID) {
      const auth = request.headers.get("authorization") || "";
      const token = auth.startsWith("Bearer ") ? auth.slice(7).trim() : "";
      const claims = token ? await verifyFirebaseToken(token, env.FIREBASE_PROJECT_ID).catch(() => null) : null;
      if (!claims) return json({ error: "auth_required" }, 401);
      device = `u:${claims.sub}`;
      if (!(await dailyAllow(env, claims.sub, Number(env.USER_DAILY_LIMIT) || 25))) {
        return json({ error: "rate_limited" }, 429);
      }
    }
    if (!allow(`d:${device}`, limit) || (ip && !allow(`i:${ip}`, limit * 3))) {
      return json({ error: "rate_limited" }, 429);
    }

    const raw = await request.text();
    if (raw.length > MAX_BODY) return json({ error: "too_large" }, 413);
    let body;
    try { body = JSON.parse(raw); } catch { return json({ error: "bad_json" }, 400); }

    const kind = (env.UPSTREAM_KIND || "openai").toLowerCase();
    const model = env.MODEL || DEFAULT_MODEL;
    try {
      if (kind === "anthropic") return await viaAnthropic(body, env, model);
      return await viaOpenAI(body, env, model);
    } catch (e) {
      return json({ error: "upstream_unreachable" }, 502);
    }
  },
};

function allow(key, limit) {
  const now = Date.now();
  const hour = 3_600_000;
  const list = (hits.get(key) || []).filter((t) => now - t < hour);
  if (list.length >= limit) { hits.set(key, list); return false; }
  list.push(now);
  hits.set(key, list);
  if (hits.size > 5000) for (const k of hits.keys()) { hits.delete(k); if (hits.size < 2500) break; }
  return true;
}

// ---------- суточный лимит на аккаунт ----------

const daily = new Map();

async function dailyAllow(env, uid, limit) {
  const key = `u:${uid}:${new Date().toISOString().slice(0, 10)}`;
  if (env.LIMITS) {
    try {
      const n = Number(await env.LIMITS.get(key)) || 0;
      if (n >= limit) return false;
      await env.LIMITS.put(key, String(n + 1), { expirationTtl: 172_800 });
      return true;
    } catch { /* хранилище недоступно или кончился бесплатный лимит записей: считаем в памяти */ }
  }
  const n = daily.get(key) || 0;
  if (n >= limit) return false;
  daily.set(key, n + 1);
  if (daily.size > 5000) for (const k of daily.keys()) { daily.delete(k); if (daily.size < 2500) break; }
  return true;
}

// ---------- проверка входа Firebase (JWT, RS256) ----------

const JWKS_URL = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";
let jwksCache = { at: 0, keys: [] };

async function getJwks(force = false) {
  if (!force && Date.now() - jwksCache.at < 3_600_000 && jwksCache.keys.length) return jwksCache.keys;
  const res = await fetch(JWKS_URL);
  if (!res.ok) throw new Error("jwks");
  jwksCache = { at: Date.now(), keys: (await res.json()).keys || [] };
  return jwksCache.keys;
}

function b64url(str) {
  const bin = atob(str.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(str.length / 4) * 4, "="));
  return Uint8Array.from(bin, (c) => c.charCodeAt(0));
}

/** Возвращает данные токена, если подпись и сроки верны и токен выдан именно этому проекту; иначе null. */
export async function verifyFirebaseToken(token, projectId, now = Date.now()) {
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  let header, claims;
  try {
    header = JSON.parse(new TextDecoder().decode(b64url(parts[0])));
    claims = JSON.parse(new TextDecoder().decode(b64url(parts[1])));
  } catch { return null; }
  if (header.alg !== "RS256" || !header.kid) return null;

  const sec = Math.floor(now / 1000);
  if (claims.aud !== projectId || claims.iss !== `https://securetoken.google.com/${projectId}`) return null;
  if (typeof claims.sub !== "string" || !claims.sub || claims.sub.length > 128) return null;
  if (!(claims.exp > sec) || !(claims.iat <= sec + 300)) return null;

  let jwk = (await getJwks()).find((k) => k.kid === header.kid);
  if (!jwk) jwk = (await getJwks(true)).find((k) => k.kid === header.kid); // Google мог сменить ключи
  if (!jwk) return null;
  const key = await crypto.subtle.importKey("jwk", jwk, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
  const ok = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, b64url(parts[2]), new TextEncoder().encode(`${parts[0]}.${parts[1]}`));
  return ok ? claims : null;
}

async function viaAnthropic(body, env, model) {
  const res = await fetch(env.UPSTREAM_URL || "https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: { "content-type": "application/json", "x-api-key": env.UPSTREAM_KEY, "anthropic-version": "2023-06-01" },
    body: JSON.stringify({ ...body, model, max_tokens: Math.min(body.max_tokens || 1024, MAX_TOKENS) }),
  });
  return new Response(await res.text(), { status: res.status, headers: { "content-type": "application/json" } });
}

async function viaOpenAI(body, env, model) {
  const payload = toOpenAI(body, model);
  const res = await fetch(env.UPSTREAM_URL || DEFAULT_URL, {
    method: "POST",
    headers: { "content-type": "application/json", authorization: `Bearer ${env.UPSTREAM_KEY}` },
    body: JSON.stringify(payload),
  });
  const data = await res.json().catch(() => null);
  if (!res.ok || !data) return json({ error: "upstream_error", status: res.status }, res.status === 429 ? 429 : 502);
  return json(fromOpenAI(data));
}

// ---------- перевод Anthropic → OpenAI ----------

export function toOpenAI(body, model) {
  const messages = [];
  if (body.system) messages.push({ role: "system", content: String(body.system) });
  for (const m of body.messages || []) {
    if (typeof m.content === "string") { messages.push({ role: m.role, content: m.content }); continue; }
    const blocks = Array.isArray(m.content) ? m.content : [];
    if (m.role === "assistant") {
      const textParts = blocks.filter((b) => b.type === "text").map((b) => b.text).join("");
      const calls = blocks.filter((b) => b.type === "tool_use").map((b) => ({
        id: b.id, type: "function", function: { name: b.name, arguments: JSON.stringify(b.input || {}) },
      }));
      const msg = { role: "assistant", content: textParts || null };
      if (calls.length) msg.tool_calls = calls;
      messages.push(msg);
    } else {
      for (const b of blocks.filter((b) => b.type === "tool_result")) {
        const c = Array.isArray(b.content) ? b.content.map((x) => x.text || "").join("") : String(b.content ?? "");
        messages.push({ role: "tool", tool_call_id: b.tool_use_id, content: c });
      }
      const textParts = blocks.filter((b) => b.type === "text").map((b) => b.text).join("");
      if (textParts) messages.push({ role: "user", content: textParts });
    }
  }
  const out = { model, messages, max_tokens: Math.min(body.max_tokens || 1024, MAX_TOKENS), temperature: 0.4 };
  if (Array.isArray(body.tools) && body.tools.length) {
    out.tools = body.tools.map((t) => ({
      type: "function", function: { name: t.name, description: t.description || "", parameters: t.input_schema || { type: "object", properties: {} } },
    }));
    out.tool_choice = "auto";
  }
  if (/gpt-oss/i.test(model)) out.reasoning_effort = "low"; // быстрее ответ, меньше расход лимита
  return out;
}

// ---------- перевод OpenAI → Anthropic ----------

export function fromOpenAI(data) {
  const msg = data?.choices?.[0]?.message || {};
  const content = [];
  const t = typeof msg.content === "string" ? msg.content : "";
  if (t.trim()) content.push({ type: "text", text: t });
  let n = 0;
  for (const call of msg.tool_calls || []) {
    let input = {};
    try { input = JSON.parse(call.function?.arguments || "{}"); } catch { input = {}; }
    content.push({ type: "tool_use", id: call.id || `call_${Date.now()}_${n++}`, name: call.function?.name, input });
  }
  const hasTools = content.some((b) => b.type === "tool_use");
  return { id: data.id || "relay", type: "message", role: "assistant", content, stop_reason: hasTools ? "tool_use" : "end_turn" };
}

function json(obj, status = 200) {
  return new Response(JSON.stringify(obj), { status, headers: { "content-type": "application/json" } });
}
function text(s) {
  return new Response(s, { headers: { "content-type": "text/plain; charset=utf-8" } });
}
