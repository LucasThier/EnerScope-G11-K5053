import test from 'node:test';
import assert from 'node:assert/strict';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createLoader } from './loadModule.mjs';

const root = fileURLToPath(new URL('../src/', import.meta.url));
const ok = () => Promise.resolve({ data: { data: null } });
const client = { get: ok, post: ok, patch: ok, delete: ok };
const load = createLoader((name) => (name.endsWith('/client') ? { client } : undefined));
const { projectsApi } = load(resolve(root, 'api/projects.ts'));
const { versionAccessApi } = load(resolve(root, 'api/versionAccess.ts'));
const { onAccessInvalidated } = load(resolve(root, 'api/accessEvents.ts'));

async function invalidations(run) {
  const seen = [];
  const off = onAccessInvalidated((id) => seen.push(id ?? null));
  await run();
  off();
  return seen;
}

test('version role changes invalidate the cached access', async () => {
  assert.deepEqual(await invalidations(() => versionAccessApi.addRole('v', { userId: 'u', memberType: 'EDITOR' })), [null]);
  assert.deepEqual(await invalidations(() => versionAccessApi.removeRole('v', 'm', 'r')), [null]);
});

test('project member changes invalidate the access of that project', async () => {
  assert.deepEqual(await invalidations(() => projectsApi.addMember('p', { userId: 'u', memberType: 'EDITOR' })), ['p']);
  assert.deepEqual(await invalidations(() => projectsApi.changeMemberRole('p', 'm', { memberType: 'ADMIN' })), ['p']);
  assert.deepEqual(await invalidations(() => projectsApi.removeMember('p', 'm')), ['p']);
});

test('creating, deleting and merging a version invalidate the cached access', async () => {
  assert.deepEqual(await invalidations(() => projectsApi.createVersion('p', { name: 'n' })), ['p']);
  assert.deepEqual(await invalidations(() => projectsApi.deleteVersion('v')), [null]);
  assert.deepEqual(await invalidations(() => projectsApi.mergeVersion('v')), [null]);
});

test('reads never invalidate', async () => {
  assert.deepEqual(await invalidations(async () => {
    await versionAccessApi.projectAccess('p');
    await versionAccessApi.members('v');
    await projectsApi.listVersions('p');
    await projectsApi.members('p');
  }), []);
});
