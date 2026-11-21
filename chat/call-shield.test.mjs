import test from "node:test";
import assert from "node:assert/strict";
import { callShield } from "./call-shield.mjs";
import { engine } from "./mobile-engine.mjs";
const T = 1800000000000,
  owner = "911111111111",
  pn = "919000000001",
  jid = pn + "@s.whatsapp.net",
  lid = "12345678901@lid";
function fixture(scope = "all-direct") {
  let t = T;
  const sent = [],
    reports = [];
  const core = engine({
    owner,
    scope,
    clock: () => t,
    later: () => 1,
    cancel: () => {},
  });
  core.connected(true);
  const shield = callShield({
    owner,
    scope,
    state: () => core.callState(),
    report: (m) => reports.push(m),
    now: () => t,
  });
  const offer = {
    id: "a",
    chatId: jid,
    from: jid,
    status: "offer",
    date: new Date(T),
    isGroup: false,
    offline: false,
  };
  const io = {
    reject: async (id, from) => sent.push(["reject", id, from]),
    send: async (to, text) => sent.push(["text", to, text]),
    resolve: async () => null,
  };
  return {
    core,
    shield,
    sent,
    reports,
    io,
    advance: (n) => {
      t += n;
    },
    run: (c = {}, overrides = {}) =>
      shield.event({ ...offer, ...c }, { ...io, ...overrides }),
    enable: (priority = []) => shield.configure({ enabled: true, priority }),
  };
}
for (const m of ["sleeping", "busy", "driving"])
  test("decline then single away text in " + m, async () => {
    const f = fixture();
    f.enable();
    f.core.select(m);
    await f.run();
    await f.run();
    assert.equal(f.sent.length, 2);
    assert.deepEqual(f.sent[0], ["reject", "a", jid]);
    assert.equal(f.sent[1][1], jid);
    assert.ok(f.sent[1][2].includes(`Pavit is ${m}`));
  });
test("opt in, live away selection, timer and tester restrictions all apply", async () => {
  const f = fixture();
  f.core.select("busy");
  await f.run();
  f.enable();
  f.core.select("off");
  await f.run();
  f.core.select("sleeping", 500);
  f.advance(600);
  await f.run();
  assert.equal(f.sent.length, 0);
  const g = fixture("918000000000");
  g.enable();
  g.core.select("driving");
  await g.run();
  assert.equal(g.sent.length, 0);
});
test("priority numbers never rejected via direct JID, caller metadata or LID mapping; unresolved/conflicting IDs pass through", async () => {
  const f = fixture();
  f.enable(["+" + pn]);
  f.core.select("busy");
  await f.run({ from: pn + ":3@s.whatsapp.net" });
  await f.run({ id: "b", from: lid, chatId: lid, callerPn: pn });
  await f.run(
    { id: "c", from: lid, chatId: lid },
    { resolve: async () => jid },
  );
  await f.run({ id: "d", chatId: lid, from: lid });
  await f.run({ id: "e", callerPn: "918888888888" });
  assert.deepEqual(f.sent, []);
});
test("skip non-offers, old, offline, group, outgoing/self and invalid direct addresses", async () => {
  const f = fixture();
  f.enable();
  f.core.select("sleeping");
  for (const [i, c] of [
    { status: "ringing" },
    { date: new Date(T - 61000) },
    { offline: true },
    { isGroup: true },
    { groupJid: "group@g.us" },
    { from: owner + "@s.whatsapp.net", chatId: owner + "@s.whatsapp.net" },
    { chatId: "1@g.us" },
    { date: new Date(NaN) },
  ].entries())
    await f.run({ id: "" + i, ...c });
  assert.deepEqual(f.sent, []);
});
test("priority edit or termination during async lookup prevents reject", async () => {
  for (const action of ["priority", "hangup", "pause"]) {
    const f = fixture();
    f.enable();
    f.core.select("busy");
    await f.run(
      { from: lid, chatId: lid },
      {
        resolve: async () => {
          if (action === "priority") f.enable([pn]);
          else if (action === "hangup") await f.run({ status: "terminate" });
          else f.core.select("off");
          return jid;
        },
      },
    );
    assert.deepEqual(f.sent, []);
  }
});
test("unconfirmed reject, pause and accept during reject do not send a message", async () => {
  for (const action of ["fail", "accept", "pause"]) {
    const f = fixture();
    f.enable();
    f.core.select("busy");
    await f.run(
      {},
      {
        reject: async () => {
          if (action === "fail") throw Error();
          if (action === "accept") await f.run({ status: "accept" });
          if (action === "pause") f.core.select("off");
        },
      },
    );
    assert.deepEqual(f.sent, []);
  }
});
test("repeat calls are declined, but caller gets at most one away text per minute", async () => {
  const f = fixture();
  f.enable();
  f.core.select("driving", 0, "hi");
  await f.run();
  await f.run({ id: "2" });
  assert.deepEqual(
    f.sent.map((r) => r[0]),
    ["reject", "text", "reject"],
  );
  assert.match(f.sent[1][2], /drive kar raha hai/);
});
test("invalid policy disables shielding rather than losing priority list", async () => {
  const f = fixture();
  f.enable();
  f.core.select("busy");
  f.shield.configure({ enabled: true, priority: ["oops"] });
  await f.run();
  assert.equal(f.sent.length, 0);
});
