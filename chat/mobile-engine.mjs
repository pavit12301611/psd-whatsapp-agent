import { conversation, incoming } from "./routing.mjs";
import { digest, urgent } from "./routine.mjs";
import { local as localTriage, parse as parseTriage } from "./triage.mjs";

// A selection is a new generation. Re-check generation before every send, not
// just before an async model request: Pause can arrive while a request is in flight.
const LOCATION_ASK =
  /(location|live\s*location|kahan ho|kaha ho|kahan hai|where are you|loc bhej|apni loc)/i;

export function asksLocation(text) {
  return LOCATION_ASK.test(String(text || ""));
}

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
  onLink = () => {},
  onLocate = () => {},
  onTriage = () => {},
  classify = async () => null,
  sendPin = () => {},
  sendText = () => {},
}) {
  let generation = 0,
    mode = "off",
    since = Infinity,
    online = false;
  const chats = new Map(),
    optedOut = new Set(),
    prioritySeen = new Set();
  let vip = new Set(),
    ignore = new Set(),
    family = new Set(),
    shareLocation = false,
    direct = true,
    digestOn = true,
    triageOn = true,
    pending = [];
  const ownerHandled = new Set();
  // Privacy ids (LID) learnt from earlier messages, so rules survive an unanswered alt field.
  const known = new Map();
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
    if (mode !== "off") {
      for (const id of prioritySeen) prioritySeen.delete(id);
      ownerHandled.clear();
    }
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
  function markOwner(jid) {
    const id = String(jid || "").split("@")[0];
    if (!id) return false;
    ownerHandled.add(id);
    return true;
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
      learnt = m.number || known.get(sender) || null;
    if (m.number && jid.endsWith("@lid") && known.get(sender) !== m.number) {
      known.set(sender, m.number);
      onLink({ lid: sender, number: m.number });
    }
    // Never guess when silence was requested: an unidentifiable chat is not answered.
    if (!learnt && ignore.size) {
      report("Stayed silent: a privacy chat could not be matched to a number.");
      return "unidentified";
    }
    // One person may appear as a phone JID or a privacy (LID) JID; rules match either.
    const ids = learnt && learnt !== sender ? [sender, learnt] : [sender],
      name = jid.endsWith("@lid") && !learnt ? "private chat " + sender : "+" + (learnt || sender),
      cmd = m.text.toLowerCase(),
      // Once the owner answers this person, Mimi steps aside for the rest of the window.
      handled = () => ids.some((id) => ownerHandled.has(id));
    // Ignored numbers are a black hole: no reply, no alert, no note, no digest entry.
    if (ids.some((id) => ignore.has(id))) return "ignored";
    if (handled()) return "owner-handled";
    if (ids.some((id) => family.has(id)) && shareLocation && asksLocation(m.text)) {
      onLocate({ to: jid, live: /live/i.test(m.text), seq: 0 });
      return "locating";
    }
    // Priority contacts skip Mimi entirely: the owner is alerted, nobody is auto-replied.
    if (ids.some((id) => vip.has(id))) {
      if (!prioritySeen.has(m.id)) {
        prioritySeen.add(m.id);
        if (prioritySeen.size > 500) prioritySeen.delete(prioritySeen.values().next().value);
        onPriority({ sender: name, text: m.text.slice(0, 600), urgent: urgent(m.text) });
        report(`Priority message from ${name}.`);
      }
      return "priority";
    }
    if (triageOn) {
      const kind = ids.some((id) => family.has(id)) ? "family" : null;
      Promise.resolve()
        .then(async () => {
          let sorted = null;
          if (provider !== "test") {
            try {
              sorted = parseTriage(await classify(m.text));
            } catch {
              sorted = null;
            }
          }
          if (!sorted) sorted = localTriage(m.text, { family: !!kind, reason, language, mode });
          onTriage({
            id: m.id,
            jid,
            sender: name,
            text: m.text.slice(0, 400),
            category: sorted.category,
            urgent: sorted.urgent,
            draft: sorted.draft,
          });
        })
        .catch(() => {});
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
          direct,
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
    /** The owner replied from their own phone: Mimi leaves this chat alone. */
    ownerReplied(jid) {
      return markOwner(jid);
    },
    /** Sends an owner-approved draft from the inbox and marks the chat as handled. */
    async sendNow(to, text) {
      const jid = String(to || "");
      if (!jid) return false;
      try {
        await sendText(jid, String(text || "").slice(0, 1000));
        markOwner(jid);
        return true;
      } catch {
        return false;
      }
    },
    /** Native answers a location request with fresh coordinates (or an error). */
    async locate(payload = {}) {
      const to = String(payload.to || "");
      if (!to || !available()) return false;
      const seq = Math.max(0, Math.min(11, Number(payload.seq) || 0));
      try {
        if (payload.error || typeof payload.lat !== "number" || typeof payload.lng !== "number") {
          await sendText(to, "Mimi: location abhi available nahi hai. Pavit ko bolungi.");
          return false;
        }
        await sendPin(to, payload.lat, payload.lng, payload.accuracy);
        if (seq === 0)
          await sendText(
            to,
            payload.live
              ? "📍 Ye Pavit ki location hai. Live updates har 30 second mein aayenge."
              : "📍 Ye Pavit ki current location hai.",
          );
        if (payload.live && seq < 11) {
          const epoch = generation;
          later(() => {
            if (epoch === generation && available()) onLocate({ to, live: true, seq: seq + 1 });
          }, 30000);
        }
        return true;
      } catch {
        return false;
      }
    },
    // Native owns the weekly routine; the engine only needs who may bypass Mimi.
    configure({ vip: numbers = [], ignore: blocked = [], family: kin = [], location: share = false, direct: aiDirect = true, triage: sort = true, digest: enabled = true, mapped = [] } = {}) {
      const clean = (list, cap) =>
        new Set(
          (Array.isArray(list) ? list : [])
            .map((n) => String(n).replace(/[^0-9]/g, ""))
            .filter((n) => /^[1-9][0-9]{6,14}$/.test(n))
            .slice(0, cap),
        );
      vip = clean(numbers, 50);
      ignore = clean(blocked, 100);
      family = clean(kin, 50);
      shareLocation = share === true;
      direct = aiDirect !== false;
      triageOn = sort !== false;
      known.clear();
      for (const pair of Array.isArray(mapped) ? mapped.slice(0, 500) : [])
        if (Array.isArray(pair) && /^\d{7,15}$/.test(pair[0]) && /^[1-9]\d{6,14}$/.test(pair[1]))
          known.set(String(pair[0]), String(pair[1]));
      digestOn = enabled !== false;
      return { priority: vip.size, ignored: ignore.size, family: family.size, location: shareLocation };
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
