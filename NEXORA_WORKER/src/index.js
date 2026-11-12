import express from 'express';
import { readFile, writeFile } from 'node:fs/promises';
import { TelegramClient, Api } from 'telegram';
import { StringSession } from 'telegram/sessions/index.js';
import { NewMessage } from 'telegram/events/index.js';

const app = express();
app.use(express.json({ limit: '64kb' }));

const PORT = Number(process.env.PORT || 3000);
const TOKEN = process.env.NEXORA_WORKER_TOKEN || '';
const SESSION_FILE = '/data/telegram-session.txt';

let apiId = 0;
let apiHash = '';
let geminiKey = '';
let client = null;
let connected = false;
let pendingPhone = '';
let pendingPhoneCodeHash = '';
let allowedChats = [];
let preferences = {
  enabled: false,
  language: 'Auto detect',
  tone: 'Friendly',
  prompt: 'Reply naturally and briefly.',
  delay: 2,
};
let handlerAttached = false;

function protect(req, res, next) {
  const supplied = (req.get('authorization') || '').replace(/^Bearer\s+/i, '');
  if (!TOKEN || supplied !== TOKEN) {
    return res.status(401).json({ error: 'Unauthorized' });
  }
  next();
}

app.get('/health', (_req, res) => {
  res.json({ ok: true, telegramConnected: connected });
});
app.use('/api', protect);

async function loadSession() {
  try {
    return (await readFile(SESSION_FILE, 'utf8')).trim();
  } catch {
    return '';
  }
}

async function persistSession() {
  if (!client) throw new Error('Telegram client is not initialized');
  await writeFile(SESSION_FILE, client.session.save(), { encoding: 'utf8', mode: 0o600 });
}

async function startTelegram(session = '') {
  if (!apiId || !apiHash) throw new Error('Telegram API credentials are not configured');
  if (client?.connected) {
    try { await client.disconnect(); } catch {}
  }
  client = new TelegramClient(new StringSession(session), apiId, apiHash, {
    connectionRetries: 5,
  });
  await client.connect();
  connected = await client.isUserAuthorized();
  if (connected) attachReplyHandler();
  return connected;
}

async function replyWithGemini(message) {
  if (!geminiKey) throw new Error('Gemini API key is not configured');
  const prompt = [
    preferences.prompt || 'Reply naturally and briefly.',
    'Tone: ' + (preferences.tone || 'Friendly'),
    'Language: ' + (preferences.language || 'Auto detect'),
    'Incoming Telegram message:',
    message,
    'Write only the suggested reply.',
  ].join('\\n');
  const response = await fetch(
    'https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=' +
      encodeURIComponent(geminiKey),
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        contents: [{ parts: [{ text: prompt }] }],
        generationConfig: { temperature: 0.7, maxOutputTokens: 300 },
      }),
    }
  );
  if (!response.ok) throw new Error('Gemini API request failed: ' + response.status);
  const data = await response.json();
  return data.candidates?.[0]?.content?.parts?.map(part => part.text || '').join('').trim() || '';
}

function attachReplyHandler() {
  if (!client || handlerAttached) return;
  handlerAttached = true;
  client.addEventHandler(async event => {
    const message = event.message;
    if (!connected || !preferences.enabled || !message?.message || message.out) return;
    try {
      const entity = await client.getEntity(message.peerId);
      const id = String(message.chatId || '');
      const username = entity?.username ? '@' + entity.username.toLowerCase() : '';
      const title = String(entity?.title || entity?.firstName || '').toLowerCase();
      const allowed = allowedChats.some(value => {
        const item = String(value).trim().toLowerCase();
        return item && (item === id || item === username || item === title);
      });
      if (!allowed) return;
      if (preferences.delay > 0) {
        await new Promise(resolve => setTimeout(resolve, Math.min(15, preferences.delay) * 1000));
      }
      const answer = await replyWithGemini(message.message);
      if (answer) await message.reply({ message: answer });
    } catch (err) {
      console.error('Auto reply failed:', err?.message || 'unknown error');
    }
  }, new NewMessage({ incoming: true }));
}

app.post('/api/configure', async (req, res) => {
  try {
    const body = req.body || {};
    const nextApiId = Number(body.apiId || 0);
    const nextApiHash = String(body.apiHash || '');
    const nextGeminiKey = String(body.geminiKey || '');
    if (!nextApiId || !nextApiHash || !nextGeminiKey) {
      return res.status(400).json({ error: 'Required worker configuration is missing' });
    }
    apiId = nextApiId;
    apiHash = nextApiHash;
    geminiKey = nextGeminiKey;
    allowedChats = Array.isArray(body.chats) ? body.chats.map(String) : [];
    preferences = {
      enabled: Boolean(body.settings?.enabled),
      language: String(body.settings?.language || 'Auto detect'),
      tone: String(body.settings?.tone || 'Friendly'),
      prompt: String(body.settings?.prompt || 'Reply naturally and briefly.').slice(0, 2000),
      delay: Math.max(0, Math.min(15, Number(body.settings?.delay) || 0)),
    };
    const session = await loadSession();
    if (session && !connected) {
      await startTelegram(session);
    }
    res.json({ ok: true, telegramConnected: connected });
  } catch (err) {
    res.status(503).json({ error: err?.message || 'Worker configuration failed' });
  }
});

app.post('/api/login/send-code', async (req, res) => {
  try {
    const phone = String(req.body?.phone || '').trim();
    if (!phone) return res.status(400).json({ error: 'Phone number is required' });
    pendingPhone = phone;
    if (!client || !client.connected) {
      await startTelegram('');
    }
    const sent = await client.sendCode({ apiId, apiHash }, phone);
    pendingPhoneCodeHash = sent.phoneCodeHash;
    res.json({ ok: true, message: 'Telegram login code sent' });
  } catch (err) {
    res.status(400).json({ error: err?.message || 'Could not send Telegram login code' });
  }
});

app.post('/api/login/verify-code', async (req, res) => {
  try {
    const code = String(req.body?.code || '').trim();
    const password = String(req.body?.password || '');
    if (!client || !pendingPhone || !pendingPhoneCodeHash || !code) {
      return res.status(400).json({ error: 'Send a login code first' });
    }
    try {
      await client.invoke(new Api.auth.SignIn({
        phoneNumber: pendingPhone,
        phoneCodeHash: pendingPhoneCodeHash,
        phoneCode: code,
      }));
    } catch (err) {
      if (String(err?.errorMessage || err?.message).includes('SESSION_PASSWORD_NEEDED')) {
        if (!password) return res.status(202).json({ needs2FA: true });
        await client.signInWithPassword(
          { apiId, apiHash },
          { password: async () => password, onError: error => { throw error; } }
        );
      } else {
        throw err;
      }
    }
    connected = await client.isUserAuthorized();
    if (!connected) return res.status(401).json({ error: 'Telegram authorization did not complete' });
    await persistSession();
    attachReplyHandler();
    pendingPhone = '';
    pendingPhoneCodeHash = '';
    res.json({ ok: true, connected: true });
  } catch (err) {
    res.status(400).json({ error: err?.message || 'Telegram verification failed' });
  }
});

app.get('/api/status', async (_req, res) => {
  try {
    if (!client) {
      const session = await loadSession();
      if (session && apiId && apiHash) await startTelegram(session);
    }
    if (!connected) return res.json({ connected: false });
    const me = await client.getMe();
    res.json({ connected: true, username: me.username || null, id: String(me.id) });
  } catch {
    connected = false;
    res.json({ connected: false });
  }
});

async function boot() {
  const session = await loadSession();
  if (session && apiId && apiHash) await startTelegram(session);
  app.listen(PORT, '0.0.0.0', () => console.log('NEXORA Telegram Worker listening on ' + PORT));
}
boot().catch(err => {
  console.error('Worker startup failed:', err?.message || err);
  process.exit(1);
});
