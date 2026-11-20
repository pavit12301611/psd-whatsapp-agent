"use strict";
const $ = (id) => document.getElementById(id),
  icon = (n) =>
    `<svg class="ic" aria-hidden="true"><use href="#i-${n}"></use></svg>`,
  esc = (t) =>
    String(t ?? "").replace(
      /[&<>"']/g,
      (c) =>
        ({
          "&": "&amp;",
          "<": "&lt;",
          ">": "&gt;",
          '"': "&quot;",
          "'": "&#39;",
        })[c],
    );
const modes = [
  ["Available", "Unsaved numbers", "sun", "#95721c", "#fff3c6"],
  ["Busy", "In focus time", "briefcase-business", "#a65932", "#ffe4d4"],
  ["Sleeping", "Resting quietly", "moon-star", "#7052af", "#ece2ff"],
  ["Driving", "On the road", "car", "#447770", "#ddf1ea"],
];
const tabs = [
  ["Overview", "layout-dashboard"],
  ["Call briefs", "notebook-text"],
  ["Connections", "plug"],
];
const native = window.MimiDesk;
let tab = 0,
  s;
const demo = {
  mode: "Available",
  enabled: false,
  access: false,
  contacts: false,
  ready: false,
  gemini: false,
  groq: false,
  briefs: [
    {
      from: "Courier enquiry",
      text: "A delivery partner wanted directions to the front entrance. Requested action: confirm the drop-off location.",
      time: 0,
    },
    {
      from: "Project callback",
      text: "A new client asked for a callback about a design project. Preferred time: after 4 pm.",
      time: 0,
    },
  ],
  status: "No invites sent yet.",
};
function go(t) {
  tab = t;
  render();
}
function modal(t, b) {
  $("modal").innerHTML = `<h2>${esc(t)}</h2><pre>${esc(b)}</pre>`;
  $("dialog").showModal();
}
function act(k, arg) {
  if (k === "refresh") return render();
  if (k === "mode") {
    if (native) native.mode(arg);
    else localStorage.setItem("mode", arg);
    render();
    return;
  }
  if (k === "toggle") {
    if (native) {
      native.toggle();
      render();
    } else {
      demo.enabled = !demo.enabled;
      render();
    }
    return;
  }
  if (
    native &&
    ["setup", "access", "contacts", "polish", "clear"].includes(k)
  ) {
    native.action(k, arg ?? "");
    return;
  }
  modal(
    "Preview workspace",
    "This is the browser preview. Install the APK on your phone to configure credentials, grant Android permissions and receive live invites.",
  );
}
function row(ico, title, sub, click, color = "#7356b6", bg = "#eeebf9") {
  return `<button class="row" data-c="${esc(click)}"><span class="ico" style="--ink:${color};--bg:${bg}">${icon(ico)}</span><span><strong>${esc(title)}</strong><small>${esc(sub)}</small></span><span class="tail">${icon("chevron-right")}</span></button>`;
}
function render() {
  s = native
    ? JSON.parse(native.state())
    : { ...demo, mode: localStorage.getItem("mode") || "Available" };
  $("nav").innerHTML = tabs
    .map(
      ([n, i], idx) =>
        `<button class="nav ${idx === tab ? "active" : ""}" data-tab="${idx}">${icon(i)}${n}</button>`,
    )
    .join("");
  $("crumb").textContent = tabs[tab][0];
  $("date").textContent = new Date()
    .toLocaleDateString("en-GB", {
      weekday: "short",
      day: "numeric",
      month: "short",
    })
    .toUpperCase();
  const title = (eye, t, p) =>
    `<div class="intro"><section><div class="eyebrow">${eye}</div><h1>${t}</h1><p>${p}</p></section><span class="tag">${native ? "ON DEVICE" : "DEMO DESK"}</span></div>`;
  const list = (all) =>
    s.briefs.length
      ? s.briefs
          .slice(0, all ? 40 : 2)
          .map((b, i) =>
            row(
              i % 2 ? "notebook-text" : "phone-incoming",
              b.from,
              b.time
                ? new Date(b.time).toLocaleString()
                : "Sample brief · Preview",
              `brief:${i}`,
              i % 2 ? "#916030" : "#5f54a4",
              i % 2 ? "#fff0d5" : "#eee9fa",
            ),
          )
          .join("")
      : '<div class="empty">No briefs yet. A caller’s shared Mimi brief appears here when its notification is visible.</div>';
  if (tab === 0) {
    $("content").innerHTML =
      title(
        "YOUR VOICE WORKSPACE",
        "Voice desk",
        "A little space to manage your AI receptionist.",
      ) +
      `<div class="hero"><section><div class="hero-tag"><i class="dot"></i> MIMI / VOICE AGENT</div><h2>${s.enabled ? "Mimi is on duty." : "Meet Mimi, your copilot."}</h2><p>${s.enabled ? "Listening for eligible message notifications." : "A friendly voice for the calls you can’t take."}</p><button class="primary" data-c="toggle">${icon("power")}${s.enabled ? "Pause invites" : "Enable invites"}</button></section><img src="mimi.svg" alt="Mimi, a little lavender assistant wearing lime headphones"></div><div class="sectionhead"><h2>Availability</h2><span class="micro">SELECT A MODE</span></div><div class="modes">${modes.map(([m, d, i, c, b]) => `<button class="mode ${s.mode === m ? "on" : ""}" data-mode="${m}" aria-pressed="${s.mode === m}"><i class="radio"></i><span class="ico" style="--ink:${c};--bg:${b}">${icon(i)}</span><strong>${m}</strong><small>${d}</small></button>`).join("")}</div><div class="rule">${icon("shield-check")}${s.mode === "Available" ? "Only identifiable, unsaved numbers get an invite." : "Direct messages from saved and unsaved contacts can trigger an invite."}</div><div class="sectionhead"><h2>Recent briefs</h2><button class="link" data-tab="1">View all ${icon("arrow-up-right")}</button></div><div class="panel">${list(false)}</div>`;
  }
  if (tab === 1)
    $("content").innerHTML =
      title(
        "CONVERSATION NOTES",
        "Call briefs",
        "Read the notes callers chose to send back.",
      ) +
      `<div class="sectionhead"><h2>Your inbox</h2><span class="micro">${s.briefs.length} BRIEFS</span></div><div class="panel">${list(true)}</div><div class="rule">${icon("shield-check")}Stored on this device. No audio recordings.</div>`;
  if (tab === 2)
    $("content").innerHTML =
      title(
        "DEVICE SETUP",
        "Connections",
        "Your keys, permissions and invite settings.",
      ) +
      `<div class="sectionhead"><h2>Service configuration</h2></div><div class="panel">${row("key-round", "Provider keys & invite page", `${s.gemini ? "Gemini configured" : "Gemini required"} · ${s.groq ? "Groq configured" : "Groq optional"}`, "setup")}${row("radio", "Notification access", s.access ? (s.bound ? "Granted · Listener connected" : "Granted · Listener not connected") : "Allow Mimi to reply to WhatsApp Business messages", "access")}${row("users", "Contacts access", s.contacts ? "Granted" : "Needed for unsaved-number detection", "contacts")}</div><div class="sectionhead"><h2>Device activity</h2><button class="link" data-c="refresh">Refresh</button></div><div class="panel"><div class="empty"><strong>Last WhatsApp Business notification</strong><p>${s.lastSeen ? esc(new Date(s.lastSeen).toLocaleString()) : "None seen yet"}</p>${esc(s.status)}</div></div><div class="sectionhead"><h2>Privacy</h2></div><div class="panel">${row("notebook-text", "Clear saved briefs", "Erase the inbox on this phone", "clear")}</div>`;
}
document.addEventListener("click", (e) => {
  const b = e.target.closest("button");
  if (!b) return;
  if (b.id === "close") return $("dialog").close();
  if (b.dataset.tab !== undefined) return go(+b.dataset.tab);
  if (b.dataset.mode) return act("mode", b.dataset.mode);
  let c = b.dataset.c;
  if (!c) return;
  if (c.startsWith("brief:")) {
    let i = +c.split(":")[1],
      v = s.briefs[i];
    modal(v.from, v.text);
    if (native && s.groq) {
      const x = document.createElement("button");
      x.textContent = "Refine with Groq";
      x.onclick = () => {
        $("dialog").close();
        act("polish", String(v.time));
      };
      $("modal").append(x);
    }
    return;
  }
  act(c);
});
render();
window.refreshDesk = render;
