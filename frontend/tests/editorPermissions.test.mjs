import test from 'node:test';
import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createLoader } from './loadModule.mjs';

const load = createLoader();
const root = fileURLToPath(new URL('../src/', import.meta.url));
const { editorGates } = load(resolve(root, 'utils/editorPermissions.ts'));

const version = (versionId, overrides = {}) => ({
  versionId,
  canEdit: false,
  canEditConnections: false,
  canManage: false,
  ...overrides,
});
const access = (overrides = {}) => ({
  projectId: 'p',
  canCreateRootVersions: false,
  versions: [
    version('both', { canEdit: true, canEditConnections: true }),
    version('nodes', { canEdit: true }),
    version('links', { canEditConnections: true }),
    version('none'),
  ],
  ...overrides,
});

const DENIED = { canEdit: false, canEditConnections: false, canCreateRoot: false, readOnly: true };
function assertDenied(gates) {
  for (const [key, value] of Object.entries(DENIED)) assert.equal(gates[key], value, key);
  assert.equal(gates.canBranchFrom('both'), false);
}

test('null or undefined access denies everything', () => {
  assertDenied(editorGates(null, 'both', false, false));
  assertDenied(editorGates(undefined, 'both', false, false));
});

test('a failed or loading access denies everything even when data is present', () => {
  assertDenied(editorGates(access({ canCreateRootVersions: true }), 'both', true, false));
  assertDenied(editorGates(access({ canCreateRootVersions: true }), 'both', false, true));
});

test('unknown or missing version has no edit rights', () => {
  for (const id of ['ghost', '', null, undefined]) {
    const gates = editorGates(access(), id, false, false);
    assert.equal(gates.canEdit, false);
    assert.equal(gates.canEditConnections, false);
    assert.equal(gates.readOnly, true);
  }
});

test('canEdit and canEditConnections are independent', () => {
  const both = editorGates(access(), 'both', false, false);
  assert.deepEqual([both.canEdit, both.canEditConnections, both.readOnly], [true, true, false]);
  const nodes = editorGates(access(), 'nodes', false, false);
  assert.deepEqual([nodes.canEdit, nodes.canEditConnections, nodes.readOnly], [true, false, false]);
  const links = editorGates(access(), 'links', false, false);
  assert.deepEqual([links.canEdit, links.canEditConnections, links.readOnly], [false, true, false]);
  const none = editorGates(access(), 'none', false, false);
  assert.deepEqual([none.canEdit, none.canEditConnections, none.readOnly], [false, false, true]);
});

test('canCreateRoot comes from the project and does not depend on the version', () => {
  assert.equal(editorGates(access({ canCreateRootVersions: true }), 'none', false, false).canCreateRoot, true);
  assert.equal(editorGates(access({ canCreateRootVersions: true }), null, false, false).canCreateRoot, true);
  assert.equal(editorGates(access({ canCreateRootVersions: false }), 'both', false, false).canCreateRoot, false);
});

test('canBranchFrom follows the parent version canEdit', () => {
  const gates = editorGates(access(), 'none', false, false);
  assert.equal(gates.canBranchFrom('both'), true);
  assert.equal(gates.canBranchFrom('nodes'), true);
  assert.equal(gates.canBranchFrom('links'), false);
  assert.equal(gates.canBranchFrom('none'), false);
  assert.equal(gates.canBranchFrom('ghost'), false);
});

test('the role-based project edit check no longer exists anywhere in src', () => {
  const walk = (dir) =>
    readdirSync(dir, { withFileTypes: true }).flatMap((entry) =>
      entry.isDirectory() ? walk(join(dir, entry.name)) : [join(dir, entry.name)],
    );
  const offenders = walk(root).filter(
    (file) => /\.tsx?$/.test(file) && readFileSync(file, 'utf8').includes('canEditProjectByRole'),
  );
  assert.deepEqual(offenders, []);
});
