import worker, { toOpenAI, fromOpenAI } from "./worker.js";
import assert from "node:assert/strict";

// 1. перевод запроса с вызовом инструмента и результатом
const req = {
  system: "Ты помощник", max_tokens: 1024,
  tools: [{ name: "set_alarm", description: "будильник", input_schema: { type: "object", properties: { hour: { type: "integer" } } } }],
  messages: [
    { role: "user", content: "разбуди в 7" },
    { role: "assistant", content: [{ type: "text", text: "ставлю" }, { type: "tool_use", id: "t1", name: "set_alarm", input: { hour: 7 } }] },
    { role: "user", content: [{ type: "tool_result", tool_use_id: "t1", content: "Готово" }] },
  ],
};
const o = toOpenAI(req, "openai/gpt-oss-120b");
assert.equal(o.messages[0].role, "system");
assert.equal(o.messages[2].tool_calls[0].function.arguments, '{"hour":7}');
assert.deepEqual(o.messages[3], { role: "tool", tool_call_id: "t1", content: "Готово" });
assert.equal(o.tools[0].function.parameters.properties.hour.type, "integer");
assert.equal(o.reasoning_effort, "low");

// 2. перевод ответа
const a = fromOpenAI({ choices: [{ message: { content: null, tool_calls: [{ id: "c9", function: { name: "calculate", arguments: '{"expression":"2+2"}' } }] } }] });
assert.equal(a.stop_reason, "tool_use");
assert.deepEqual(a.content[0], { type: "tool_use", id: "c9", name: "calculate", input: { expression: "2+2" } });
const b = fromOpenAI({ choices: [{ message: { content: "Привет" } }] });
assert.equal(b.stop_reason, "end_turn");
assert.equal(b.content[0].text, "Привет");
const bad = fromOpenAI({ choices: [{ message: { tool_calls: [{ function: { name: "x", arguments: "{oops" } }] } }] });
assert.deepEqual(bad.content[0].input, {});

// 3. целиком через fetch с подменой провайдера
let seen;
globalThis.fetch = async (url, init) => {
  seen = { url, init };
  return new Response(JSON.stringify({ id: "x", choices: [{ message: { content: "Четыре" } }] }), { status: 200 });
};
const env = { UPSTREAM_KEY: "gsk_test", APP_TOKEN: "pw", HOURLY_LIMIT: "3" };
const call = (headers = {}) => worker.fetch(new Request("https://r.example/v1/messages", {
  method: "POST", headers: { "x-app-token": "pw", "x-device-id": "dev1", ...headers }, body: JSON.stringify({ system: "s", messages: [{ role: "user", content: "2+2?" }] }),
}), env);
let r = await call();
assert.equal(r.status, 200);
assert.equal((await r.json()).content[0].text, "Четыре");
assert.equal(seen.url, "https://api.groq.com/openai/v1/chat/completions");
assert.equal(seen.init.headers.authorization, "Bearer gsk_test");
assert.equal((await call({ "x-app-token": "wrong" })).status, 403);
await call(); await call();
assert.equal((await call()).status, 429); // 4-й запрос за час с одного устройства
assert.equal((await call({ "x-device-id": "dev2" })).status, 200); // другое устройство не затронуто
const health = await worker.fetch(new Request("https://r.example/"), env);
assert.match(await health.text(), /задан/);
assert.equal((await worker.fetch(new Request("https://r.example/v1/messages", { method: "POST", body: "{" }), { UPSTREAM_KEY: "k" })).status, 400);
console.log("OK");

// ---------- вход Firebase ----------
const { verifyFirebaseToken } = await import("./worker.js");
const enc = (o) => Buffer.from(JSON.stringify(o)).toString("base64url");
const pair = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
const other = await crypto.subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]);
const jwk = { ...(await crypto.subtle.exportKey("jwk", pair.publicKey)), kid: "k1", alg: "RS256", use: "sig" };
async function mint(over = {}, key = pair.privateKey, kid = "k1") {
  const now = Math.floor(Date.now() / 1000);
  const head = enc({ alg: "RS256", kid, typ: "JWT" });
  const body = enc({ aud: "lifeos-test", iss: "https://securetoken.google.com/lifeos-test", sub: "user42", iat: now - 10, exp: now + 3000, ...over });
  const sig = Buffer.from(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(`${head}.${body}`))).toString("base64url");
  return `${head}.${body}.${sig}`;
}
let jwksCalls = 0;
globalThis.fetch = async (url, init) => {
  if (String(url).includes("securetoken@system")) { jwksCalls++; return new Response(JSON.stringify({ keys: [jwk] })); }
  return new Response(JSON.stringify({ id: "x", choices: [{ message: { content: "ок" } }] }), { status: 200 });
};
assert.equal((await verifyFirebaseToken(await mint(), "lifeos-test")).sub, "user42");
assert.equal(await verifyFirebaseToken(await mint({ exp: 1 }), "lifeos-test"), null, "просрочен");
assert.equal(await verifyFirebaseToken(await mint({ aud: "чужой" }), "lifeos-test"), null, "чужой проект");
assert.equal(await verifyFirebaseToken(await mint({ iss: "https://evil.example" }), "lifeos-test"), null, "чужой издатель");
assert.equal(await verifyFirebaseToken(await mint({}, other.privateKey), "lifeos-test"), null, "подпись не тем ключом");
assert.equal(await verifyFirebaseToken(await mint({}, pair.privateKey, "нет-такого"), "lifeos-test"), null, "неизвестный ключ");
assert.equal(await verifyFirebaseToken(await mint({ sub: "" }), "lifeos-test"), null, "пустой пользователь");
assert.equal(await verifyFirebaseToken("мусор", "lifeos-test"), null);
const forged = (await mint()).split("."); forged[1] = enc({ aud: "lifeos-test", iss: "https://securetoken.google.com/lifeos-test", sub: "admin", iat: 1, exp: 9999999999 });
assert.equal(await verifyFirebaseToken(forged.join("."), "lifeos-test"), null, "подделанное содержимое");
assert.ok(jwksCalls >= 1 && jwksCalls <= 3, "ключи Google кэшируются");

// воркер целиком с обязательным входом и суточным лимитом на аккаунт
const envA = { UPSTREAM_KEY: "k", FIREBASE_PROJECT_ID: "lifeos-test", USER_DAILY_LIMIT: "2" };
const ask = (headers) => worker.fetch(new Request("https://r.example/v1/messages", { method: "POST", headers, body: JSON.stringify({ messages: [{ role: "user", content: "привет" }] }) }), envA);
assert.equal((await ask({})).status, 401, "без входа нельзя");
assert.equal((await ask({ authorization: "Bearer garbage" })).status, 401);
const good = { authorization: `Bearer ${await mint()}` };
assert.equal((await ask(good)).status, 200);
assert.equal((await ask(good)).status, 200);
assert.equal((await ask(good)).status, 429, "третий запрос за сутки");
const second = { authorization: `Bearer ${await mint({ sub: "user43" })}` };
assert.equal((await ask(second)).status, 200, "другой аккаунт не затронут");
// KV: счётчик переживает перезапуск
const store = new Map();
const kv = { get: async (k) => store.get(k) ?? null, put: async (k, v) => void store.set(k, v) };
const envK = { ...envA, LIMITS: kv, USER_DAILY_LIMIT: "1" };
const askK = (t) => worker.fetch(new Request("https://r.example/v1/messages", { method: "POST", headers: { authorization: `Bearer ${t}` }, body: JSON.stringify({ messages: [{ role: "user", content: "а" }] }) }), envK);
const tk = await mint({ sub: "kvuser" });
assert.equal((await askK(tk)).status, 200);
assert.equal((await askK(tk)).status, 429);
assert.equal(store.size, 1);
console.log("OK firebase");

// ---------- запасной платный ключ ----------
const calls = [];
globalThis.fetch = async (url, init) => {
  calls.push({ url: String(url), init });
  if (String(url).includes("groq")) return new Response("{}", { status: 429 });
  return new Response(JSON.stringify({ type: "message", role: "assistant", content: [{ type: "text", text: "платный ответ" }], stop_reason: "end_turn" }), { status: 200 });
};
const envF = { UPSTREAM_KEY: "gsk", FALLBACK_KEY: "sk-ant-x", FALLBACK_DAILY_LIMIT: "2" };
const askF = (e) => worker.fetch(new Request("https://r.example/v1/messages", { method: "POST", headers: { "x-device-id": "f" + Math.random() }, body: JSON.stringify({ max_tokens: 5000, messages: [{ role: "user", content: "привет" }] }) }), e);
let rf = await askF(envF);
assert.equal(rf.status, 200);
assert.equal((await rf.json()).content[0].text, "платный ответ");
const paid = calls.find((c) => c.url.includes("anthropic"));
assert.equal(paid.init.headers["x-api-key"], "sk-ant-x");
const sent = JSON.parse(paid.init.body);
assert.equal(sent.model, "claude-haiku-5-5");
assert.equal(sent.max_tokens, 1500, "потолок токенов");
assert.equal((await askF(envF)).status, 200);
assert.equal((await askF(envF)).status, 429, "потолок платных запросов в сутки");
assert.equal((await askF({ UPSTREAM_KEY: "gsk" })).status, 429, "без запасного ключа отказ остаётся отказом");
console.log("OK fallback");
