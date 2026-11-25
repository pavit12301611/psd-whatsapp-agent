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
  // `number` is the resolved phone number even for privacy (LID) chats, so
  // contact rules match the same person whichever identifier WhatsApp uses.
  return { id: k.id, jid, number: pn || null, text: text.trim().slice(0, 2000) };
}

export const signature = "Mimi — Pavit's own AI bot:";
export function reasonSuffix(mode, language, reason) {
  const text =
    mode === "busy" && typeof reason === "string"
      ? reason
          .replace(/[\s\u0000-\u001f]+/g, " ")
          .trim()
          .slice(0, 120)
      : "";
  return text ? (language === "hi" ? "\nWajah: " : "\nReason: ") + text : "";
}
export function away(mode, language = "en", reason = "") {
  if (!["busy", "sleeping", "driving"].includes(mode))
    throw Error("Choose busy, sleeping or driving.");
  if (language === "hi") {
    const status = {
      busy: "busy hai",
      sleeping: "so raha hai",
      driving: "drive kar raha hai",
    }[mode];
    return `${signature} Pavit abhi ${status}. Maine uske liye tumhara message chhod diya hai. Free hote hi woh reply karega. Pavit ko aur kya batana hai? Mujhe text kar do. Options ke liye /help likho.${reasonSuffix(mode, language, reason)}`;
  }
  return `${signature} Pavit is ${mode} right now. I’ve left your message for him. He’ll reply as soon as he’s free. Text me what you’d like Pavit to know, and I’ll handle the rest. Send /help for options.${reasonSuffix(mode, language, reason)}`;
}

// One tester, no on-disk transcript. One instance across socket reconnects.
export function conversation({
  provider,
  mode,
  generate,
  language = "en",
  reason = "",
  now = Date.now,
}) {
  const greeting = away(mode, language, reason);
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
    const hi = language === "hi";
    if (cmd === "/help") {
      await send(
        `${signature} ${hi ? "Callback ke liye /callback <time ya reason>, zaroori note ke liye /urgent <message> likho. /stop se replies band honge." : "Send /callback <time or reason> to request a call, /urgent <message> to flag a note, or /stop to pause replies."}`,
      );
      return "help";
    }
    // Only an explicit command is tagged. Never infer urgency from normal chat.
    const command = /^\/(callback|urgent)(?:\s+([\s\S]+))?$/i.exec(
      m.text.trim(),
    );
    const kind = command?.[1].toLowerCase();
    if (kind && !command[2]?.trim()) {
      await send(
        `${signature} ${hi ? "Aise likho" : "Try"}: ${kind === "callback" ? "/callback after 6 pm" : "/urgent delivery at the gate"}`,
      );
      return "needs details";
    }
    // Only claim a handoff after the owner's self-chat send has succeeded.
    try {
      await leaveNote(command ? command[2].trim() : m.text, kind);
    } catch {
      await send(
        `${signature} I couldn’t leave a separate note for Pavit this time. Your original WhatsApp message is still in this chat.`,
      );
      return "handoff failed";
    }
    if (kind) {
      const ack =
        kind === "callback"
          ? hi
            ? "Pavit ke liye callback request chhod di hai."
            : "I’ve left your callback request for Pavit."
          : hi
            ? "Pavit ke liye tumhara note urgent mark kar diya hai. Instant reply guaranteed nahi hai."
            : "I’ve marked your note urgent for Pavit. This does not guarantee an immediate response.";
      await send(`${signature} ${ack}`);
      return kind;
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
      await send(
        language === "hi"
          ? `${signature} Ye bhi Pavit ke notes mein add kar diya. Shukriya!`
          : `${signature} I’ve added that to Pavit’s notes. Thank you.`,
      );
      return "noted";
    }
    const user = { role: "user", content: m.text };
    const answer = await generate([...turns, user]);
    await send(`${signature} ${answer}`);
    turns = [...turns, user, { role: "assistant", content: answer }].slice(-8);
    return "replied";
  };
}
