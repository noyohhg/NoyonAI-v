# NEXORA private Telegram worker

This service is a personal-account MTProto worker. It supports secure-token-protected Telegram phone-code login, session creation, Gemini reply generation, and allowlisted chat IDs.

## Railway variables
- NEXORA_WORKER_TOKEN
- TELEGRAM_API_ID
- TELEGRAM_API_HASH
- TELEGRAM_SESSION (set after login)
- GEMINI_API_KEY
- ALLOWED_CHAT_IDS (comma-separated Telegram chat IDs)

Health: GET /health

Protected API:
- POST /api/login/send-code { "phone": "+..." }
- POST /api/login/verify-code { "code": "..." } (add "password" only if Telegram 2-step verification is enabled)
- GET /api/me
- POST /api/chats { "chatIds": ["-100..."] }

Never share the session string, API hash, worker token, or login code. Do not enable automation until the NEXORA dashboard is connected to this worker and the session and allowlist are securely persisted.
