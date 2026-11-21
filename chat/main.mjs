import makeWASocket, {
  Browsers,
  DisconnectReason,
  useMultiFileAuthState,
} from "@whiskeysockets/baileys";
import pino from "pino";
import { mkdir, chmod } from "node:fs/promises";
import { homedir } from "node:os";
import { join } from "node:path";
import { conversation, incoming, number } from "./routing.mjs";
import { ai } from "./providers.mjs";

process.umask(0o077);
const owner = number(process.env.MIMI_OWNER),
  tester = number(process.env.MIMI_TESTER),
  provider = process.env.MIMI_PROVIDER;
if (owner === tester)
  throw Error("Use a different account for the tester, not a self-chat.");
if (!["test", "gemini", "groq"].includes(provider))
  throw Error("Run bash start.sh to select a provider.");
const session = join(homedir(), ".mimi-linked-chat", owner);
await mkdir(session, { recursive: true, mode: 0o700 });
await chmod(session, 0o700);
const { state, saveCreds } = await useMultiFileAuthState(session);
const reply = conversation({
  provider,
  mode: process.env.MIMI_MODE,
  generate: (t) => ai(provider, process.env.MIMI_KEY, t),
});
const since = Date.now();
let active,
  retries = 0,
  timer,
  halted = false,
  ready = false,
  pending = 0,
  chain = Promise.resolve();
function stop(message, code = 0) {
  halted = true;
  ready = false;
  clearTimeout(timer);
  active?.end(undefined);
  console.log(message);
  process.exitCode = code;
}
process.once("SIGINT", () =>
  stop(
    "Mimi stopped. To revoke access: WhatsApp Business → Linked devices → Log out.",
  ),
);
process.once("SIGTERM", () => stop("Mimi stopped."));
function connect() {
  if (halted) return;
  const sock = makeWASocket({
    auth: state,
    browser: Browsers.ubuntu("Chrome"),
    logger: pino({ level: "silent" }),
    syncFullHistory: false,
    shouldSyncHistoryMessage: () => false,
    markOnlineOnConnect: false,
    emitOwnEvents: false,
  });
  active = sock;
  let requested = false;
  sock.ev.on("creds.update", () =>
    saveCreds().catch(() =>
      stop("Could not save local device session. Check Termux storage.", 1),
    ),
  );
  sock.ev.on(
    "connection.update",
    async ({ connection, qr, lastDisconnect }) => {
      if (halted || active !== sock) return;
      if (qr && !requested) {
        requested = true;
        try {
          const code = await sock.requestPairingCode(owner);
          if (active === sock && !halted)
            console.log(
              `Private pairing code: ${code.match(/.{1,4}/g).join("-")}\nEnter ONLY in your WhatsApp Business → Linked devices → Link a device → Link with phone number instead. Never share it.`,
            );
        } catch {
          if (active === sock && !halted)
            stop(
              "Could not request a pairing code. Verify number and network, then restart.",
              1,
            );
        }
      }
      if (connection === "open") {
        retries = 0;
        ready = true;
        console.log(
          "LINKED · waiting for a NEW direct text from the selected tester. Ctrl+C pauses replies.",
        );
      }
      if (connection === "close") {
        active = null;
        ready = false;
        const code = lastDisconnect?.error?.output?.statusCode;
        if (
          [
            DisconnectReason.loggedOut,
            DisconnectReason.forbidden,
            DisconnectReason.badSession,
            DisconnectReason.connectionReplaced,
            DisconnectReason.multideviceMismatch,
          ].includes(code)
        )
          return stop(
            `Session closed (${code}). Check Linked devices. See chat/README.md before re-pairing.`,
            1,
          );
        if (++retries > 5)
          return stop(
            "Reconnect limit reached. Check internet, then restart manually.",
            1,
          );
        console.log(
          `Disconnected (${code || "network"}). Reconnecting ${retries}/5…`,
        );
        timer = setTimeout(
          connect,
          code === DisconnectReason.restartRequired
            ? 500
            : Math.min(30000, 1000 * 2 ** retries),
        );
      }
    },
  );
  sock.ev.on("messages.upsert", ({ type, messages }) => {
    if (type !== "notify" || !ready || active !== sock || halted) return;
    for (const raw of messages) {
      if (pending >= 20) break;
      pending++;
      chain = chain
        .then(async () => {
          if (halted || !ready || active !== sock) return;
          const m = await incoming(raw, tester, since, (jid) =>
            sock.signalRepository.lidMapping.getPNForLID(jid),
          );
          if (!m) return;
          console.log("New tester text received.");
          const deliver = async (jid, text) => {
            if (halted || !ready || active !== sock)
              throw Error("Disconnected");
            await sock.sendMessage(jid, { text, linkPreview: null });
          };
          const result = await reply(
            m,
            (text) => deliver(m.jid, text),
            (text, kind) =>
              deliver(
                `${owner}@s.whatsapp.net`,
                `${kind ? kind.toUpperCase() : "Mimi inbox"} · from +${tester}\n${text}`,
              ),
          );
          console.log("Mimi:", result);
        })
        .catch((e) =>
          console.log(
            /^Provider HTTP \d{3}$/.test(e?.message)
              ? e.message + " · check key, model and quota."
              : "Message not processed; check connection or provider.",
          ),
        )
        .finally(() => {
          pending--;
        });
    }
  });
}
console.log(
  "Connecting linked device… No web server, notification listener or call handling.",
);
connect();
