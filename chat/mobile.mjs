// Private pipes to/from the foreground service. No TCP listener, shell or remote commands.
import { createInterface } from "node:readline";
import { join } from "node:path";
import { mkdir } from "node:fs/promises";
import makeSocket, {
  Browsers,
  DisconnectReason,
  useMultiFileAuthState,
} from "@whiskeysockets/baileys";
import pino from "pino";
import { number } from "./routing.mjs";
import { engine } from "./mobile-engine.mjs";
import { callShield } from "./call-shield.mjs";
import { ai } from "./providers.mjs";
process.umask(0o077);
const emit = (type, value, until = 0) =>
  process.stdout.write(JSON.stringify({ type, value, until }) + "\n");
let sock,
  core,
  shield,
  started = false,
  closing = false,
  retries = 0,
  pending = 0,
  queue = Promise.resolve();
function fail(message) {
  if (closing) return;
  closing = true;
  emit("error", message);
  setTimeout(() => process.exit(1), 100);
}
process.on("uncaughtException", () =>
  fail("Chat engine error. Stop and reconnect."),
);
process.on("unhandledRejection", () =>
  fail("Chat engine request failed. Stop and reconnect."),
);
async function init(config) {
  const owner = number(config.owner),
    scope = config.scope === "all-direct" ? "all-direct" : number(config.scope),
    provider = config.provider;
  if (!["test", "gemini", "groq"].includes(provider) || owner === scope)
    throw Error("invalid config");
  core = engine({
    owner,
    scope,
    provider,
    generate: (ts) => ai(provider, config.key, ts),
    report: (s) => emit("status", s),
    onExpire: () => {
      emit("mode", "off");
      emit("status", "Sleep timer finished. Auto-replies are paused.");
    },
    onNote: (item) => emit("note", item),
  });
  shield = callShield({
    owner,
    scope,
    state: () => (closing ? null : core.callState()),
    report: (s) => emit("status", s),
  });
  shield.configure(config.calls);
  const folder = join(process.env.HOME, "sessions", owner);
  await mkdir(folder, { recursive: true, mode: 0o700 });
  const { state, saveCreds } = await useMultiFileAuthState(folder);
  let previouslyLinked = !!state.creds.account;
  function connect() {
    if (closing) return;
    const s = makeSocket({
      auth: state,
      browser: Browsers.ubuntu("Chrome"),
      logger: pino({ level: "silent" }),
      syncFullHistory: false,
      shouldSyncHistoryMessage: () => false,
      markOnlineOnConnect: false,
      emitOwnEvents: false,
    });
    sock = s;
    let asked = false;
    s.ev.on("creds.update", () =>
      saveCreds().catch(() => fail("Could not save linked session.")),
    );
    s.ev.on("connection.update", async ({ qr, connection, lastDisconnect }) => {
      if (closing || sock !== s) return;
      if (qr && !asked) {
        asked = true;
        try {
          const code = await s.requestPairingCode(owner);
          if (sock === s && !closing) emit("code", code);
        } catch {
          if (sock === s)
            fail(
              "Pairing failed. Check the number, network and Linked devices.",
            );
        }
      }
      if (connection === "open") {
        previouslyLinked = true;
        core.connected(true);
        retries = 0;
        emit("linked", "Linked. Applying saved availability…");
      }
      if (connection === "close") {
        sock = null;
        core.connected(false);
        emit("offline", "Connection lost. Your availability is saved.");
        const code = lastDisconnect?.error?.output?.statusCode;
        retries++;
        const networkLoss = previouslyLinked && [408, 428, 503].includes(code);
        if (
          [401, 403, 411, 440, 500].includes(code) ||
          (!networkLoss && retries > 5)
        )
          return fail(
            `WhatsApp connection closed (${code || "network"}). Check Linked devices, then reconnect.`,
          );
        setTimeout(
          connect,
          code === DisconnectReason.restartRequired
            ? 500
            : Math.min(60000, 2 ** Math.min(retries, 6) * 1000),
        );
      }
    });
    s.ev.on("call", (calls) => {
      if (sock !== s || closing) return;
      for (const c of calls)
        shield
          .event(c, {
            resolve: (jid) => s.signalRepository.lidMapping.getPNForLID(jid),
            reject: (id, from) => s.rejectCall(id, from),
            send: (jid, text) =>
              s.sendMessage(jid, { text, linkPreview: null }),
          })
          .catch(() => emit("status", "Call handling unavailable."));
    });
    s.ev.on("messages.upsert", ({ type, messages }) => {
      if (!core.active || type !== "notify" || sock !== s) return;
      for (const raw of messages) {
        if (pending >= 48) break;
        pending++;
        const epoch = core.generation;
        queue = queue
          .then(() =>
            core.handle(
              type,
              raw,
              (jid) => s.signalRepository.lidMapping.getPNForLID(jid),
              (jid, text) => s.sendMessage(jid, { text, linkPreview: null }),
              epoch,
            ),
          )
          .catch((e) => {
            if (e.message !== "Paused")
              emit(
                "status",
                /^Provider HTTP \d{3}$/.test(e.message)
                  ? e.message
                  : "Could not complete message. Check network and account.",
              );
          })
          .finally(() => pending--);
      }
    });
  }
  connect();
}
const input = createInterface({ input: process.stdin, terminal: false });
input.on("line", (line) => {
  try {
    const m = JSON.parse(line);
    if (m.type === "init" && !started) {
      started = true;
      init(m).catch(() => fail("Could not initialise local chat session."));
    } else if (m.type === "calls" && shield) {
      shield.configure(m.calls);
    } else if (m.type === "mode" && core) {
      shield.configure(m.calls);
      const mode = core.restore(m);
      emit("mode", mode, core.until);
    }
  } catch {
    fail("Invalid local configuration.");
  }
});
input.on("close", () => process.exit(0)); // parent died: child must not keep sending
emit("ready", "");
