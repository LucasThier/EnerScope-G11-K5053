import test from 'node:test';
import assert from 'node:assert/strict';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createLoader } from './loadModule.mjs';

const root = fileURLToPath(new URL('../src/', import.meta.url));
const load = createLoader();
const {
  INITIAL_PROJECT_ACCESS_STATE: INITIAL,
  projectAccessReducer: reduce,
  needsProjectAccessLoad: needsLoad,
  projectAccessView: view,
} = load(resolve(root, 'utils/projectAccessState.ts'));
const events = load(resolve(root, 'api/accessEvents.ts'));

const accessFor = (projectId) => ({ projectId, versions: [] });
const run = (...actions) => actions.reduce(reduce, INITIAL);
const loaded = (projectId, request = 1) =>
  run(
    { type: 'project', projectId },
    { type: 'requested', projectId, request },
    { type: 'succeeded', projectId, request, access: accessFor(projectId) },
  );

test('nothing is cached at first and a screen has to load', () => {
  assert.equal(needsLoad(INITIAL, 'a', null), true);
  assert.deepEqual(view(INITIAL, 'a'), { access: null, loading: true, error: null, forbidden: false });
  assert.deepEqual(view(INITIAL, undefined), { access: null, loading: false, error: null, forbidden: false });
});

test('a fresh answer is reused: no new request and no loading', () => {
  const state = loaded('a');
  assert.equal(needsLoad(state, 'a', null), false);
  assert.equal(view(state, 'a').loading, false);
  assert.deepEqual(view(state, 'a').access, accessFor('a'));
});

test('a request already in flight for the project is not repeated', () => {
  const state = run({ type: 'project', projectId: 'a' }, { type: 'requested', projectId: 'a', request: 1 });
  assert.equal(needsLoad(state, 'a', 'a'), false);
  assert.equal(needsLoad(state, 'b', 'a'), true);
});

test('changing project resets the cache and never shows the previous project', () => {
  const state = reduce(loaded('a'), { type: 'project', projectId: 'b' });
  assert.equal(state.access, null);
  assert.equal(state.loaded, false);
  assert.equal(needsLoad(state, 'b', null), true);
  assert.equal(view(state, 'a').loading, true);
  assert.equal(view(state, 'a').access, null);
  assert.equal(view(loaded('a'), 'b').access, null);
  assert.equal(reduce(loaded('a'), { type: 'project', projectId: 'a' }).access.projectId, 'a');
});

test('a response for a project that is no longer selected is discarded', () => {
  const state = run(
    { type: 'project', projectId: 'a' },
    { type: 'requested', projectId: 'a', request: 1 },
    { type: 'project', projectId: 'b' },
    { type: 'requested', projectId: 'b', request: 2 },
    { type: 'succeeded', projectId: 'a', request: 1, access: accessFor('a') },
  );
  assert.equal(state.projectId, 'b');
  assert.equal(state.access, null);
  assert.equal(state.loaded, false);
});

test('an older response for the same project cannot overwrite a newer request', () => {
  const state = run(
    { type: 'project', projectId: 'a' },
    { type: 'requested', projectId: 'a', request: 1 },
    { type: 'requested', projectId: 'a', request: 2 },
    { type: 'succeeded', projectId: 'a', request: 1, access: { projectId: 'a', stale: true } },
    { type: 'failed', projectId: 'a', request: 1, status: 500, message: 'old' },
  );
  assert.equal(state.loaded, false);
  assert.equal(state.access, null);
  const done = reduce(state, { type: 'succeeded', projectId: 'a', request: 2, access: accessFor('a') });
  assert.deepEqual(done.access, accessFor('a'));
});

test('a 403 is kept as an answer: forbidden, no access, no error, not refetched', () => {
  const state = run(
    { type: 'project', projectId: 'a' },
    { type: 'requested', projectId: 'a', request: 1 },
    { type: 'failed', projectId: 'a', request: 1, status: 403, message: 'x' },
  );
  assert.deepEqual(view(state, 'a'), { access: null, loading: false, error: null, forbidden: true });
  assert.equal(needsLoad(state, 'a', null), false);
});

test('any other failure shows the error and is retried when a screen asks again', () => {
  const state = run(
    { type: 'project', projectId: 'a' },
    { type: 'requested', projectId: 'a', request: 1 },
    { type: 'failed', projectId: 'a', request: 1, status: 500, message: 'boom' },
  );
  assert.deepEqual(view(state, 'a'), { access: null, loading: false, error: 'boom', forbidden: false });
  assert.equal(needsLoad(state, 'a', null), true);
});

test('retry after a failure or a 403 replaces it with the new answer', () => {
  for (const status of [403, 500]) {
    const failed = run(
      { type: 'project', projectId: 'a' },
      { type: 'requested', projectId: 'a', request: 1 },
      { type: 'failed', projectId: 'a', request: 1, status, message: 'boom' },
    );
    const retried = run(
      { type: 'requested', projectId: 'a', request: 2 },
      { type: 'succeeded', projectId: 'a', request: 2, access: accessFor('a') },
    );
    void retried;
    const state = [
      { type: 'requested', projectId: 'a', request: 2 },
      { type: 'succeeded', projectId: 'a', request: 2, access: accessFor('a') },
    ].reduce(reduce, failed);
    assert.deepEqual(view(state, 'a'), { access: accessFor('a'), loading: false, error: null, forbidden: false });
  }
});

test('invalidation keeps the old answer visible but makes the next screen refetch', () => {
  const state = reduce(loaded('a'), { type: 'invalidate', projectId: 'a' });
  assert.equal(view(state, 'a').loading, false);
  assert.deepEqual(view(state, 'a').access, accessFor('a'));
  assert.equal(needsLoad(state, 'a', null), true);
  assert.equal(state.generation, 1);
});

test('invalidation without a project, or for the cached one, applies; for another project it does not', () => {
  assert.equal(reduce(loaded('a'), { type: 'invalidate' }).fresh, false);
  assert.equal(reduce(loaded('a'), { type: 'invalidate', projectId: 'b' }).fresh, true);
  assert.equal(reduce(INITIAL, { type: 'invalidate' }), INITIAL);
});

test('a response that was in flight when the invalidation happened is discarded', () => {
  const state = run(
    { type: 'project', projectId: 'a' },
    { type: 'requested', projectId: 'a', request: 1 },
    { type: 'invalidate', projectId: 'a' },
    { type: 'succeeded', projectId: 'a', request: 1, access: { projectId: 'a', beforeChange: true } },
  );
  assert.equal(state.loaded, false);
  assert.equal(state.access, null);
  assert.equal(needsLoad(state, 'a', null), true);
});

test('successive invalidations each bump the generation so mounted screens refetch each time', () => {
  const once = reduce(loaded('a'), { type: 'invalidate', projectId: 'a' });
  const twice = reduce(once, { type: 'invalidate', projectId: 'a' });
  assert.equal(twice.generation, 2);
});

test('access events reach subscribers, with the project when known, and stop after unsubscribing', () => {
  const seen = [];
  const off = events.onAccessInvalidated((id) => seen.push(id));
  events.notifyAccessInvalidated('p1');
  events.notifyAccessInvalidated();
  off();
  events.notifyAccessInvalidated('p2');
  assert.deepEqual(seen, ['p1', undefined]);
});

test('invalidatesAccess notifies only after success and returns the response untouched', async () => {
  const seen = [];
  const off = events.onAccessInvalidated((id) => seen.push(id));
  const response = { data: 1 };
  assert.equal(await events.invalidatesAccess(Promise.resolve(response), 'p1'), response);
  await assert.rejects(events.invalidatesAccess(Promise.reject(new Error('no'))), /no/);
  off();
  assert.deepEqual(seen, ['p1']);
});
