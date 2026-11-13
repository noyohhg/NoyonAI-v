import express from 'express';
import { readFile, writeFile, mkdir, readdir } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import path from 'node:path';
import { TelegramClient, Api } from 'telegram';
import { StringSession } from 'telegram/sessions/index.js';
import { NewMessage } from 'telegram/events/index.js';

const app = express();
app.use(express.json({ limit: '64kb' }));

const PORT = Number(process.env.PORT || 3000);
const TOKEN = process.env.NEXORA_WORKER_TOKEN || '';
const DATA_DIR = '/data/telegram-users';
const accounts = new Map();
let apiId = Number(process.env.TELEGRAM_API_ID || 0);
let apiHash = process.env.TELEGRAM_API_HASH || '';

const defaultPreferences = {
  enabled: false,
  language: 'Auto detect',
  tone: 'Friendly',
  prompt: 'Reply naturally and briefly.',
  delay: 2,
};

function accountKey(userId) {
  return createHash('sha256').update(String(userId)).digest('hex');
}

function accountFile(userId) {
  return path.join(DATA_DIR, accountKey(userId) + '.json');
}

function getAccount(userId) {
  if (!userId || typeof userId !== 'string' || userId.length > 256) {
    throw new Error('Authenticated dashboard account is required');
  }
  let state = accounts.get(userId);
  if (!state) {
    state = {
      userId,
      geminiKey: '',
      allowedChats: [],
      preferences: { ...defaultPreferences },
      session: '',
      client: null,
      connected: false,
      pendingPhone: '',
      pendingPhoneCodeHash: '',
      handlerAttached: false,
      reconnecting: false,
      inFlightMessages: new Set(),
      repliedMessages: new Set(),
    };
    accounts.set(userId, state);
  }
  return state;
}

function getRequestAccount(req) {
  const id = String(req.body?.accountId || req.get('x-nexora-account-id') || '');
  return getAccount(id);
}

function protect(req, res, next) {
  const supplied = (req.get('authorization') || '').replace(/^Bearer\s+/i, '');
  if (!TOKEN || supplied !== TOKEN) {
    return res.status(401).json({ error: 'Unauthorized' });
  }
  next();
}

async function persistAccount(state) {
  await mkdir(DATA_DIR, { recursive: true, mode: 0o700 });
  const record = {
    userId: state.userId,
    geminiKey: state.geminiKey,
    allowedChats: state.allowedChats,
    preferences: state.preferences,
    session: state.client ? state.client.session.save() : state.session,
  };
  await writeFile(accountFile(state.userId), JSON.stringify(record), {
    encoding: 'utf8',
    mode: 0o600,
  });
}

async function startTelegram(state, session = state.session || '') {
  if (!apiId || !apiHash) throw new Error('Telegram API credentials are not configured');
  if (state.client?.connected) {
    try { await state.client.disconnect(); } catch {}
  }
  state.handlerAttached = false;
  state.client = new TelegramClient(new StringSession(session), apiId, apiHash, {
    connectionRetries: 5,
  });
  await state.client.connect();
  state.connected = await state.client.isUserAuthorized();
  state.session = session;
  if (state.connected) attachReplyHandler(state);
  return state.connected;
}

async function replyWithGemini(state, message) {
  if (!state.geminiKey) throw new Error('Gemini API key is not configured');
  const prompt = [
    state.preferences.prompt || 'Reply naturally and briefly.',
    'Tone: ' + (state.preferences.tone || 'Friendly'),
    'Language: ' + (state.preferences.language || 'Auto detect'),
    'Incoming Telegram message:',
    message,
    'Write only the suggested reply.',
  ].join('\n');
  const models = ['gemini-3.8-flash'];
  let lastError = '';
  for (const model of models) {
    const response = await fetch(
      'https://generativelanguage.googleapis.com/v1beta/models/' + model +
        ':generateContent?key=' + encodeURIComponent(state.geminiKey),
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        signal: AbortSignal.timeout(30000),
        body: JSON.stringify({
          contents: [{ parts: [{ text: prompt }] }],
          generationConfig: { temperature: 0.7, maxOutputTokens: 300 },
        }),
      }
    );
    const data = await response.json().catch(() => ({}));
    if (response.ok) {
      const answer = data.candidates?.[0]?.content?.parts
        ?.map(part => part.text || '').join('').trim() || '';
      if (answer) return answer;
      throw new Error('Gemini returned an empty reply');
    }
    lastError = data.error?.message || ('Gemini API request failed: ' + response.status);
    if (response.status !== 404 && response.status !== 400) break;
  }
  throw new Error(lastError || 'Gemini could not generate a reply');
}

function attachReplyHandler(state) {
  if (!state.client || state.handlerAttached) return;
  state.client.addEventHandler(async event => {
    const message = event.message;
    if (!message?.message || message.out) return;
    const chatId = String(message.chatId || '');
    const accountTag = accountKey(state.userId).slice(0, 10);
    const messageKey = chatId + ':' + String(message.id || '');
    console.log('Incoming Telegram message event; account:', accountTag, 'enabled:', Boolean(state.preferences.enabled), 'connected:', Boolean(state.connected), 'chat:', chatId || 'unknown', 'message:', message.id || 'unknown');
    if (!state.connected || !state.preferences.enabled) return;
    if (state.repliedMessages.has(messageKey) || state.inFlightMessages.has(messageKey)) {
      console.log('Duplicate Telegram message ignored; account:', accountTag, 'message:', message.id || 'unknown');
      return;
    }
    state.inFlightMessages.add(messageKey);
    try {
      const delay = Math.max(0, Math.min(15, Number(state.preferences.delay) || 0));
      if (delay) await new Promise(resolve => setTimeout(resolve, delay * 1000));
      console.log('Generating AI reply; account:', accountTag, 'message:', message.id || 'unknown');
      const answer = await replyWithGemini(state, message.message);
      if (!answer) throw new Error('AI returned an empty reply');
      console.log('AI reply generated; sending Telegram reply; account:', accountTag, 'message:', message.id || 'unknown');
      await message.reply({ message: answer });
      state.repliedMessages.add(messageKey);
      if (state.repliedMessages.size > 2000) state.repliedMessages.delete(state.repliedMessages.values().next().value);
      console.log('Telegram auto-reply sent; account:', accountTag, 'chat:', chatId || 'unknown', 'message:', message.id || 'unknown');
    } catch (err) {
      console.error('Auto reply failed for account', accountTag, 'message:', message.id || 'unknown', err?.message || 'unknown error');
    } finally {
      state.inFlightMessages.delete(messageKey);
    }
  }, new NewMessage({}));
  state.handlerAttached = true;
  console.log('Telegram incoming-message handler attached for account:', accountKey(state.userId).slice(0, 10));
}

app.get('/health', (_req, res) => {
  res.json({ ok: true, connectedAccounts: [...accounts.values()].filter(x => x.connected).length });
});
app.use('/api', protect);

app.post('/api/configure', async (req, res) => {
  try {
    const state = getRequestAccount(req);
    const body = req.body || {};
    const nextApiId = Number(body.apiId || 0);
    const nextApiHash = String(body.apiHash || '');
    const nextGeminiKey = String(body.geminiKey || '');
    if (!nextApiId || !nextApiHash || !nextGeminiKey) {
      return res.status(400).json({ error: 'Required worker configuration is missing' });
    }
    apiId = nextApiId;
    apiHash = nextApiHash;
    state.geminiKey = nextGeminiKey;
    state.allowedChats = Array.isArray(body.chats) ? body.chats.map(String) : [];
    state.preferences = {
      enabled: typeof body.settings?.enabled === 'boolean'
        ? body.settings.enabled
        : state.preferences.enabled,
      language: String(body.settings?.language || state.preferences.language || 'Auto detect'),
      tone: String(body.settings?.tone || state.preferences.tone || 'Friendly'),
      prompt: String(body.settings?.prompt || state.preferences.prompt || 'Reply naturally and briefly.').slice(0, 2000),
      delay: Math.max(0, Math.min(15, Number(body.settings?.delay ?? state.preferences.delay) || 0)),
    };
    if (state.session && (!state.connected || !state.client?.connected)) {
      await startTelegram(state, state.session);
    }
    await persistAccount(state);
    res.json({
      ok: true,
      telegramConnected: state.connected,
      autoReplyEnabled: Boolean(state.preferences.enabled),
    });
  } catch (err) {
    res.status(503).json({ error: err?.message || 'Worker configuration failed' });
  }
});

app.post('/api/login/send-code', async (req, res) => {
  try {
    const state = getRequestAccount(req);
    const phone = String(req.body?.phone || '').trim();
    if (!phone) return res.status(400).json({ error: 'Phone number is required' });
    if (!apiId || !apiHash) return res.status(503).json({ error: 'Telegram API credentials are not configured' });
    state.pendingPhone = phone;
    if (!state.client || !state.client.connected) await startTelegram(state, '');
    const sent = await state.client.sendCode({ apiId, apiHash }, phone);
    state.pendingPhoneCodeHash = sent.phoneCodeHash;
    res.json({ ok: true, message: 'Telegram login code sent' });
  } catch (err) {
    res.status(400).json({ error: err?.message || 'Could not send Telegram login code' });
  }
});

app.post('/api/login/verify-code', async (req, res) => {
  try {
    const state = getRequestAccount(req);
    const code = String(req.body?.code || '').trim();
    const password = String(req.body?.password || '');
    if (!state.client || !state.pendingPhone || !state.pendingPhoneCodeHash || !code) {
      return res.status(400).json({ error: 'Send a login code first' });
    }
    try {
      await state.client.invoke(new Api.auth.SignIn({
        phoneNumber: state.pendingPhone,
        phoneCodeHash: state.pendingPhoneCodeHash,
        phoneCode: code,
      }));
    } catch (err) {
      if (String(err?.errorMessage || err?.message).includes('SESSION_PASSWORD_NEEDED')) {
        if (!password) return res.status(202).json({ needs2FA: true });
        await state.client.signInWithPassword(
          { apiId, apiHash },
          { password: async () => password, onError: error => { throw error; } }
        );
      } else {
        throw err;
      }
    }
    state.connected = await state.client.isUserAuthorized();
    if (!state.connected) return res.status(401).json({ error: 'Telegram authorization did not complete' });
    state.session = state.client.session.save();
    await persistAccount(state);
    attachReplyHandler(state);
    state.pendingPhone = '';
    state.pendingPhoneCodeHash = '';
    const me = await state.client.getMe();
    res.json({ ok: true, connected: true, username: me.username || null, id: String(me.id) });
  } catch (err) {
    res.status(400).json({ error: err?.message || 'Telegram verification failed' });
  }
});

app.get('/api/status', async (req, res) => {
  try {
    const state = getRequestAccount(req);
    if (!state.client && state.session && apiId && apiHash) {
      await startTelegram(state, state.session);
    }
    if (!state.connected) {
      return res.json({
        connected: false,
        autoReplyEnabled: Boolean(state.preferences.enabled),
      });
    }
    const me = await state.client.getMe();
    res.json({
      connected: true,
      username: me.username || null,
      id: String(me.id),
      autoReplyEnabled: Boolean(state.preferences.enabled),
    });
  } catch (err) {
    res.json({ connected: false, error: err?.message || 'Telegram status unavailable' });
  }
});


app.post('/api/test-message', async (req, res) => {
  try {
    const state = getRequestAccount(req);
    if (!state.connected || !state.client?.connected) {
      return res.status(409).json({ error: 'Telegram account is not connected' });
    }
    const target = String(req.body?.target || '').trim();
    const message = String(req.body?.message || '').trim();
    if (!target || !message) {
      return res.status(400).json({ error: 'Telegram ID/username and test message are required' });
    }
    if (message.length > 4000) {
      return res.status(400).json({ error: 'Test message is too long (maximum 4000 characters)' });
    }
    const entity = await state.client.getEntity(target);
    await state.client.sendMessage(entity, { message });
    console.log('Telegram test message sent; account:', accountKey(state.userId).slice(0, 10));
    res.json({ ok: true, message: 'Test message sent' });
  } catch (err) {
    console.error('Telegram test message failed:', err?.message || err);
    res.status(400).json({ error: err?.message || 'Could not send Telegram test message' });
  }
});

async function boot() {
  await mkdir(DATA_DIR, { recursive: true, mode: 0o700 });
  const files = await readdir(DATA_DIR).catch(() => []);
  for (const file of files.filter(name => name.endsWith('.json'))) {
    try {
      const saved = JSON.parse(await readFile(path.join(DATA_DIR, file), 'utf8'));
      if (!saved.userId) continue;
      const state = getAccount(String(saved.userId));
      state.geminiKey = String(saved.geminiKey || '');
      state.allowedChats = Array.isArray(saved.allowedChats) ? saved.allowedChats : [];
      state.preferences = { ...defaultPreferences, ...(saved.preferences || {}) };
      state.session = String(saved.session || '');
      if (state.session && apiId && apiHash) {
        try {
          await startTelegram(state, state.session);
          console.log('Restored Telegram account:', accountKey(state.userId).slice(0, 10), 'connected:', state.connected);
        } catch (err) {
          console.error('Telegram account restore failed:', accountKey(state.userId).slice(0, 10), err?.message || err);
        }
      }
    } catch (err) {
      console.error('Could not restore account file:', file, err?.message || err);
    }
  }
  setInterval(async () => {
    for (const state of accounts.values()) {
      if (!state.session || state.reconnecting || state.client?.connected) continue;
      state.reconnecting = true;
      try {
        await startTelegram(state, state.session);
        if (state.connected) {
          await persistAccount(state);
          console.log('Telegram connection restored for account:', accountKey(state.userId).slice(0, 10));
        }
      } catch (err) {
        console.error('Telegram reconnect attempt failed for account:', accountKey(state.userId).slice(0, 10), err?.message || err);
      } finally {
        state.reconnecting = false;
      }
    }
  }, 30000);

  app.listen(PORT, '0.0.0.0', () => console.log('NEXORA multi-account Telegram Worker listening on ' + PORT));
}
boot().catch(err => {
  console.error('Worker startup failed:', err?.message || err);
  process.exit(1);
});
