const instruction =
  "You are Mimi, an AI text receptionist in a private WhatsApp chat. Reply briefly and warmly in the sender’s language. Listen, clarify the request and urgency, and give a short recap when useful. Do not pretend to be the account owner, claim to have performed tasks, ask for secrets or promise a callback.";
export async function ai(provider, key, turns, request = fetch) {
  const gem = provider === "gemini";
  if (!gem && provider !== "groq") throw Error("Unknown provider");
  const model = gem
    ? process.env.MIMI_GEMINI_MODEL || "gemini-2.5-flash"
    : process.env.MIMI_GROQ_MODEL || "llama-3.3-70b-versatile";
  const url = gem
    ? `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent`
    : "https://api.groq.com/openai/v1/chat/completions";
  const body = gem
    ? {
        systemInstruction: { parts: [{ text: instruction }] },
        contents: turns.map((t) => ({
          role: t.role === "assistant" ? "model" : "user",
          parts: [{ text: t.content }],
        })),
      }
    : {
        model,
        max_tokens: 350,
        messages: [{ role: "system", content: instruction }, ...turns],
      };
  const res = await request(url, {
    method: "POST",
    redirect: "error",
    signal: AbortSignal.timeout(20000),
    headers: {
      "Content-Type": "application/json",
      ...(gem ? { "x-goog-api-key": key } : { Authorization: "Bearer " + key }),
    },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw Error(`Provider HTTP ${res.status}`);
  const data = await res.json();
  const text = gem
    ? data.candidates?.[0]?.content?.parts
        ?.filter((p) => !p.thought)
        .map((p) => p.text || "")
        .join("")
    : data.choices?.[0]?.message?.content;
  if (typeof text !== "string" || !text.trim())
    throw Error("Provider returned no text");
  return text.trim().slice(0, 1500);
}
