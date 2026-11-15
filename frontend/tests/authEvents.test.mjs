import test from 'node:test';
import assert from 'node:assert/strict';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createLoader } from './loadModule.mjs';

const store = new Map();
globalThis.localStorage = {
  getItem: (k) => (store.has(k) ? store.get(k) : null),
  setItem: (k, v) => store.set(k, String(v)),
  removeItem: (k) => store.delete(k),
};

const root = fileURLToPath(new URL('../src/', import.meta.url));
const load = createLoader();
const events = load(resolve(root, 'api/authEvents.ts'));
const { client } = load(resolve(root, 'api/client.ts'));

const forbidden = (config) =>
  Promise.reject(
    Object.assign(new Error('Forbidden'), { config, response: { status: 403, data: {} } }),
  );

function setup(role) {
  events.resetAuthEventsForTests();
  store.clear();
  if (role) store.set('authUser', JSON.stringify({ id: 'u', platformRole: role }));
  const calls = { refresh: 0, lost: 0 };
  events.registerAuthHandlers({
    refreshUser: async () => {
      calls.refresh += 1;
    },
    sessionLost: () => {
      calls.lost += 1;
    },
  });
  client.defaults.adapter = forbidden;
  return calls;
}

test('a 403 for a platform admin refreshes the user data', async () => {
  const calls = setup('ADMIN');
  await assert.rejects(client.get('/users'));
  assert.equal(calls.refresh, 1);
});

test('a 403 for a regular user does not refresh anything', async () => {
  const calls = setup('USER');
  await assert.rejects(client.get('/users'));
  assert.equal(calls.refresh, 0);
});

test('refreshes are spaced by a minimum interval', () => {
  const calls = setup('ADMIN');
  assert.equal(events.requestUserRefresh(10_000), true);
  assert.equal(events.requestUserRefresh(10_000 + events.MIN_USER_REFRESH_INTERVAL_MS - 1), false);
  assert.equal(events.requestUserRefresh(10_000 + events.MIN_USER_REFRESH_INTERVAL_MS), true);
  assert.equal(calls.refresh, 2);
});

test('repeated 403 responses in a burst trigger a single refresh', async () => {
  const calls = setup('ADMIN');
  await Promise.allSettled([client.get('/users'), client.get('/organizations'), client.get('/users')]);
  assert.equal(calls.refresh, 1);
});

test('a failed refresh-token exchange notifies that the session was lost', async () => {
  const calls = setup('USER');
  store.set('refreshToken', 'stale');
  const axios = (await import('node:module')).createRequire(import.meta.url)('axios');
  const original = axios.post;
  axios.post = () => Promise.reject(new Error('expired'));
  client.defaults.adapter = (config) =>
    Promise.reject(Object.assign(new Error('Unauthorized'), { config, response: { status: 401, data: {} } }));
  try {
    await assert.rejects(client.get('/projects'));
  } finally {
    axios.post = original;
  }
  assert.equal(calls.lost, 1);
  assert.equal(store.has('refreshToken'), false);
});
