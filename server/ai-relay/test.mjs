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
