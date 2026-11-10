import test from 'node:test';
import assert from 'node:assert/strict';

import { onRequest } from '../functions/api/[[path]].js';

const BACKEND = 'https://enerscope.abc123.us-east-1.cs.amazonlightsail.com';
const realFetch = globalThis.fetch;

// Replaces fetch for one test and records what the proxy sent upstream.
function stubFetch(t, respond = () => new Response('{"success":true}', { status: 200 })) {
  const calls = [];
  globalThis.fetch = async (url, init) => {
    calls.push({ url, init });
    return respond(url, init);
  };
  t.after(() => { globalThis.fetch = realFetch; });
  return calls;
}

test('forwards path and query string to the BACKEND_URL origin', async (t) => {
  const calls = stubFetch(t);
  const request = new Request('https://enerscope.pages.dev/api/v1/projects?page=2&size=10');
  await onRequest({ request, env: { BACKEND_URL: `${BACKEND}/ignored/path/` } });
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, `${BACKEND}/api/v1/projects?page=2&size=10`);
  assert.equal(calls[0].init.redirect, 'manual');
});

test('forwards method, body and Authorization, and drops Host and Origin', async (t) => {
  const calls = stubFetch(t);
  const request = new Request('https://enerscope.pages.dev/api/v1/auth/login', {
    method: 'POST',
    headers: {
      Authorization: 'Bearer token-123',
      'Content-Type': 'application/json',
      Origin: 'https://enerscope.pages.dev',
    },
    body: JSON.stringify({ mail: 'a@b.c', password: 'x' }),
  });
  await onRequest({ request, env: { BACKEND_URL: BACKEND } });
  const { init } = calls[0];
  assert.equal(init.method, 'POST');
  assert.equal(new TextDecoder().decode(init.body), '{"mail":"a@b.c","password":"x"}');
  assert.equal(init.headers.get('authorization'), 'Bearer token-123');
  assert.equal(init.headers.get('content-type'), 'application/json');
  assert.equal(init.headers.get('origin'), null);
  assert.equal(init.headers.get('host'), null);
});

test('sends no body for GET requests', async (t) => {
  const calls = stubFetch(t);
  await onRequest({ request: new Request('https://x.pages.dev/api/v1/health'), env: { BACKEND_URL: BACKEND } });
  assert.equal(calls[0].init.method, 'GET');
  assert.equal(calls[0].init.body, undefined);
});

test('passes the backend response through untouched, including a 401', async (t) => {
  stubFetch(t, () => new Response('{"success":false,"message":"expired"}', {
    status: 401,
    headers: { 'Content-Type': 'application/json' },
  }));
  const response = await onRequest({
    request: new Request('https://x.pages.dev/api/v1/users/me'),
    env: { BACKEND_URL: BACKEND },
  });
  assert.equal(response.status, 401);
  assert.equal(response.headers.get('content-type'), 'application/json');
  assert.deepEqual(await response.json(), { success: false, message: 'expired' });
});

test('answers 500 with an ApiResponse envelope when BACKEND_URL is missing or invalid', async (t) => {
  const calls = stubFetch(t);
  for (const env of [{}, { BACKEND_URL: 'not a url' }]) {
    const response = await onRequest({ request: new Request('https://x.pages.dev/api/v1/health'), env });
    assert.equal(response.status, 500);
    const body = await response.json();
    assert.equal(body.success, false);
    assert.equal(body.data, null);
    assert.match(body.message, /BACKEND_URL/);
  }
  assert.equal(calls.length, 0);
});

test('answers 502 with an ApiResponse envelope when the backend is unreachable', async (t) => {
  stubFetch(t, () => { throw new TypeError('fetch failed'); });
  const response = await onRequest({
    request: new Request('https://x.pages.dev/api/v1/health'),
    env: { BACKEND_URL: BACKEND },
  });
  assert.equal(response.status, 502);
  const body = await response.json();
  assert.equal(body.success, false);
  assert.equal(body.message, 'The backend is unreachable');
});
