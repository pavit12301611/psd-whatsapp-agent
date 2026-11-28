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

test("explicit callback is handed off once and reported locally only after the self-chat send succeeds", async () => {
  const events = [];
  const e = engine({
    owner,
    scope: "all-direct",
    provider: "groq",
    generate: () => assert.fail("No AI call"),
    clock: () => T,
    onNote: (item) => events.push(item),
  });
  e.connected(true);
  e.select("driving");
  const raw = msg(a, "cb", "/CALLBACK after 6 pm");
  const dispatch = () =>
    e.handle(
      "notify",
      raw,
      () => null,
      async (j, t) => events.push([j, t]),
    );
  await dispatch();
  await dispatch();
  assert.equal(events.length, 3);
  assert.match(events[0][1], /^CALLBACK · \+919000000001\nafter 6 pm$/);
  assert.deepEqual(events[1], {
    kind: "callback",
    sender: "+919000000001",
    text: "after 6 pm",
  });
  assert.match(events[2][1], /left your callback request/);
});
test("urgent handoff failure produces no local ticket and no success claim", async () => {
  const replies = [];
  const e = engine({
    owner,
    scope: "all-direct",
    provider: "test",
    clock: () => T,
    onNote: () => assert.fail(),
  });
  e.connected(true);
  e.select("sleeping");
  await e.handle(
    "notify",
    msg(a, "u", "/urgent at the gate"),
    () => null,
    async (j, t) => {
      if (j !== a) throw Error("offline");
      replies.push(t);
    },
  );
  assert.equal(replies.length, 1);
  assert.match(replies[0], /couldn’t leave/);
});
test("no command handoff while paused, opted out, asking for help or missing details", async () => {
  const notes = [],
    replies = [];
  const e = engine({
    owner,
    scope: "all-direct",
    provider: "test",
    clock: () => T,
    onNote: (x) => notes.push(x),
  });
  e.connected(true);
  let n = 0;
  const h = (t) =>
    e.handle(
      "notify",
      msg(a, String(++n), t),
      () => null,
      async (j, x) => replies.push(x),
    );
  await h("/urgent msg");
  assert.equal(replies.length, 0);
  e.select("busy", 0, "hi");
  await h("/help");
  await h("/callback");
  await h("/urgent");
  assert.match(replies[0], /Callback ke liye/);
  await h("/stop");
  await h("/urgent please call");
  assert.equal(notes.length, 0);
  await h("/start");
  await h("/urgent please call");
  assert.equal(notes[0].kind, "urgent");
  assert.match(replies.at(-1), /urgent mark/);
});
test("ordinary messages mentioning urgency are not copied into the local queue", async () => {
  const notes = [];
  const e = engine({
    owner,
    scope: "all-direct",
    provider: "test",
    clock: () => T,
    onNote: (item) => notes.push(item),
  });
  e.connected(true);
  e.select("busy");
  for (const [id, text] of [
    ["a", "this is urgent"],
    ["b", "/urgentcare details"],
  ])
    await e.handle(
      "notify",
      msg(a, id, text),
      () => null,
      async () => {},
    );
  assert.deepEqual(notes, [null, null]);
});

test("relink applies latest saved Busy reason only to fresh work, no replay or broadcast", async () => {
  let now = T;
  const e = engine({
    owner,
    scope: "all-direct",
    provider: "test",
    clock: () => now,
  });
  const out = [];
  e.connected(true);
  e.select("driving");
  e.connected(false);
  now += 60000;
  assert.equal(e.active, false);
  e.connected(true);
  assert.equal(
    e.restore({
      value: "busy",
      reason: " In a meeting\n until noon ",
      language: "en",
    }),
    "busy",
  );
  const h = (m) =>
    e.handle(
      "notify",
      m,
      () => null,
      async (j, t) => out.push(t),
    );
  await h(msg(a, "old", "hey", T));
  assert.deepEqual(out, []);
  await h(msg(a, "fresh", "hi", now));
  assert.match(out.at(-1), /Reason: In a meeting until noon$/);
  e.connected(false);
  e.connected(true);
  e.restore({ value: "off" });
  assert.equal(e.active, false);
});
test("restored sleep resumes remaining time, never extends or revives expired timer", () => {
  let now = T;
  const e = engine({
    owner,
    clock: () => now,
    later: () => 1,
    cancel: () => {},
  });
  const saved = {
    value: "sleeping",
    until: T + 60000,
    reason: "must not leak",
  };
  now += 30000;
  e.connected(true);
  e.restore(saved);
  assert.equal(e.until, T + 60000);
  assert.equal(e.callState().reason, "");
  e.connected(false);
  now += 31000;
  e.connected(true);
  assert.equal(e.restore(saved), "off");
  assert.equal(e.active, false);
});
test("priority contacts bypass Mimi; everyone else is greeted and lands in one digest", async () => {
  const out = [], priority = [], digests = [], status = [];
  const e = engine({
    owner,
    scope: "all-direct",
    provider: "test",
    clock: () => T,
    report: (s) => status.push(s),
    onPriority: (p) => priority.push(p),
    onDigest: (d) => digests.push(d),
  });
  e.connected(true);
  assert.deepEqual(e.configure({ vip: ["+91 90000 00001", "nope"], digest: true }), { priority: 1, ignored: 0, family: 0, location: false });
  e.select("busy", 0, "hi", "School · back at 2:00 pm");
  const send = (jid, text) => out.push([jid, text]);
  await e.handle("notify", msg(a, "v1", "emergency, hospital"), () => null, send);
  await e.handle("notify", msg(a, "v1", "emergency, hospital"), () => null, send);
  assert.equal(out.length, 0, "VIP numbers are never auto-replied");
  assert.equal(priority.length, 1, "duplicate alerts are suppressed");
  assert.equal(priority[0].urgent, true);
  await e.handle("notify", msg(b, "n1", "notes bhej dena"), () => null, send);
  assert.ok(out.length >= 1);
  assert.equal(digests.length, 0);
  e.select("off");
  assert.equal(digests.length, 1);
  assert.equal(digests[0].total, 1);
  assert.equal(digests[0].urgent, 0);
  assert.match(status.at(-1) || "", /Direct message/);
});

test("digest can be switched off and never fires twice for one window", async () => {
  const digests = [];
  const e = engine({
    owner,
    scope: "all-direct",
    provider: "test",
    clock: () => T,
    onDigest: (d) => digests.push(d),
  });
  e.connected(true);
  e.configure({ vip: [], digest: false });
  e.select("busy");
  await e.handle("notify", msg(b, "m1"), () => null, async () => {});
  e.select("off");
  assert.equal(digests.length, 0, "opt-out wins");
  e.configure({ digest: true });
  e.select("busy");
  await e.handle("notify", msg(b, "m2"), () => null, async () => {});
  e.select("off");
  e.select("off");
  assert.equal(digests.length, 1);
  assert.equal(digests[0].total, 1);
});

test("ignored numbers are silent: no reply, no alert, no note and no digest entry", async () => {
  const out = [], priority = [], digests = [], notes = [];
  const e = engine({
    owner,
    scope: "all-direct",
    provider: "test",
    clock: () => T,
    onPriority: (p) => priority.push(p),
    onDigest: (d) => digests.push(d),
    onNote: (n) => notes.push(n),
  });
  e.connected(true);
  assert.deepEqual(e.configure({ vip: [a], ignore: [a, "+91 90000 00002"], digest: true }), { priority: 1, ignored: 2, family: 0, location: false });
  e.select("busy", 0, "hi", "School");
  const send = (jid, text) => out.push([jid, text]);
  await e.handle("notify", msg(a, "i1", "urgent hospital jana hai"), () => null, send);
  await e.handle("notify", msg(b, "i2", "/callback 5 baje"), () => null, send);
  assert.equal(out.length, 0, "nothing is ever sent to an ignored number");
  assert.equal(priority.length, 0, "ignoring beats the priority list");
  assert.equal(notes.length, 0);
  e.select("off");
  assert.equal(digests.length, 0, "ignored senders never appear in the digest");
});

test("ignore and priority rules match privacy (LID) chats by resolved phone number", async () => {
  const out = [], priority = [], digests = [];
  const e = engine({
    owner,
    scope: "all-direct",
    provider: "test",
    clock: () => T,
    onPriority: (p) => priority.push(p),
    onDigest: (d) => digests.push(d),
  });
  e.connected(true);
  e.configure({ vip: ["919000000002"], ignore: ["919000000001"], digest: true });
  e.select("busy");
  const send = (jid, text) => out.push([jid, text]);
  const lid = (id, alt) => ({
    key: { id, fromMe: false, remoteJid: "5551234@lid", remoteJidAlt: alt },
    messageTimestamp: T / 1000,
    message: { conversation: "hello from a privacy id" },
  });
  await e.handle("notify", lid("l1", "919000000001@s.whatsapp.net"), () => null, send);
  assert.equal(out.length, 0, "ignored person stays silent even on a LID chat");
  await e.handle("notify", lid("l2", "919000000002@s.whatsapp.net"), () => null, send);
  assert.equal(out.length, 0, "priority person is never auto-replied");
  assert.equal(priority.length, 1);
  assert.match(priority[0].sender, /919000000002/);
  e.select("off");
  assert.equal(digests.length, 0);
});

test("learnt privacy ids keep ignored people silent even without an alt field", async () => {
  const out = [], links = [];
  const e = engine({
    owner,
    scope: "all-direct",
    provider: "test",
    clock: () => T,
    onLink: (pair) => links.push(pair),
  });
  e.connected(true);
  e.configure({ vip: [], ignore: ["919000000001"], digest: true });
  e.select("busy");
  const send = (jid, text) => out.push([jid, text]);
  const bare = (id) => ({
    key: { id, fromMe: false, remoteJid: "5551234@lid" },
    messageTimestamp: T / 1000,
    message: { conversation: "hi" },
  });
  // Unknown identity: fail closed instead of messaging a possibly ignored number.
  await e.handle("notify", bare("u1"), () => null, send);
  assert.equal(out.length, 0);
  // Once the mapping is seen, the same privacy chat is answered normally…
  await e.handle(
    "notify",
    {
      key: { id: "u2", fromMe: false, remoteJid: "5551234@lid", remoteJidAlt: "919000000009@s.whatsapp.net" },
      messageTimestamp: T / 1000,
      message: { conversation: "hello" },
    },
    () => null,
    send,
  );
  assert.equal(links.length, 1);
  assert.deepEqual(links[0], { lid: "5551234", number: "919000000009" });
  assert.ok(out.length >= 1, "a resolved, non-ignored chat still gets Mimi’s reply");
});

test("family members get Pavit's location on request, and only while sharing is on", async () => {
  const out = [], asks = [], pins = [];
  const e = engine({
    owner,
    scope: "all-direct",
    provider: "test",
    clock: () => T,
    later: () => 0,
    cancel: () => {},
    onLocate: (request) => asks.push(request),
    sendPin: async (jid, lat, lng) => pins.push([jid, lat, lng]),
    sendText: async (jid, text) => out.push([jid, text]),
  });
  e.connected(true);
  e.configure({ family: [a], location: false, digest: true });
  e.select("busy");
  const send = (jid, text) => out.push([jid, text]);
  await e.handle("notify", msg(a, "f1", "bhai location bhejo"), () => null, send);
  assert.equal(asks.length, 0, "sharing off means no coordinates leave the phone");
  assert.ok(out.length >= 1, "the normal away reply still goes out");

  e.configure({ family: [a], location: true, digest: true });
  e.select("busy");
  await e.handle("notify", msg(a, "f2", "where are you? live location"), () => null, send);
  assert.deepEqual(asks[0], { to: a, live: true, seq: 0 });
  await e.locate({ to: a, lat: 27.17, lng: 78.04, accuracy: 12, live: true, seq: 0 });
  assert.deepEqual(pins[0], [a, 27.17, 78.04]);
  assert.match(out.at(-1)[1], /location/i);
  // A stranger asking is never answered with coordinates.
  asks.length = 0;
  await e.handle("notify", msg(b, "f3", "location bhejo"), () => null, send);
  assert.equal(asks.length, 0);
});

test("owner's own reply silences Mimi for that chat until the window ends", async () => {
  const out = [];
  const e = engine({ owner, scope: "all-direct", provider: "test", clock: () => T });
  e.connected(true);
  e.select("busy");
  const send = (jid, text) => out.push([jid, text]);
  await e.handle("notify", msg(a, "o1"), () => null, send);
  const before = out.length;
  e.ownerReplied(a);
  await e.handle("notify", msg(a, "o2"), () => null, send);
  assert.equal(out.length, before, "Mimi stays out of a chat the owner answered");
  await e.handle("notify", msg(b, "o3"), () => null, send);
  assert.ok(out.length > before, "other chats are unaffected");
  e.select("off");
  e.select("busy");
  await e.handle("notify", msg(a, "o4"), () => null, send);
  assert.ok(out.length > before, "a new window starts fresh");
});

test("AI answers directly when the owner enables it, without a YES MIMI round trip", async () => {
  const { conversation } = await import("./routing.mjs");
  const sent = [];
  const prompts = [];
  const reply = conversation({
    provider: "gemini",
    mode: "busy",
    direct: true,
    generate: async (turns) => {
      prompts.push(turns.length);
      return "Main Mimi hoon, Pavit busy hai. Kya message chhodna hai?";
    },
    now: () => T,
  });
  await reply({ id: "d1", jid: a, text: "hello" }, async (t) => sent.push(t), async () => {});
  assert.match(sent[0], /is busy/);
  assert.doesNotMatch(sent[0], /YES MIMI/, "no consent prompt when direct mode is on");
  await reply({ id: "d2", jid: a, text: "kal milna hai" }, async (t) => sent.push(t), async () => {});
  assert.match(sent.at(-1), /Mimi|Pavit/);
  assert.equal(prompts.length, 1, "the model actually answered the follow-up");
});
