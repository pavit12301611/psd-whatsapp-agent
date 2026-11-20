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
}) {
  let generation = 0,
    mode = "off",
    since = Infinity,
    online = false;
  const chats = new Map(),
    optedOut = new Set();
  function select(value) {
    generation++;
    mode =
      online && ["busy", "sleeping", "driving"].includes(value) ? value : "off";
    since = Math.ceil(clock() / 1000) * 1000;
    chats.clear();
    return mode;
  }
  function connected(value) {
    online = value;
    select("off");
  }
  async function handle(type, raw, resolveLid, send, arrival = generation) {
    if (
      type !== "notify" ||
      !online ||
      mode === "off" ||
      arrival !== generation
    )
      return;
    const valid = () => online && mode !== "off" && arrival === generation;
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
      chats.set(jid, conversation({ provider, mode, generate, now: clock }));
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
      (t) => deliver(owner + "@s.whatsapp.net", `Mimi inbox · ${name}\n${t}`),
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
    get active() {
      return online && mode !== "off";
    },
  };
}
