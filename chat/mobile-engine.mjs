import { conversation, incoming } from "./routing.mjs";

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
}) {
  let generation = 0,
    mode = "off",
    since = Infinity,
    online = false;
  const chats = new Map(),
    optedOut = new Set();
  let until = 0,
    alarm,
    language = "en";
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
  function select(value, durationMs = 0, lang = "en") {
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
      cmd = m.text.toLowerCase();
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
        conversation({ provider, mode, generate, language, now: clock }),
      );
    }
    const deliver = async (to, text) => {
      if (!valid()) throw Error("Paused");
      await send(to, text);
    };
    const name = jid.endsWith("@lid")
      ? "private chat " + jid.split("@")[0]
      : "+" + jid.split("@")[0];
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
    if (valid() && result !== "duplicate")
      report(
        result === "handoff failed"
          ? "Note could not be sent to your self-chat."
          : `Direct message: ${result}.`,
      );
  }
  return {
    select,
    connected,
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
            optedOut: (jid) => optedOut.has(jid),
          }
        : null;
    },
    get active() {
      return available();
    },
  };
}
