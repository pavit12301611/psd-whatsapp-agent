import { signature } from "./routing.mjs";

const pnJid = /^([1-9]\d{6,14})(?::\d+)?@s\.whatsapp\.net$/;
const lidJid = /^\d+(?::\d+)?@lid$/;
const metaPn = (v) => pnJid.exec(v)?.[1] || /^\+?([1-9]\d{6,14})$/.exec(v)?.[1];
const direct = (v) => pnJid.test(v) || lidJid.test(v);

// Never resolve a privacy ID by treating its numeric part as a telephone number.
async function identity(c, resolve) {
  if (!direct(c.from) || !direct(c.chatId)) return;
  const numbers = new Set();
  if (c.callerPn) {
    const n = metaPn(c.callerPn);
    if (!n) return;
    numbers.add(n);
  }
  const lids = new Set();
  for (const address of [c.from, c.chatId]) {
    const n = pnJid.exec(address)?.[1];
    if (n) numbers.add(n);
    else lids.add(address);
  }
  for (const lid of lids) {
    const mapped = await resolve(lid);
    if (mapped) {
      const n = pnJid.exec(mapped)?.[1];
      if (!n) return;
      numbers.add(n);
    } else if (!c.callerPn) return;
  }
  if (numbers.size === 1) return [...numbers][0];
}

export function callShield({
  owner,
  scope,
  state,
  report = () => {},
  now = Date.now,
}) {
  let enabled = false,
    priority = new Set(),
    version = 0;
  const seen = new Map(),
    replies = new Map();
  function configure(policy) {
    version++;
    enabled = false;
    priority = new Set();
    if (
      !policy ||
      !Array.isArray(policy.priority) ||
      policy.priority.length > 50 ||
      policy.priority.some(
        (n) => typeof n !== "string" || !/^\+?[1-9]\d{6,14}$/.test(n),
      )
    )
      return;
    priority = new Set(policy.priority.map((n) => n.replace(/^\+/, "")));
    enabled = policy.enabled === true;
  }
  async function event(c, { resolve, reject, send }) {
    if (!c || typeof c.id !== "string" || !c.id || c.id.length > 200) return;
    const t = now();
    for (const [key, v] of seen) if (t - v.at > 120000) seen.delete(key);
    for (const [key, v] of replies) if (t - v >= 60000) replies.delete(key);
    if (
      ["accept", "preaccept", "reject", "terminate", "timeout"].includes(
        c.status,
      )
    ) {
      const rec = seen.get(c.id);
      if (rec) {
        rec.ended = true;
        if (["accept", "preaccept", "timeout"].includes(c.status))
          rec.answeredOrMissed = true;
      } else if (seen.size < 1024) seen.set(c.id, { at: t, ended: true });
      return;
    }
    const born = c.date instanceof Date ? c.date.getTime() : NaN;
    const snapshot = state();
    if (
      !enabled ||
      !snapshot ||
      c.status !== "offer" ||
      c.offline !== false ||
      c.isGroup !== false ||
      c.groupJid ||
      !Number.isFinite(born) ||
      born < snapshot.since ||
      t - born > 15000 ||
      born > t + 3000 ||
      seen.has(c.id) ||
      seen.size >= 1024
    )
      return;
    const rec = { at: t, ended: false };
    seen.set(c.id, rec);
    const revision = version;
    const allowed = () =>
      enabled &&
      version === revision &&
      state()?.generation === snapshot.generation;
    let n;
    try {
      n = await identity(c, resolve);
    } catch {
      /* fail open */
    }
    if (!allowed() || rec.ended || now() - born > 15000) return;
    if (
      !n ||
      priority.has(n) ||
      n === owner ||
      (scope !== "all-direct" && scope !== n)
    )
      return;
    try {
      await reject(c.id, c.from);
    } catch {
      report("Could not confirm decline. No caller text sent.");
      return;
    }
    if (
      !allowed() ||
      rec.answeredOrMissed ||
      snapshot.optedOut(c.from) ||
      snapshot.optedOut(c.chatId) ||
      snapshot.optedOut(n + "@s.whatsapp.net")
    )
      return;
    if (replies.has(n)) return;
    replies.set(n, now());
    const status = {
      busy: "busy hai",
      sleeping: "so raha hai",
      driving: "drive kar raha hai",
    }[snapshot.mode];
    const text =
      snapshot.language === "hi"
        ? `${signature} Pavit abhi ${status}, isliye call nahi le sakta. Apna message yahin bhej do, ya /callback ke saath time likh do.`
        : `${signature} Pavit is ${snapshot.mode} and can’t take your call right now. Please leave a message here, or send /callback with a good time to call you back.`;
    try {
      await send(c.chatId, text);
      report("Call decline requested · away text sent.");
    } catch {
      replies.delete(n);
      report("Call decline requested, but the caller text could not be sent.");
    }
  }
  return { configure, event };
}
