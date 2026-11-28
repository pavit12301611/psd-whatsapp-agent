import assert from "node:assert/strict";
import test from "node:test";
import { local, parse, prompt } from "./triage.mjs";

test("local triage sorts without a model", () => {
  assert.equal(local("assignment kal submit karna hai").category, "college");
  assert.equal(local("client ka invoice bhej dena").category, "work");
  assert.equal(local("mummy bol rahi hain").category, "family");
  assert.equal(local("You won a lottery, click this link").category, "spam");
  assert.equal(local("kal milte hai").category, "friend");
  assert.equal(local("hospital le jao jaldi").urgent, true);
});

test("local draft uses the block reason and language", () => {
  const en = local("hello", { reason: "School", mode: "busy" });
  assert.match(en.draft, /busy/);
  const hi = local("hello", { reason: "School", language: "hi" });
  assert.match(hi.draft, /busy hoon|so raha/);
  assert.ok(local("hi", { language: "hi", mode: "sleeping" }).draft.includes("so raha"));
});

test("model output must be well formed and bounded", () => {
  const ok = parse('{"category":"college","urgent":false,"draft":"notes bhej dunga"}');
  assert.deepEqual(ok, { category: "college", urgent: false, draft: "notes bhej dunga" });
  assert.equal(parse('Sure! {"category":"weird","draft":"x"}'), null);
  assert.equal(parse("no json here"), null);
  assert.equal(parse('{"category":"work","draft":""}'), null);
  const long = parse(JSON.stringify({ category: "work", draft: "x".repeat(400) }));
  assert.equal(long.draft.length, 240);
  assert.match(prompt(), /JSON only/);
});
