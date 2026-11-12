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
//   HOURLY_LIMIT   запросов в час с одного устройства, по умолчанию 40

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

    const device = (request.headers.get("x-device-id") || "").slice(0, 64) || "anon";
    const ip = request.headers.get("cf-connecting-ip") || "";
    const limit = Number(env.HOURLY_LIMIT) || 40;
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
