"use strict";
const byId = (id) => document.getElementById(id);
const vibes = {
  off: [
    "Available",
    "Pause replies",
    "Just hanging out.",
    "You’re available, so Mimi is taking a little break.",
  ],
  busy: [
    "Busy",
    "Focus time",
    "Focus time.",
    "Mimi has her coffee. Your Busy greeting is selected.",
  ],
  sleeping: [
    "Sleeping",
    "Rest easy",
    "Dreaming away.",
    "Mimi has her night scarf on. Your sleep greeting is selected.",
  ],
  driving: [
    "Driving",
    "On the road",
    "On the move.",
    "Eyes on the road. Your driving greeting is selected.",
  ],
};
const tools = [
  [
    "studio",
    "notebook-text",
    "Reply studio",
    "Preview your greeting & language",
  ],
  [
    "shield",
    "shield-check",
    "Call Shield",
    "Manage declines & priority callers",
  ],
  [
    "inbox",
    "notebook-text",
    "Follow-up inbox",
    "Review callbacks & urgent notes",
  ],
  ["settings", "settings-2", "Account & AI", "Manage your number & provider"],
];
const icon = (name) =>
  `<svg aria-hidden="true" class="icon"><use href="#i-${name}"></use></svg>`;
document
  .querySelectorAll("[data-icon]")
  .forEach((el) => (el.outerHTML = icon(el.dataset.icon)));
byId("choices").innerHTML = Object.entries(vibes)
  .map(
    ([key, [name, desc]]) =>
      `<button class="tile" data-act="${key}"><img alt="" src="art/${key}.svg"><strong>${name}</strong><small>${desc}</small></button>`,
  )
  .join("");
byId("tools").innerHTML = tools
  .map(
    ([key, symbol, name, desc]) =>
      `<button class="tool" data-act="${key}"><span class="ico">${icon(symbol)}</span><span><strong>${name}</strong><small>${desc}</small></span><span class="chev">${icon("chevron-right")}</span></button>`,
  )
  .join("");
let previous;
function renderDesk(s) {
  const mode = s.mode in vibes ? s.mode : "off";
  if (previous !== mode) {
    byId("headline").textContent = vibes[mode][2];
    byId("mood").textContent = vibes[mode][3];
    const art = byId("island");
    byId("frames").srcset = `art/loop-${mode}.svg`;
    byId("petLine").textContent = "♡ Pet Mimi";
    art.src = `art/island-${mode}.svg`;
    if (previous !== undefined) {
      art.classList.remove("react");
      void art.offsetWidth;
      art.classList.add("react");
    }
    for (const tile of document.querySelectorAll(".tile"))
      tile.setAttribute("aria-pressed", String(tile.dataset.act === mode));
    previous = mode;
  }
  byId("network").textContent = byId("network").dataset.state = s.network;
  byId("chip").textContent =
    mode === "off"
      ? "●   Replies paused"
      : s.network === "Linked"
        ? `●   ${vibes[mode][0]} replies on`
        : `●   ${vibes[mode][0]} saved`;
  for (const key of ["reason", "timer"]) {
    byId(key).textContent = s[key] || "";
    byId(key).hidden = !s[key];
  }
  byId("activity").textContent = s.activity || "Engine offline";
  byId("scope").textContent =
    `Audience: ${s.tester ? "test number only" : "direct chats"} · ${s.notes || 0} notes this session`;
  byId("pair").textContent = s.code || "";
  byId("forget").hidden = !s.canForget;
  byId("link").textContent =
    s.network === "Offline" ? "Connect WhatsApp" : "Stop chat engine";
}
// Petting is a visual-only interaction: never call the engine or change availability.
byId("pet").addEventListener("click", () => {
  byId("petLine").textContent = {
    off: "♥ Mimi hops for you!", busy: "♥ Coffee break!", sleeping: "☾ Sweet dreams…", driving: "♥ Beep beep!"
  }[previous];
  const art = byId("island");
  art.classList.remove("react"); void art.offsetWidth; art.classList.add("react");
});
const onPhone = location.hostname === "pocket.mimi.invalid";
renderDesk({ mode: "off", network: "Offline" });
document.addEventListener("click", (e) => {
  const action = e.target.closest("[data-act]")?.dataset.act;
  if (!action) return;
  if (onPhone) {
    location.href = `/action/${action}`;
    return;
  }
  // Browser preview never accesses credentials or impersonates a linked session.
  if (action in vibes) {
    renderDesk({ mode: action, network: "Offline" });
    return;
  }
  const t = tools.find((item) => item[0] === action);
  byId("dt").textContent = t?.[2] || "Connect WhatsApp";
  byId("dd").textContent =
    "This is the pocket desk design preview. Use the Android app for account settings, tools and WhatsApp linking.";
  byId("demo").showModal();
});
byId("demo").querySelector("button").onclick = () => byId("demo").close();
function markSection() {
  const anchor = location.hash || "#desk";
  document.querySelectorAll("nav a").forEach((a) => {
    if (a.hash === anchor) a.setAttribute("aria-current", "location");
    else a.removeAttribute("aria-current");
  });
}
addEventListener("hashchange", markSection);
markSection();
