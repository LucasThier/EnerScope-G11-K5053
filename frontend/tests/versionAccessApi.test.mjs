import test from 'node:test';
import assert from 'node:assert/strict';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createLoader } from './loadModule.mjs';

const root = fileURLToPath(new URL('../src/', import.meta.url));
const calls = [];
const record = (verb) => (...args) => {
  calls.push([verb, ...args]);
  return Promise.resolve({ data: { data: null } });
};
const client = { get: record('get'), post: record('post'), delete: record('delete') };
const load = createLoader((name) => (name.endsWith('/client') ? { client } : undefined));
const { versionAccessApi } = load(resolve(root, 'api/versionAccess.ts'));

test('project access is read from the project access endpoint', async () => {
  calls.length = 0;
  await versionAccessApi.projectAccess('p1');
  assert.deepEqual(calls, [['get', '/projects/p1/access']]);
});

test('version members are listed from the version members endpoint', async () => {
  calls.length = 0;
  await versionAccessApi.members('v1');
  assert.deepEqual(calls, [['get', '/version/v1/members']]);
});

test('granting a role posts the person, the role and the vertical when there is one', async () => {
  calls.length = 0;
  await versionAccessApi.addRole('v1', { userId: 'u1', memberType: 'EDITOR', vertical: 'EXPORT' });
  await versionAccessApi.addRole('v1', { userId: 'u2', memberType: 'SIMULATOR' });
  assert.deepEqual(calls, [
    ['post', '/version/v1/members', { userId: 'u1', memberType: 'EDITOR', vertical: 'EXPORT' }],
    ['post', '/version/v1/members', { userId: 'u2', memberType: 'SIMULATOR' }],
  ]);
});

test('revoking a role deletes it by member and role id', async () => {
  calls.length = 0;
  await versionAccessApi.removeRole('v1', 'm1', 'r1');
  assert.deepEqual(calls, [['delete', '/version/v1/members/m1/roles/r1']]);
});
