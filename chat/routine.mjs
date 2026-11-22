// Mimi's weekly routine: validated local schedule, no network and no clock drift.
const TIME = /^([01]\d|2[0-3]):([0-5]\d)$/,
  DATE = /^\d{4}-\d{2}-\d{2}$/,
  ID = /^[a-z0-9]{1,24}$/;

export const KINDS = Object.freeze({
  school: "busy",
  study: "busy",
  coaching: "busy",
  work: "busy",
  sleep: "sleeping",
  driving: "driving",
  custom: "busy",
});

export function mode(block) {
  return KINDS[block?.kind] || "busy";
}

function clean(value, max) {
  return String(value ?? "")
    .replace(/[\u0000-\u001f\s]+/g, " ")
    .trim()
    .slice(0, max);
}

const minutes = (hhmm) => {
  const [h, m] = hhmm.split(":").map(Number);
  return h * 60 + m;
};

// A block that ends before it starts wraps past midnight, e.g. 23:00 → 07:00.
function spans(block) {
  const s = minutes(block.start),
    e = minutes(block.end);
  return e > s
    ? [[s, e]]
    : [
        [s, 1440],
        [0, e],
      ];
}

function clash(blocks) {
  const buckets = new Map();
  for (const b of blocks) {
    const keys = b.date ? [b.date] : b.days.map((d) => "d" + d);
    for (const key of keys) {
      const list = buckets.get(key) || [];
      list.push(b);
      buckets.set(key, list);
    }
  }
  for (const list of buckets.values()) {
    for (let i = 0; i < list.length; i++)
      for (let j = i + 1; j < list.length; j++) {
        const a = spans(list[i]),
          b = spans(list[j]);
        if (a.some(([x, y]) => b.some(([p, q]) => x < q && p < y)))
          throw Error(
            `${list[i].name} and ${list[j].name} overlap. ${list[j].name === list[i].name ? "" : "Adjust one of them."}`.trim(),
          );
      }
  }
}

export function normalize(input) {
  const src = input && typeof input === "object" ? input : {},
    raw = Array.isArray(src.blocks) ? src.blocks.slice(0, 60) : [],
    ids = new Set();
  const blocks = raw.map((b, index) => {
    if (!b || typeof b !== "object") throw Error("Invalid routine block.");
    const name = clean(b.name, 32);
    if (!name) throw Error("Give the block a name.");
    const id = typeof b.id === "string" && ID.test(b.id) ? b.id : "b" + index;
    if (ids.has(id)) throw Error("Duplicate block.");
    ids.add(id);
    const start = String(b.start || ""),
      end = String(b.end || "");
    if (!TIME.test(start) || !TIME.test(end))
      throw Error(`${name}: pick a start and end time.`);
    if (start === end) throw Error(`${name}: start and end are the same time.`);
    const date = typeof b.date === "string" && DATE.test(b.date) ? b.date : "";
    let days = Array.isArray(b.days)
      ? [...new Set(b.days.filter((d) => Number.isInteger(d) && d >= 1 && d <= 7))].sort()
      : [];
    if (!date && !days.length) throw Error(`${name}: choose at least one day.`);
    if (date) days = [];
    return {
      id,
      name,
      kind: Object.hasOwn(KINDS, b.kind) ? b.kind : "custom",
      start,
      end,
      days,
      date,
      language: b.language === "hi" ? "hi" : "en",
      reason: clean(b.reason, 120),
    };
  });
  const skip = Array.isArray(src.skip)
    ? [...new Set(src.skip.filter((d) => typeof d === "string" && DATE.test(d)))].sort().slice(-180)
    : [];
  clash(blocks);
  return { enabled: src.enabled !== false, blocks, skip };
}

export const empty = () => ({ enabled: true, blocks: [], skip: [] });

export function isoDate(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

const isoDay = (date) => date.getDay() || 7;

function occurrences(schedule, day) {
  const key = isoDate(day),
    weekday = isoDay(day),
    override = schedule.blocks.some((b) => b.date === key);
  if (schedule.skip.includes(key) && !override) return [];
  return schedule.blocks
    .filter((b) => (b.date ? b.date === key : !override && b.days.includes(weekday)))
    .map((block) => {
      const start = new Date(day.getFullYear(), day.getMonth(), day.getDate(), ...block.start.split(":").map(Number));
      let end = new Date(day.getFullYear(), day.getMonth(), day.getDate(), ...block.end.split(":").map(Number));
      if (end <= start) end = new Date(end.getTime() + 86400000);
      return { block, start, end };
    });
}

/** The block covering `now`, including last night's overnight sleep. */
export function active(schedule, now = new Date()) {
  const day = new Date(now.getFullYear(), now.getMonth(), now.getDate()),
    yesterday = new Date(day.getTime() - 86400000);
  return (
    [...occurrences(schedule, day), ...occurrences(schedule, yesterday)]
      .filter((o) => o.start <= now && now < o.end)
      .sort((a, b) => b.start - a.start)[0] || null
  );
}

export function upcoming(schedule, now = new Date(), limit = 3) {
  const day = new Date(now.getFullYear(), now.getMonth(), now.getDate()),
    found = [];
  for (let offset = 0; offset < 8 && found.length < limit; offset++) {
    const date = new Date(day.getFullYear(), day.getMonth(), day.getDate() + offset);
    for (const o of occurrences(schedule, date))
      if (o.start > now) found.push(o);
  }
  return found.sort((a, b) => a.start - b.start).slice(0, limit);
}

export function today(schedule, now = new Date()) {
  return occurrences(
    schedule,
    new Date(now.getFullYear(), now.getMonth(), now.getDate()),
  ).sort((a, b) => a.start - b.start);
}

export function fmt(hhmm) {
  const [h, m] = hhmm.split(":").map(Number);
  return `${((h + 11) % 12) + 1}:${String(m).padStart(2, "0")} ${h < 12 ? "am" : "pm"}`;
}

const DAY = { 1: "Mon", 2: "Tue", 3: "Wed", 4: "Thu", 5: "Fri", 6: "Sat", 7: "Sun" };

export function repeat(block) {
  if (block.date) return "Once · " + block.date.slice(5);
  return block.days.length === 7
    ? "Every day"
    : block.days.map((d) => DAY[d]).join(" · ");
}

export function describe(o, now = new Date()) {
  const sameDay = isoDate(o.start) === isoDate(now);
  return `${sameDay ? "" : DAY[isoDay(o.start)] + " · "}${fmt(o.block.start)}–${fmt(o.block.end)} · ${o.block.name}`;
}

const URGENT = [
  "urgent",
  "asap",
  "emergency",
  "immediately",
  "right now",
  "jaldi",
  "turant",
  "abhi",
  "hospital",
  "accident",
  "help me",
  "madad",
  "paise",
  "payment",
  "deadline",
  "aaj hi",
  "kal se pehle",
];

export function urgent(text) {
  const t = clean(text, 500).toLowerCase();
  return URGENT.some((k) => new RegExp(`(^|[^a-z])${k}([^a-z]|$)`).test(t));
}

export function digest(entries) {
  const list = (Array.isArray(entries) ? entries : []).filter(Boolean).slice(0, 100),
    flagged = list.filter((e) => e.urgent),
    text =
      list.length === 1
        ? "1 message while you were away"
        : `${list.length} messages while you were away`;
  return {
    total: list.length,
    urgent: flagged.length,
    text: flagged.length ? `${text} · ${flagged.length} urgent` : text,
    entries: list.slice(0, 6),
  };
}

/** Local answers for "Ask Mimi"; returns null when the question is not about time. */
export function answer(schedule, question, now = new Date(), language = "en") {
  const q = clean(question, 120).toLowerCase();
  if (!q) return null;
  const hi = language === "hi",
    tomorrow = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1),
    live = active(schedule, now),
    soon = upcoming(schedule, now, 3);
  const list = (day) =>
    today(schedule, day)
      .map((o) => `• ${fmt(o.block.start)}–${fmt(o.block.end)}  ${o.block.name}`)
      .join("\n") || (hi ? "• Koi block nahi — aaj poora free." : "• No blocks — you are free all day.");
  const status = () =>
    live
      ? hi
        ? `Abhi ${live.block.name} chal raha hai, ${fmt(live.block.end)} tak.`
        : `You are in ${live.block.name} until ${fmt(live.block.end)}.`
      : hi
        ? "Abhi koi block nahi — Mimi off hai."
        : "No block right now — Mimi is off.";
  const next = () =>
    soon.length
      ? (hi ? "Aage: " : "Coming up: ") + soon.map((o) => describe(o, now)).join(" · ")
      : hi
        ? "Aage koi block nahi."
        : "Nothing else scheduled.";
  if (/kal|tomorrow/.test(q)) return `${hi ? "Kal ka plan" : "Tomorrow"}\n${list(tomorrow)}\n\n${next()}`;
  if (/abhi|now|free|khali|status/.test(q)) return `${status()}\n${next()}`;
  if (/next|agle|aage|kab|when|upcoming/.test(q)) return `${status()}\n${next()}`;
  if (/neend|sleep|sona/.test(q)) {
    const sleep = schedule.blocks.filter((b) => b.kind === "sleep");
    return sleep.length
      ? sleep.map((b) => `${b.name} · ${fmt(b.start)}–${fmt(b.end)}`).join("\n")
      : hi
        ? "Sleep block set nahi hai."
        : "No sleep block yet.";
  }
  if (/aaj|today|plan|schedule|routine|time/.test(q)) return `${hi ? "Aaj ka plan" : "Today"}\n${list(now)}\n\n${next()}`;
  return null;
}
