import test from "node:test";
import assert from "node:assert/strict";
import { readFile, readdir, stat } from "node:fs/promises";
import { dirname, join, resolve, relative, sep } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));

async function isFile(path) {
  try {
    return (await stat(path)).isFile();
  } catch {
    return false;
  }
}

// The APK bundles chat/*.mjs into a zip the phone unpacks. A module that is imported but
// not bundled makes the engine exit at startup, which looks exactly like "not connecting
// to WhatsApp". 0.9.7 shipped that way once (triage.mjs was left out of a hardcoded list).
test("every local module the engine imports exists and would be bundled", async () => {
  const files = (await readdir(here)).filter(
    (name) => name.endsWith(".mjs") && !name.endsWith(".test.mjs"),
  );
  assert.ok(files.includes("mobile.mjs"), "the engine entry point must sit in chat/");
  const missing = [];
  for (const file of files) {
    const source = await readFile(join(here, file), "utf8");
    for (const match of source.matchAll(
      /(?:from|import)\s*\(?\s*["'](\.[^"']+)["']/g,
    )) {
      const target = resolve(here, match[1]);
      const rel = relative(here, target);
      const inside = !rel.startsWith(`..${sep}`) && rel !== "..";
      if (!inside || rel.endsWith(".test.mjs") || !(await isFile(target)))
        missing.push(`${file} → ${match[1]}`);
    }
  }
  assert.deepEqual(missing, [], "unbundled or missing local imports");
});

test("the build script bundles chat/*.mjs instead of a hand-written list", async () => {
  const script = await readFile(join(here, "..", "tools", "build-chat-apk.sh"), "utf8");
  assert.match(script, /\*\.mjs/, "build script must glob chat/*.mjs");
  assert.doesNotMatch(
    script,
    /'mobile\.mjs','mobile-engine\.mjs'/,
    "the hardcoded module list must not come back",
  );
});
