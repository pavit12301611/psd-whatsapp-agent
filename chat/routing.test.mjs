import test from "node:test";
import assert from "node:assert/strict";
import { away, conversation, incoming } from "./routing.mjs";
for (const mode of ["busy", "driving", "sleeping"])
  test(`First reply uses ${mode}, after handoff`, async () => {
    const log = [];
    const handle = conversation({ mode, provider: "test" });
    const m = { id: "a", text: "Call tomorrow" };
    await handle(
      m,
      (t) => log.push(t),
      (t) => log.push(t),
    );
    assert.deepEqual(log, ["Call tomorrow", away(mode)]);
    assert.ok(
      log[1].startsWith(`psd-Pavit's ai model -- Pavit is ${mode} right now.`),
    );
    assert.equal(
      await handle(
        m,
        () => assert.fail(),
        () => assert.fail(),
      ),
      "duplicate",
    );
  });
test("No false acknowledgment if self-chat fails", async () => {
  const handle = conversation({ mode: "busy", provider: "test" });
  let output;
  assert.equal(
    await handle(
      { id: "b", text: "Details" },
      (t) => (output = t),
      () => {
        throw Error();
      },
    ),
    "handoff failed",
  );
  assert.match(output, /couldn’t leave/);
});
test("No text sent to provider before consent; stop respects opt out", async () => {
  const notes = [],
    ai = [];
  const h = conversation({
    mode: "busy",
    provider: "gemini",
    generate: (ts) => {
      ai.push(ts);
      return "Thanks.";
    },
  });
  let id = 0;
  const send = (text) =>
    h(
      { id: String(++id), text },
      () => {},
      (t) => notes.push(t),
    );
  for (const t of [
    "Hi",
    "Delivery details",
    "YES MIMI",
    "Any questions?",
    "/stop",
    "another message",
  ])
    await send(t);
  assert.deepEqual(notes, ["Hi", "Delivery details", "Any questions?"]);
  assert.deepEqual(ai, [[{ role: "user", content: "Any questions?" }]]);
});
test("Only identified tester DMs pass", async () => {
  const tester = "919876543210",
    time = 1700000000000;
  const read = (jid, k = {}, age = 0) =>
    incoming(
      {
        key: { remoteJid: jid, id: "1", fromMe: false, ...k },
        messageTimestamp: (time - age) / 1000,
        message: { conversation: "hi" },
      },
      tester,
      time,
      () => null,
      time,
    );
  assert.ok(await read(tester + "@s.whatsapp.net"));
  assert.ok(
    await read("123@lid", { remoteJidAlt: tester + "@s.whatsapp.net" }),
  );
  for (const j of [
    "123@lid",
    "room@g.us",
    "status@broadcast",
    "918888888888@s.whatsapp.net",
  ])
    assert.equal(await read(j), undefined);
  assert.equal(
    await read(tester + "@s.whatsapp.net", { fromMe: true }),
    undefined,
  );
  assert.equal(await read(tester + "@s.whatsapp.net", {}, 180000), undefined);
});
