import assert from "node:assert/strict";
import test from "node:test";
import {
  active,
  answer,
  digest,
  fmt,
  mode,
  normalize,
  repeat,
  today,
  upcoming,
  urgent,
} from "./routine.mjs";

const at = (d, h, m = 0) => new Date(2026, 10, d, h, m); // Nov 2026; 16 Mon
const block = (over = {}) => ({
  id: "school",
  name: "School",
  kind: "school",
  start: "08:00",
  end: "14:00",
  days: [1, 2, 3, 4, 5, 6],
  reason: "School mein hoon",
  ...over,
});
const schedule = (over = {}) =>
  normalize({
    blocks: [
      block(),
      { id: "study", name: "Study", kind: "study", start: "19:00", end: "21:00", days: [1, 2, 3, 4, 5, 6, 7], reason: "Padh raha hoon" },
      { id: "sleep", name: "Sleep", kind: "sleep", start: "23:00", end: "07:00", days: [1, 2, 3, 4, 5, 6, 7] },
    ],
    ...over,
  });

test("validates names, windows, days and overlaps", () => {
  assert.throws(() => normalize({ blocks: [block({ name: "  " })] }), /name/);
  assert.throws(() => normalize({ blocks: [block({ start: "25:00" })] }), /start and end/);
  assert.throws(() => normalize({ blocks: [block({ start: "09:00", end: "09:00" })] }), /same time/);
  assert.throws(() => normalize({ blocks: [block({ days: [] })] }), /at least one day/);
  assert.throws(
    () => normalize({ blocks: [block(), block({ id: "x", name: "Tuition", start: "13:00", end: "15:00" })] }),
    /overlap/,
  );
  // Adjacent windows are fine, and a holiday can hold its own one-off block.
  const ok = normalize({
    blocks: [block(), block({ id: "t", name: "Tuition", start: "14:00", end: "16:00" })],
    skip: ["2026-11-20", "junk"],
  });
  assert.deepEqual(ok.skip, ["2026-11-20"]);
  assert.equal(ok.enabled, true);
});

test("resolves the live block, its mode and next start", () => {
  const s = schedule();
  assert.equal(active(s, at(16, 9))?.block.id, "school");
  assert.equal(mode(active(s, at(16, 9)).block), "busy");
  assert.equal(active(s, at(16, 15)), null);
  assert.equal(active(s, at(16, 20))?.block.id, "study");
  assert.equal(upcoming(s, at(16, 15), 1)[0].block.id, "study");
  assert.equal(upcoming(s, at(16, 15), 2)[1].block.id, "sleep");
});

test("overnight sleep carries into the next morning", () => {
  const s = schedule();
  assert.equal(active(s, at(16, 23, 30))?.block.id, "sleep");
  assert.equal(active(s, at(17, 6, 30))?.block.id, "sleep");
  assert.equal(active(s, at(17, 7, 1)), null);
  assert.equal(fmt("23:00"), "11:00 pm");
});

test("weekend, holidays and one-off overrides win over the weekly plan", () => {
  const s = schedule();
  // 22 Nov 2026 is a Sunday: only study + sleep remain.
  assert.equal(active(s, at(22, 9)), null);
  const holiday = schedule({ skip: ["2026-11-17"] });
  assert.equal(active(holiday, at(17, 9)), null);
  const exam = normalize({
    blocks: [block(), { id: "exam", name: "Exam", kind: "study", date: "2026-11-17", start: "09:00", end: "12:00" }],
  });
  assert.equal(active(exam, at(17, 10))?.block.id, "exam");
  assert.equal(active(exam, at(18, 10))?.block.id, "school");
});

test("derives human labels", () => {
  const s = schedule();
  assert.equal(repeat(s.blocks[0]), "Mon · Tue · Wed · Thu · Fri · Sat");
  assert.equal(repeat(s.blocks[2]), "Every day");
  assert.equal(today(s, at(16, 12)).length, 3);
});

test("flags urgency conservatively and summarises a digest", () => {
  assert.equal(urgent("Bhai ye urgent hai"), true);
  assert.equal(urgent("jaldi batao"), true);
  assert.equal(urgent("Kaisa hai? milte hai kal"), false);
  const d = digest([
    { sender: "+9198", text: "call me", urgent: true },
    { sender: "+9199", text: "notes bhej", urgent: false },
  ]);
  assert.equal(d.total, 2);
  assert.equal(d.urgent, 1);
  assert.match(d.text, /2 messages.*1 urgent/);
});

test("answers schedule questions locally", () => {
  const s = schedule();
  const now = at(16, 12);
  assert.match(answer(s, "aaj kya plan hai", now), /School/);
  assert.match(answer(s, "kya main abhi free hoon", now), /School until 2:00 pm/);
  assert.match(answer(s, "next kab", now), /Study/);
  assert.match(answer(s, "kal ka plan", now), /Study/);
  assert.match(answer(s, "neend", now), /Sleep/);
  assert.equal(answer(s, "hello", now), null);
});
