import test from 'node:test';
import assert from 'node:assert/strict';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createLoader } from './loadModule.mjs';

const root = fileURLToPath(new URL('../src/', import.meta.url));
const load = createLoader();
const { organizationRoleLabel, projectRoleLabel, userStatusLabel, userDetailActions } = load(
  resolve(root, 'utils/userDetail.ts'),
);

test('organization roles have a Spanish label', () => {
  assert.equal(organizationRoleLabel('OWNER'), 'Propietario');
  assert.equal(organizationRoleLabel('MEMBER'), 'Miembro');
});

test('project roles have a Spanish label', () => {
  assert.equal(projectRoleLabel('ADMIN'), 'Administrador');
  assert.equal(projectRoleLabel('EDITOR'), 'Editor');
});

test('a role the screen does not know is shown as the backend sent it', () => {
  assert.equal(organizationRoleLabel('GUEST'), 'GUEST');
  assert.equal(projectRoleLabel('VIEWER'), 'VIEWER');
});

test('status label follows the active flag', () => {
  assert.equal(userStatusLabel(true), 'Activo');
  assert.equal(userStatusLabel(false), 'Inactivo');
});

test('the detail always offers the role change and toggles deactivate and reactivate by state', () => {
  assert.deepEqual(userDetailActions({ active: true }), ['changeRole', 'deactivate']);
  assert.deepEqual(userDetailActions({ active: false }), ['changeRole', 'reactivate']);
});
