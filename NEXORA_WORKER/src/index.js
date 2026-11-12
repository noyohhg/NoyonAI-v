import express from "express";
import { TelegramClient } from "telegram";
import { StringSession } from "telegram/sessions/index.js";
import { NewMessage } from "telegram/events/index.js";
import { Api } from "telegram";

const app = express();
app.use(express.json({ limit: "32kb" }));
const PORT = process.env.PORT || 3000;
const TOKEN = process.env.NEXORA_WORKER_TOKEN || "";
const API_ID = Number(process.env.TELEGRAM_API_ID || 0);
const API_HASH = process.env.TELEGRAM_API_HASH || "";
const SESSION = process.env.TELEGRAM_SESSION || "";
const GEMINI_KEY = process.env.GEMINI_API_KEY || "";
const ALLOWED = new Set((process.env.ALLOWED_CHAT_IDS || "").split(",").map(s => s.trim()).filter(Boolean));
let client;
let connected = false;
let pendingPhone = "";
let pendingHash = "";

function protect(req, res, next) {
  const token = (req.get("authorization") || "").replace(/^Bearer\s+/i, "");
  if (!TOKEN || token !== TOKEN) return res.status(401).json({ error: "Unauthorized" });
  next();
}
app.get("/health", (_req, res) => res.json({ ok: true, telegramConnected: connected }));
app.use("/api", protect);

app.post("/api/login/send-code", async (req, res) => {
  try {
    if (!API_ID || !API_HASH) return res.status(503).json({ error: "Telegram API credentials are not configured" });
    pendingPhone = String(req.body.phone || "").trim();
    if (!pendingPhone) return res.status(400).json({ error: "Phone number required in international format" });
    client = new TelegramClient(new StringSession(""), API_ID, API_HASH, { connectionRetries: 5 });
    await client.connect();
    const sent = await client.invoke(new Api.auth.SendCode({ phoneNumber: pendingPhone, apiId: API_ID, apiHash: API_HASH, settings: new Api.CodeSettings({}) }));
    pendingHash = sent.phoneCodeHash;
    res.json({ ok: true, message: "Code sent to your Telegram account." });
  } catch (e) { res.status(400).json({ error: e.message }); }
});

app.post("/api/login/verify-code", async (req, res) => {
  try {
    const code = String(req.body.code || "").trim();
    if (!client || !pendingHash || !code) return res.status(400).json({ error: "Start login first and enter the Telegram code." });
    try {
      await client.invoke(new Api.auth.SignIn({ phoneNumber: pendingPhone, phoneCodeHash: pendingHash, phoneCode: code }));
    } catch (e) {
      if (String(e.errorMessage || e.message).includes("SESSION_PASSWORD_NEEDED")) {
        const password = String(req.body.password || "");
        if (!password) return res.status(202).json({ needs2FA: true });
        await client.signInWithPassword({ apiId: API_ID, apiHash: API_HASH }, { password: async () => password, onError: err => { throw err; } });
      } else throw e;
    }
    connected = true;
    const sessionString = client.session.save();
    res.json({ ok: true, sessionString, warning: "Save this session securely. Never share it." });
    attachReplyHandler();
  } catch (e) { res.status(400).json({ error: e.message }); }
});

app.get("/api/me", async (_req, res) => {
  try {
    const me = await client.getMe();
    res.json({ connected: true, id: String(me.id), username: me.username || null });
  } catch { res.json({ connected: false }); }
});

app.post("/api/chats", (req, res) => {
  if (!Array.isArray(req.body.chatIds)) return res.status(400).json({ error: "chatIds must be an array" });
  ALLOWED.clear();
  req.body.chatIds.map(String).forEach(id => ALLOWED.add(id));
  res.json({ ok: true, selected: ALLOWED.size, note: "Set ALLOWED_CHAT_IDS in Railway variables to persist after restart." });
});

async function makeReply(text) {
  if (!GEMINI_KEY) throw new Error("Gemini API key is missing");
  const response = await fetch("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + encodeURIComponent(GEMINI_KEY), {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ contents: [{ parts: [{ text: "Write a natural, brief reply to this incoming Telegram message. Do not invent facts.\n\n" + text }] }], generationConfig: { maxOutputTokens: 250, temperature: 0.7 } })
  });
  if (!response.ok) throw new Error("Gemini request failed: " + response.status);
  const data = await response.json();
  return data.candidates?.[0]?.content?.parts?.map(p => p.text || "").join("").trim() || "";
}

function attachReplyHandler() {
  client.addEventHandler(async event => {
    const message = event.message;
    if (!message?.message || message.out || !ALLOWED.has(String(message.chatId))) return;
    try {
      const answer = await makeReply(message.message);
      if (answer) await message.reply({ message: answer });
    } catch (e) { console.error("Reply error:", e.message); }
  }, new NewMessage({ incoming: true }));
}

async function boot() {
  if (API_ID && API_HASH && SESSION) {
    client = new TelegramClient(new StringSession(SESSION), API_ID, API_HASH, { connectionRetries: 5 });
    await client.connect();
    connected = await client.isUserAuthorized();
    if (connected) attachReplyHandler();
  }
  app.listen(PORT, "0.0.0.0", () => console.log("NEXORA Worker listening on " + PORT));
}
boot().catch(err => { console.error("Startup failed:", err); process.exit(1); });
