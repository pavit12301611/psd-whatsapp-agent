"use strict";
const $ = (id) => document.getElementById(id),
  status = (t) => {
    $("state").textContent = t;
  };
const p = new URLSearchParams(location.hash.slice(1)),
  token = p.get("token"),
  model = p.get("model"),
  to = p.get("to");
history.replaceState(null, "", location.pathname); // Never put bearer tokens in HTTP requests or referrers.
const valid =
  token?.startsWith("auth_tokens/") &&
  /^[\w.-]{2,100}$/.test(model || "") &&
  /^[1-9]\d{6,14}$/.test(to || "");
if (!valid) {
  $("start").disabled = true;
  status("Open the private link from your WhatsApp chat to start.");
}
let mic,
  ctx,
  ws,
  proc,
  source,
  mute,
  live = false,
  asking = false,
  done = false,
  transcript = "",
  summary = "",
  next = 0,
  timer,
  timeout,
  players = new Set();
const send = (x) => {
  if (ws?.readyState === WebSocket.OPEN) ws.send(JSON.stringify(x));
};
function stopAudio() {
  mic?.getTracks().forEach((t) => t.stop());
  proc?.disconnect();
  source?.disconnect();
  mute?.disconnect();
  if (proc) proc.onaudioprocess = null;
}
function silence() {
  for (const a of players) {
    try {
      a.stop();
    } catch {}
  }
  players.clear();
  next = 0;
}
function finish(message) {
  if (done) return;
  done = true;
  clearTimeout(timeout);
  clearTimeout(timer);
  stopAudio();
  silence();
  ws?.close();
  ctx?.close().catch(() => {});
  $("start").hidden = true;
  $("end").hidden = true;
  $("result").hidden = !live;
  $("note").value = (summary.trim() || transcript.trim() || "").slice(0, 2800);
  status(message);
}
window.addEventListener("pagehide", () => finish("Call ended."));
function makeBrief() {
  if (done || asking || !live) return;
  asking = true;
  stopAudio();
  silence();
  $("end").disabled = true;
  status("Mimi is preparing a brief…");
  send({ realtimeInput: { audioStreamEnd: true } });
  send({
    realtimeInput: {
      text: "The call is over. Now give only a concise factual written-style brief: caller name, purpose, urgency, and requested next action. Mark missing information unknown.",
    },
  });
  timeout = setTimeout(
    () => finish("Review or edit the notes below before sharing."),
    12000,
  );
}
$("end").onclick = makeBrief;
$("share").onclick = () => {
  let note = $("note").value.trim();
  if (!note) return status("Add a note before sharing.");
  location.href =
    "https://wa.me/" +
    to +
    "?text=" +
    encodeURIComponent("Mimi call brief:\n" + note);
};
function play(part) {
  if (!part?.data || asking) return;
  const bytes = Uint8Array.from(atob(part.data), (c) => c.charCodeAt(0)),
    pcm = new DataView(bytes.buffer),
    rate = Number((part.mimeType || "").match(/rate=(\d+)/)?.[1] || 24000);
  const buf = ctx.createBuffer(1, bytes.length >> 1, rate),
    f = buf.getChannelData(0);
  for (let i = 0; i < f.length; i++) f[i] = pcm.getInt16(i * 2, true) / 32768;
  const a = ctx.createBufferSource();
  a.buffer = buf;
  a.connect(ctx.destination);
  next = Math.max(ctx.currentTime + 0.02, next);
  a.start(next);
  next += buf.duration;
  players.add(a);
  a.onended = () => players.delete(a);
}
function stream() {
  source = ctx.createMediaStreamSource(mic);
  proc = ctx.createScriptProcessor(4096, 1, 1);
  mute = ctx.createGain();
  mute.gain.value = 0;
  source.connect(proc);
  proc.connect(mute);
  mute.connect(ctx.destination);
  proc.onaudioprocess = (e) => {
    if (asking || ws.bufferedAmount > 65536) return;
    const f = e.inputBuffer.getChannelData(0),
      ratio = ctx.sampleRate / 16000,
      bytes = new Uint8Array(2 * Math.floor(f.length / ratio)),
      view = new DataView(bytes.buffer);
    for (let i = 0; i < bytes.length / 2; i++) {
      let lo = Math.floor(i * ratio),
        hi = Math.max(lo + 1, Math.floor((i + 1) * ratio)),
        sum = 0;
      for (let j = lo; j < hi; j++) sum += f[j];
      view.setInt16(
        i * 2,
        Math.max(-1, Math.min(1, sum / (hi - lo))) * 32767,
        true,
      );
    }
    send({
      realtimeInput: {
        audio: {
          mimeType: "audio/pcm;rate=16000",
          data: btoa(String.fromCharCode(...bytes)),
        },
      },
    });
  };
}
$("start").onclick = async () => {
  if (!valid || done) return;
  $("start").disabled = true;
  status("Connecting your microphone…");
  try {
    ctx = new AudioContext();
    await ctx.resume();
    mic = await navigator.mediaDevices.getUserMedia({
      audio: { echoCancellation: true, noiseSuppression: true },
    });
    if (done) {
      stopAudio();
      return;
    }
    ws = new WebSocket(
      "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContentConstrained?access_token=" +
        encodeURIComponent(token),
    );
    ws.binaryType = "arraybuffer";
    timeout = setTimeout(
      () => finish("Connection timed out. Ask for a fresh link."),
      18000,
    );
    ws.onopen = () => send({ setup: { model: "models/" + model } });
    ws.onerror = () =>
      finish(
        "Could not connect. Check internet access and ask for a fresh invite.",
      );
    ws.onclose = () =>
      finish(
        live
          ? "Call ended. Review your notes below."
          : "Invite expired, already used or unavailable. Ask for a fresh invite.",
      );
    ws.onmessage = (e) => {
      if (done) return;
      try {
        const msg = JSON.parse(
          typeof e.data === "string"
            ? e.data
            : new TextDecoder().decode(e.data),
        );
        if (msg.setupComplete && !live) {
          clearTimeout(timeout);
          live = true;
          $("start").hidden = true;
          $("end").hidden = false;
          status("Connected · Microphone on");
          stream();
          send({
            realtimeInput: {
              text: "Please greet the caller and ask how you can help.",
            },
          });
          timer = setTimeout(makeBrief, 4 * 60 * 1000);
        }
        const c = msg.serverContent;
        if (!c) return;
        if (c.interrupted) silence();
        if (c.inputTranscription?.text)
          transcript = (transcript + c.inputTranscription.text).slice(-5000);
        if (asking && c.outputTranscription?.text)
          summary = (summary + c.outputTranscription.text).slice(0, 2800);
        for (const part of c.modelTurn?.parts || [])
          if (part.inlineData) play(part.inlineData);
        if (asking && summary && c.turnComplete)
          finish("Call complete. Review your brief before sending.");
      } catch {
        finish("Audio connection interrupted.");
      }
    };
  } catch {
    finish(
      "Microphone unavailable. Open the link in Chrome and allow microphone access.",
    );
  }
};
