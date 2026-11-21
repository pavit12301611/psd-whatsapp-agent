import test from "node:test";
import assert from "node:assert/strict";
import { engine } from "./mobile-engine.mjs";
const T = 1800000000000;
const owner = "911234567890";
const a = "919000000001@s.whatsapp.net",
  b = "919000000002@s.whatsapp.net";
function msg(jid, id, text = "hello", stamp = T) {
  return {
    key: { id, fromMe: false, remoteJid: jid },
    messageTimestamp: stamp / 1000,
    message: { conversation: text },
  };
}
test("linked is silent; explicit away mode replies independently to all new direct texts, never broadcasts", async () => {
  const out = [];
  const e = engine({
    owner,
    scope: "all-direct",
    provider: "test",
    clock: () => T,
  });
  const send = (jid, text) => out.push([jid, text]);
  const handle = (type, m) => e.handle(type, m, () => null, send);
  e.connected(true);
  await handle("notify", msg(a, "1"));
  assert.equal(out.length, 0);
  e.select("sleeping");
  assert.equal(out.length, 0);
  for (const [type, m] of [
    ["append", msg(a, "2")],
    ["notify", msg("123@g.us", "3")],
    ["notify", msg(a, "4", "old", T - 2000)],
  ])
    await handle(type, m);
  assert.equal(out.length, 0);
  for (const j of [a, b]) await handle("notify", msg(j, "5"));
  assert.deepEqual(
    out.map((x) => x[0]),
    [owner + "@s.whatsapp.net", a, owner + "@s.whatsapp.net", b],
  );
  assert.match(out[3][1], /Pavit is sleeping/);
  const old = e.generation;
  e.select("off");
  await handle("notify", msg(b, "6"));
  e.select("busy");
  await e.handle("notify", msg(a, "7"), () => null, send, old);
  assert.equal(out.length, 4);
  e.connected(false);
  e.connected(true);
  assert.equal(e.active, false);
});
test("pause while note handoff pending suppresses subsequent away reply", async () => {
  const e = engine({
    owner,
    scope: "all-direct",
    provider: "test",
    clock: () => T,
  });
  e.connected(true);
  e.select("busy");
  const out = [];
  await assert.rejects(
    e.handle(
      "notify",
      msg(a, "x"),
      () => null,
      async (j) => {
        out.push(j);
        e.select("off");
      },
    ),
    /Paused/,
  );
  assert.deepEqual(out, [owner + "@s.whatsapp.net"]);
});

test("sleep deadline guards delayed Android wake and in-flight replies", async () => {
  let now = T,
    expired = 0,
    notes = 0,
    callback;
  const sent = [];
  const e = engine({
    owner,
    scope: "all-direct",
    provider: "test",
    clock: () => now,
    later: (fn) => {
      callback = fn;
      return 1;
    },
    cancel: () => {},
    onExpire: () => expired++,
    onNote: () => notes++,
  });
  e.connected(true);
  e.select("sleeping", 60000);
  assert.equal(e.until, T + 60000);
  await assert.rejects(
    e.handle(
      "notify",
      msg(a, "timer"),
      () => null,
      async (j, t) => {
        sent.push(j);
        now += 60000;
      },
    ),
    /Paused/,
  );
  callback();
  assert.equal(notes, 1);
  assert.equal(expired, 1);
  assert.equal(e.active, false);
  assert.deepEqual(sent, [owner + "@s.whatsapp.net"]);
  assert.equal(e.until, 0);
});
test("changing mode cancels old timer; only current alarm can auto-pause", () => {
  let callbacks = [],
    cancelled = [],
    expired = 0;
  const e = engine({
    owner,
    clock: () => T,
    later: (cb) => {
      callbacks.push(cb);
      return callbacks.length;
    },
    cancel: (id) => cancelled.push(id),
    onExpire: () => expired++,
  });
  e.connected(true);
  e.select("sleeping", 120000);
  e.select("busy");
  callbacks[0]();
  assert.ok(e.active);
  assert.equal(expired, 0);
  assert.deepEqual(cancelled, [1]);
  e.select("sleeping", 60000);
  callbacks[1]();
  assert.equal(e.active, false);
  assert.equal(expired, 1);
  e.select("sleeping", -1);
  assert.equal(e.active, false);
});
