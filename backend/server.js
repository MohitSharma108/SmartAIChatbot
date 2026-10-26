// SmartAIChatbot backend: Android app -> this server -> OpenAI Responses API.
// The OpenAI key lives only in backend/.env and never reaches the app.
import 'dotenv/config';
import express from 'express';
import rateLimit from 'express-rate-limit';
import OpenAI from 'openai';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

// ---------- configuration (fail fast) ----------
const { OPENAI_API_KEY, OPENAI_MODEL, OPENAI_BASE_URL } = process.env;
const PORT = Number(process.env.PORT) || 3000;
const __dirname = path.dirname(fileURLToPath(import.meta.url));

if (!OPENAI_API_KEY || !OPENAI_MODEL) {
  console.error('Missing configuration: set OPENAI_API_KEY and OPENAI_MODEL in backend/.env (copy .env.example).');
  process.exit(1);
}
if (OPENAI_API_KEY.startsWith('your_') || OPENAI_MODEL.startsWith('your_')) {
  console.error('backend/.env still contains placeholder values. Put your real key and a supported model name in it.');
  process.exit(1);
}

// ---------- limits ----------
const MAX_MESSAGE_CHARS = 4000;       // must match ChatViewModel.MAX_MESSAGE_LENGTH in the app
const MAX_HISTORY_ITEMS = 20;
const MAX_HISTORY_ITEM_CHARS = 8000;  // older long AI answers are truncated, not rejected
const MAX_HISTORY_TOTAL_CHARS = 32_000;
const ALLOWED_ROLES = new Set(['user', 'assistant']);
const CONVERSATION_ID_PATTERN = /^[A-Za-z0-9_-]{1,100}$/;

const SYSTEM_INSTRUCTIONS =
  'You are a helpful, concise AI assistant inside a mobile chat app. Answer clearly in plain text.';

const openAiOptions = {
  apiKey: OPENAI_API_KEY,
  timeout: 50_000, // shorter than the Android read timeout (60 s)
  maxRetries: 1,
};
if (OPENAI_BASE_URL) {
  openAiOptions.baseURL = OPENAI_BASE_URL;
}
const client = new OpenAI(openAiOptions);

const app = express();
app.disable('x-powered-by');
// No CORS: a native Android app does not need it. Add the `cors` package only if a browser client is added later.
app.use(express.json({ limit: '256kb' }));

const chatLimiter = rateLimit({
  windowMs: 60_000,
  limit: 20, // 20 chat requests per IP per minute
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: { code: 'rate_limited', message: 'Too many requests. Please wait a minute and try again.' } },
});

function sendError(res, status, code, message) {
  return res.status(status).json({ error: { code, message } });
}

function validateChatBody(body) {
  if (!body || typeof body !== 'object' || Array.isArray(body)) {
    return { error: 'Request body must be a JSON object.' };
  }

  const { conversationId, message, history } = body;

  if (typeof message !== 'string' || message.trim().length === 0) {
    return { error: 'message must be a non-empty string.' };
  }
  if (message.length > MAX_MESSAGE_CHARS) {
    return { error: `message must be at most ${MAX_MESSAGE_CHARS} characters.` };
  }
  if (conversationId !== undefined && (typeof conversationId !== 'string' || !CONVERSATION_ID_PATTERN.test(conversationId))) {
    return { error: 'conversationId must be 1-100 characters: letters, digits, "-" or "_".' };
  }
  if (history !== undefined && !Array.isArray(history)) {
    return { error: 'history must be an array.' };
  }

  const cleanHistory = [];
  for (const item of (history || []).slice(-MAX_HISTORY_ITEMS)) {
    if (!item || typeof item !== 'object') {
      return { error: 'Each history item must be an object.' };
    }
    if (!ALLOWED_ROLES.has(item.role)) {
      return { error: 'history roles must be "user" or "assistant".' };
    }
    if (typeof item.content !== 'string') {
      return { error: 'history content must be a string.' };
    }
    const content = item.content.trim().slice(0, MAX_HISTORY_ITEM_CHARS);
    if (content.length > 0) {
      cleanHistory.push({ role: item.role, content });
    }
  }

  // Keep the newest contiguous portion of history under a total budget. This
  // prevents a client-supplied history from exhausting the model context or
  // unexpectedly increasing upstream usage while preserving recent context.
  const boundedHistory = [];
  let historyChars = 0;
  for (let i = cleanHistory.length - 1; i >= 0; i -= 1) {
    const item = cleanHistory[i];
    if (historyChars + item.content.length > MAX_HISTORY_TOTAL_CHARS) {
      break;
    }
    boundedHistory.unshift(item);
    historyChars += item.content.length;
  }

  return { value: { message: message.trim(), history: boundedHistory } };
}

function handleUpstreamError(err, res) {
  // Log only safe metadata: never the key, never the user's text, never a stack trace.
  console.error(`[openai] ${err?.name || 'Error'} status=${err?.status ?? 'n/a'} code=${err?.code ?? 'n/a'}`);

  if (err instanceof OpenAI.APIConnectionTimeoutError) {
    return sendError(res, 504, 'upstream_timeout', 'The AI service took too long to respond. Please try again.');
  }
  if (err instanceof OpenAI.APIConnectionError) {
    return sendError(res, 502, 'upstream_unreachable', 'The server could not reach the AI service. Please try again later.');
  }
  if (err instanceof OpenAI.APIError) {
    if (err.status === 401 || err.status === 403) {
      return sendError(res, 502, 'upstream_auth', 'The server could not authenticate with the AI service. Check the backend configuration.');
    }
    if (err.status === 429 && err.code === 'insufficient_quota') {
      return sendError(res, 503, 'quota_exceeded', 'The AI service quota or billing limit was reached. Check the OpenAI project billing.');
    }
    if (err.status === 429) {
      return sendError(res, 429, 'rate_limited', 'The AI service is busy. Please wait a moment and try again.');
    }
    if (err.status === 404) {
      return sendError(res, 502, 'model_unavailable', 'The configured model is not available to this OpenAI project. Run "npm run models" and update OPENAI_MODEL.');
    }
    if (err.status === 400) {
      return sendError(res, 502, 'upstream_rejected', 'The AI service rejected the request.');
    }
  }
  return sendError(res, 502, 'upstream_error', 'The AI service returned an error. Please try again.');
}

app.get('/health', (req, res) => {
  res.json({ status: 'ok' });
});

app.post('/chat', chatLimiter, async (req, res) => {
  const parsed = validateChatBody(req.body);
  if (parsed.error) {
    return sendError(res, 400, 'invalid_request', parsed.error);
  }
  const { message, history } = parsed.value;

  try {
    const response = await client.responses.create({
      model: OPENAI_MODEL,
      instructions: SYSTEM_INSTRUCTIONS,
      input: [...history, { role: 'user', content: message }],
      store: false, // do not keep the conversation on OpenAI's side
    });

    const reply = (response.output_text || '').trim();
    if (!reply) {
      return sendError(res, 502, 'empty_reply', 'The AI returned an empty answer. Please try again.');
    }
    return res.json({ reply });
  } catch (err) {
    return handleUpstreamError(err, res);
  }
});

app.use(express.static(path.join(__dirname, '..', 'website')));

app.use((req, res) => sendError(res, 404, 'not_found', 'Route not found.'));

// eslint-disable-next-line no-unused-vars
app.use((err, req, res, next) => {
  if (err?.type === 'entity.too.large') {
    return sendError(res, 413, 'payload_too_large', 'Request body is too large.');
  }
  if (err?.type === 'entity.parse.failed' || err instanceof SyntaxError) {
    return sendError(res, 400, 'invalid_json', 'Request body is not valid JSON.');
  }
  console.error('[server] unexpected error');
  return sendError(res, 500, 'internal_error', 'Internal server error.');
});

export { app, validateChatBody };

export function startServer(port = PORT) {
  return app.listen(port, () => {
    console.log(`SmartAIChatbot backend listening on port ${port} (model: ${OPENAI_MODEL})`);
  });
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  startServer();
}
