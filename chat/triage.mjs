// Sorting a message and sketching a reply — locally first, so it works without a key.
import { urgent } from "./routine.mjs";

export const CATEGORIES = ["family", "friend", "college", "work", "spam", "other"];

const RULES = [
  ["spam", /(lottery|winner|prize|loan approved|kyc|click this link|otp|casino|crypto profit)/i],
  ["college", /(assignment|exam|class|syllabus|attendance|college|school|fees?|teacher|notes chahiye)/i],
  ["work", /(invoice|order|client|meeting|deadline|salary|payment|project|report)/i],
  ["family", /(mummy|papa|mom|dad|bhai|didi|behen|ghar|home|khaana|tabiyat|dawai)/i],
];

export function category(text) {
  const t = String(text || "");
  for (const [name, pattern] of RULES) if (pattern.test(t)) return name;
  return "friend";
}

export function local(text, { family = false, reason = "", language = "en", mode = "busy" } = {}) {
  const hi = language === "hi",
    clean = String(reason || "").trim(),
    state = hi
      ? mode === "sleeping" ? "so raha hoon" : "busy hoon"
      : mode === "sleeping" ? "sleeping" : mode === "driving" ? "driving" : "busy",
    draft = hi
      ? `Main abhi ${state}${clean ? ` — ${clean}` : ""}. Free hote hi reply karta hoon.`
      : `I'm ${state} right now${clean ? ` — ${clean}` : ""}. I'll reply as soon as I'm free.`;
  return {
    category: family ? "family" : category(text),
    urgent: urgent(text),
    draft: draft.slice(0, 240),
  };
}

/** Model output is untrusted text: keep only a well-formed, bounded answer. */
export function parse(raw) {
  if (typeof raw !== "string") return null;
  const start = raw.indexOf("{"), end = raw.lastIndexOf("}");
  if (start < 0 || end <= start) return null;
  let data;
  try {
    data = JSON.parse(raw.slice(start, end + 1));
  } catch {
    return null;
  }
  const name = String(data.category || "").toLowerCase(),
    draft = String(data.draft || "").replace(/\s+/g, " ").trim();
  if (!CATEGORIES.includes(name) || !draft) return null;
  return { category: name, urgent: data.urgent === true, draft: draft.slice(0, 240) };
}

export function prompt() {
  return [
    "You sort one incoming WhatsApp message for a personal assistant named Mimi.",
    "Reply with JSON only:",
    '{"category":"family|friend|college|work|spam|other","urgent":true|false,"draft":"reply of at most 240 characters"}',
    "The draft is written in the sender's language as the owner's assistant, first person plural for the owner:",
    "say briefly that the owner is away, ask what is needed, never promise a time, never share secrets, never claim to be the owner.",
    "Mark urgent only for real urgency (health, safety, money deadline today).",
  ].join(" ");
}
