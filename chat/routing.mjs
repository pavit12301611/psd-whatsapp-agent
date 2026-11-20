const phoneJid = /^(\d{7,15})(?::\d+)?@s\.whatsapp\.net$/;
const lidJid = /^\d+(?::\d+)?@lid$/;
export function number(value) {
  const n = (value || "").replace(/[ ()-]/g, "").replace(/^\+/, "");
  if (!/^[1-9]\d{6,14}$/.test(n))
    throw Error("Enter both phone numbers including country code.");
  return n;
}

// Fail closed: an unmapped privacy ID is never interpreted as a phone number.
export async function incoming(
  m,
  tester,
  startedAt,
  resolveLid,
  now = Date.now(),
) {
  const k = m?.key,
    jid = k?.remoteJid;
  if (
    !k ||
    k.fromMe !== false ||
    !k.id ||
    k.participant ||
    typeof jid !== "string"
  )
    return;
  const sent = Number(m.messageTimestamp) * 1000;
  if (
    !Number.isFinite(sent) ||
    sent < startedAt - 1000 ||
    sent < now - 120000 ||
    sent > now + 5000
  )
    return;
  const text = m.message?.conversation ?? m.message?.extendedTextMessage?.text;
  if (typeof text !== "string" || !text.trim()) return;
  let pn = phoneJid.exec(jid)?.[1];
  if (!pn && lidJid.test(jid)) {
    pn =
      phoneJid.exec(k.remoteJidAlt)?.[1] ??
      phoneJid.exec(await resolveLid(jid))?.[1];
  }
  // The mobile UI explicitly opts in to all *incoming* personal chats.
  if (tester === "all-direct") {
    if (!phoneJid.test(jid) && !lidJid.test(jid)) return;
  } else if (pn !== tester) return;
  return { id: k.id, jid, text: text.trim().slice(0, 2000) };
}

export const signature = "psd-Pavit's ai model --";
export function away(mode) {
  if (!["busy", "sleeping", "driving"].includes(mode))
    throw Error("Choose busy, sleeping or driving.");
  return `${signature} Pavit is ${mode} right now. I’ve left your message for him. He’ll reply as soon as he’s free. Text me what you’d like Pavit to know, and I’ll handle the rest.`;
}

// One tester, no on-disk transcript. One instance across socket reconnects.
export function conversation({ provider, mode, generate, now = Date.now }) {
  const greeting = away(mode);
  let consent = false,
    greeted = false,
    stopped = false,
    turns = [],
    bucket = [];
  const seen = new Map();
  return async function reply(m, send, leaveNote) {
    const t = now();
    for (const [id, ts] of seen) if (t - ts > 125000) seen.delete(id);
    if (seen.has(m.id)) return "duplicate";
    seen.set(m.id, t);
    const cmd = m.text.toLowerCase();
    if (cmd === "/stop") {
      turns = [];
      consent = false;
      greeted = false;
      if (!stopped)
        await send(`${signature} Stopped. Send /start to begin again.`);
      stopped = true;
      return "stopped";
    }
    if (stopped && cmd !== "/start") return "stopped";
    stopped = false;
    if (cmd === "/start") {
      turns = [];
      greeted = false;
      consent = false;
      await send(
        `${signature} Pavit is ${mode} right now. What would you like me to pass along?`,
      );
      return "started";
    }
    if (provider !== "test" && greeted && cmd === "yes mimi") {
      await send(`${signature} AI chat is on. What else should Pavit know?`);
      consent = true;
      return "consented";
    }
    bucket = bucket.filter((x) => t - x < 60000);
    if (bucket.length >= 6) return "rate limited";
    bucket.push(t);
    // Only claim a handoff after the owner's self-chat send has succeeded.
    try {
      await leaveNote(m.text);
    } catch {
      await send(
        `${signature} I couldn’t leave a separate note for Pavit this time. Your original WhatsApp message is still in this chat.`,
      );
      return "handoff failed";
    }
    if (!greeted) {
      const optIn =
        provider === "test"
          ? ""
          : `\n\nFor AI conversation, reply YES MIMI to share future messages with ${provider}. Otherwise I’ll just pass your notes along. /stop pauses replies.`;
      await send(greeting + optIn);
      greeted = true;
      return "greeted";
    }
    if (!consent) {
      await send(`${signature} I’ve added that to Pavit’s notes. Thank you.`);
      return "noted";
    }
    const user = { role: "user", content: m.text };
    const answer = await generate([...turns, user]);
    await send(`${signature} ${answer}`);
    turns = [...turns, user, { role: "assistant", content: answer }].slice(-8);
    return "replied";
  };
}
