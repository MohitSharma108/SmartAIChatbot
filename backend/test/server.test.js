import assert from 'node:assert/strict';
import http from 'node:http';
import test, { after, before } from 'node:test';

process.env.OPENAI_API_KEY = 'sk-test';
process.env.OPENAI_MODEL = 'test-model';

let upstream;
let upstreamPort;
let appServer;
let appPort;
let upstreamResponse;
const upstreamRequests = [];

function listen(server) {
  return new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => resolve(server.address().port));
  });
}

function close(server) {
  return new Promise((resolve, reject) => {
    if (!server) {
      resolve();
      return;
    }
    server.close((error) => (error ? reject(error) : resolve()));
  });
}

async function readRequestBody(request) {
  const chunks = [];
  for await (const chunk of request) {
    chunks.push(chunk);
  }
  return Buffer.concat(chunks).toString('utf8');
}

async function request(path, options = {}) {
  const response = await fetch(`http://127.0.0.1:${appPort}${path}`, {
    method: options.method || 'GET',
    headers: options.body === undefined
      ? undefined
      : { 'content-type': 'application/json' },
    body: options.body,
  });
  const text = await response.text();
  return {
    status: response.status,
    body: text ? JSON.parse(text) : null,
  };
}

before(async () => {
  upstreamResponse = {
    status: 200,
    body: {
      id: 'resp_test',
      object: 'response',
      output: [{
        type: 'message',
        role: 'assistant',
        content: [{ type: 'output_text', text: 'stub reply' }],
      }],
    },
  };

  upstream = http.createServer(async (request, response) => {
    const rawBody = await readRequestBody(request);
    upstreamRequests.push({
      method: request.method,
      url: request.url,
      body: rawBody ? JSON.parse(rawBody) : null,
    });
    response.writeHead(upstreamResponse.status, { 'content-type': 'application/json' });
    response.end(JSON.stringify(upstreamResponse.body));
  });
  upstreamPort = await listen(upstream);

  process.env.OPENAI_BASE_URL = `http://127.0.0.1:${upstreamPort}/v1`;
  const { app } = await import('../server.js');
  appServer = app.listen(0, '127.0.0.1');
  await new Promise((resolve, reject) => {
    appServer.once('listening', resolve);
    appServer.once('error', reject);
  });
  appPort = appServer.address().port;
});

after(async () => {
  await close(appServer);
  await close(upstream);
});

test('reports a healthy backend', async () => {
  const result = await request('/health');
  assert.equal(result.status, 200);
  assert.deepEqual(result.body, { status: 'ok' });
});

test('rejects invalid chat input before calling OpenAI', async () => {
  const beforeCount = upstreamRequests.length;
  const result = await request('/chat', {
    method: 'POST',
    body: JSON.stringify({ message: '   ' }),
  });

  assert.equal(result.status, 400);
  assert.equal(result.body.error.code, 'invalid_request');
  assert.equal(upstreamRequests.length, beforeCount);
});

test('bounds history and returns the assistant reply', async () => {
  const history = Array.from({ length: 20 }, (_, index) => ({
    role: index % 2 === 0 ? 'user' : 'assistant',
    content: `${index}:${'x'.repeat(4000)}`,
  }));
  const result = await request('/chat', {
    method: 'POST',
    body: JSON.stringify({
      conversationId: 'conversation_test',
      message: 'hello',
      history,
    }),
  });

  assert.equal(result.status, 200);
  assert.deepEqual(result.body, { reply: 'stub reply' });

  const lastRequest = upstreamRequests.at(-1);
  assert.equal(lastRequest.method, 'POST');
  assert.equal(lastRequest.url, '/v1/responses');
  assert.equal(lastRequest.body.input.at(-1).content, 'hello');
  assert.ok(lastRequest.body.input.length < history.length + 1);
  assert.ok(
    lastRequest.body.input
      .slice(0, -1)
      .reduce((total, item) => total + item.content.length, 0) <= 32_000,
  );
});

test('maps upstream authentication errors to a safe response', async () => {
  upstreamResponse = {
    status: 401,
    body: { error: { message: 'upstream secret must not leak' } },
  };

  const result = await request('/chat', {
    method: 'POST',
    body: JSON.stringify({ message: 'hello' }),
  });

  assert.equal(result.status, 502);
  assert.deepEqual(result.body, {
    error: {
      code: 'upstream_auth',
      message: 'The server could not authenticate with the AI service. Check the backend configuration.',
    },
  });
  assert.doesNotMatch(JSON.stringify(result.body), /upstream secret/);

  upstreamResponse = {
    status: 200,
    body: {
      id: 'resp_test',
      object: 'response',
      output: [{
        type: 'message',
        role: 'assistant',
        content: [{ type: 'output_text', text: 'stub reply' }],
      }],
    },
  };
});

test('returns JSON errors for malformed JSON and unknown routes', async () => {
  const malformed = await fetch(`http://127.0.0.1:${appPort}/chat`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: '{',
  });
  assert.equal(malformed.status, 400);
  assert.deepEqual(await malformed.json(), {
    error: { code: 'invalid_json', message: 'Request body is not valid JSON.' },
  });

  const unknown = await request('/not-a-route');
  assert.equal(unknown.status, 404);
  assert.deepEqual(unknown.body, {
    error: { code: 'not_found', message: 'Route not found.' },
  });
});
