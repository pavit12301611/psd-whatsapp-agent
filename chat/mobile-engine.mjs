import { conversation, incoming } from "./routing.mjs";
import { digest, urgent } from "./routine.mjs";

// A selection is a new generation. Re-check generation before every send, not
// just before an async model request: Pause can arrive while a request is in flight.
export function engine({
  owner,
  scope,
  provider,
  generate,
  report = () => {},
  clock = Date.now,
  later = setTimeout,
  cancel = clearTimeout,
  onExpire = () => {},
  onNote = () => {},
  onDigest = () => {},
  onPriority = () => {},
}) {
  let generation = 0,
    mode = "off",
    since = Infinity,
    online = false;
  const chats = new Map(),
    optedOut = new Set(),
    prioritySeen = new Set();
  let vip = new Set(),
    digestOn = true,
    pending = [];
  let until = 0,
    alarm,
    language = "en",
    reason = "";
  function expire(epoch) {
    if (epoch !== generation || !until) return;
    select("off");
    onExpire();
  }
  function available() {
    // A suspended phone may deliver timers late. Guard every send as well.
    if (until && clock() >= until) expire(generation);
    return online && mode !== "off";
  }
  function select(value, durationMs = 0, lang = "en", why = "") {
    // A finished away window is the natural moment to hand back what arrived.
    if (mode !== "off" && pending.length) {
      const summary = digest(pending);
      pending = [];
      if (digestOn) onDigest(summary);
    }
    if (mode !== "off") for (const id of prioritySeen) prioritySeen.delete(id);
    if (alarm != null) cancel(alarm);
    alarm = undefined;
    until = 0;
    language = lang === "hi" ? "hi" : "en";
    if (
      !Number.isInteger(durationMs) ||
      durationMs < 0 ||
      durationMs > 25 * 3600000
    )
      value = "off";
    reason =
      value === "busy" && typeof why === "string" ? why.slice(0, 120) : "";
    generation++;
    mode =
      online && ["busy", "sleeping", "driving"].includes(value) ? value : "off";
    if (mode === "sleeping" && durationMs > 0) {
      until = clock() + durationMs;
      const epoch = generation;
      alarm = later(() => expire(epoch), durationMs);
    }
    since = Math.ceil(clock() / 1000) * 1000;
    chats.clear();
    return mode;
  }
  function connected(value) {
    online = value;
    select("off");
  }
  async function handle(type, raw, resolveLid, send, arrival = generation) {
    if (type !== "notify" || !available() || arrival !== generation) return;
    const valid = () => available() && arrival === generation;
    if (Number(raw.messageTimestamp) * 1000 < since) return;
    const m = await incoming(raw, scope, since, resolveLid, clock());
    if (!m || !valid()) return;
    const jid = m.jid,
      sender = jid.split("@")[0],
      name = jid.endsWith("@lid")
        ? "private chat " + sender
        : "+" + sender,
      cmd = m.text.toLowerCase();
    // Priority contacts skip Mimi entirely: the owner is alerted, nobody is auto-replied.
    if (vip.has(sender)) {
      if (!prioritySeen.has(m.id)) {
        prioritySeen.add(m.id);
        if (prioritySeen.size > 500) prioritySeen.delete(prioritySeen.values().next().value);
        onPriority({ sender: name, text: m.text.slice(0, 600), urgent: urgent(m.text) });
        report(`Priority message from ${name}.`);
      }
      return "priority";
    }
    if (optedOut.has(jid) && cmd !== "/start") return;
    if (cmd === "/start") optedOut.delete(jid);
    if (cmd === "/stop") optedOut.add(jid);
    if (!chats.has(jid)) {
      if (chats.size >= 256) {
        report("Chat limit reached for this mode.");
        return;
      }
      chats.set(
        jid,
        conversation({
          provider,
          mode,
          generate,
          language,
          reason,
          now: clock,
        }),
      );
    }
    const deliver = async (to, text) => {
      if (!valid()) throw Error("Paused");
      await send(to, text);
    };
    const result = await chats.get(jid)(
      m,
      (t) => deliver(jid, t),
      async (t, kind) => {
        const label =
          kind === "urgent"
            ? "URGENT"
            : kind === "callback"
              ? "CALLBACK"
              : "Mimi inbox";
        await deliver(owner + "@s.whatsapp.net", `${label} · ${name}\n${t}`);
        onNote(kind ? { kind, sender: name, text: t.slice(0, 600) } : null);
      },
    );
    if (valid() && result !== "duplicate" && digestOn) {
      pending.push({ sender: name, text: m.text.slice(0, 200), urgent: urgent(m.text) });
      if (pending.length > 100) pending.shift();
    }
    if (valid() && result !== "duplicate")
      report(
        result === "handoff failed"
          ? "Note could not be sent to your self-chat."
          : `Direct message: ${result}.`,
      );
  }
  return {
    select,
    restore(saved) {
      const end = saved.until ?? 0,
        remaining = end - clock();
      if (
        saved.value === "sleeping" &&
        end !== 0 &&
        (!Number.isSafeInteger(end) || remaining <= 0)
      )
        return select("off");
      return select(
        saved.value,
        saved.value === "sleeping" && end !== 0 ? remaining : 0,
        saved.language,
        saved.reason,
      );
    },
    connected,
    // Native owns the weekly routine; the engine only needs who may bypass Mimi.
    configure({ vip: numbers = [], digest: enabled = true } = {}) {
      vip = new Set(
        (Array.isArray(numbers) ? numbers : [])
          .map((n) => String(n).replace(/[^0-9]/g, ""))
          .filter((n) => /^[1-9][0-9]{6,14}$/.test(n))
          .slice(0, 50),
      );
      digestOn = enabled !== false;
      return vip.size;
    },
    handle,
    get generation() {
      return generation;
    },
    get until() {
      return until;
    },
    callState() {
      return available()
        ? {
            generation,
            mode,
            since,
            language,
            reason,
            optedOut: (jid) => optedOut.has(jid),
          }
        : null;
    },
    get active() {
      return available();
    },
  };
}
