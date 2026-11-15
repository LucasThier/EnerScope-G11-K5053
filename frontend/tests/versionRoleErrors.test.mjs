import test from 'node:test';
import assert from 'node:assert/strict';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';
import { createLoader } from './loadModule.mjs';

const nodeRequire = createRequire(import.meta.url);
const { AxiosError } = nodeRequire('axios');
const root = fileURLToPath(new URL('../src/', import.meta.url));
const errors = createLoader()(resolve(root, 'api/errors.ts'));

const failure = (status, message) =>
  new AxiosError('Request failed', 'ERR_BAD_REQUEST', undefined, undefined, {
    status,
    statusText: '',
    headers: {},
    config: {},
    data: { success: false, message },
  });

test('403 messages of the version endpoints are translated', () => {
  assert.equal(
    errors.getVersionRoleErrorMessage(failure(403, 'You are not allowed to manage this version'), 'x'),
    'No tenés permiso para administrar esta versión.',
  );
  assert.equal(
    errors.getVersionRoleErrorMessage(failure(403, 'You are not allowed to access this version'), 'x'),
    'No tenés acceso a esta versión.',
  );
});

test('the 403 of managing roles asks for a project administrator', () => {
  assert.equal(
    errors.getVersionRoleErrorMessage(failure(403, 'You are not allowed to manage the roles of this version'), 'x'),
    'Solo los Administradores del proyecto pueden asignar, cambiar o quitar roles de versión.',
  );
});

test('400 messages of granting a role are translated', () => {
  const cases = {
    'User already has this role': 'Esa persona ya tiene ese rol en la versión.',
    'EDITOR roles require a vertical': 'Elegí la vertical del Editor.',
    "User is not a member of the version's project": 'Esa persona no integra el proyecto de esta versión.',
    'Version is not attached to any project': 'La versión no pertenece a ningún proyecto.',
    'User is not active': 'La cuenta de esa persona está desactivada.',
    'ADMIN roles apply to the whole version, not one vertical': 'Ese rol aplica a toda la versión, no a una vertical.',
    'SIMULATOR roles apply to the whole version, not one vertical': 'Ese rol aplica a toda la versión, no a una vertical.',
    'memberType cannot be null': 'Elegí un rol.',
  };
  for (const [message, expected] of Object.entries(cases)) {
    assert.equal(errors.getVersionRoleErrorMessage(failure(400, message), 'x'), expected, message);
  }
});

test('404 messages of revoking a role are translated for the version context', () => {
  assert.equal(
    errors.getVersionRoleErrorMessage(failure(404, 'Member not found'), 'x'),
    'Esa persona ya no tiene roles en esta versión.',
  );
  assert.equal(
    errors.getVersionRoleErrorMessage(failure(404, 'Role not found'), 'x'),
    'Ese rol ya no existe en esta versión.',
  );
  assert.equal(
    errors.getVersionRoleErrorMessage(failure(404, 'Version not found with id: 1'), 'x'),
    'La versión no existe o ya fue eliminada.',
  );
});

test('the global translation of Member not found still talks about the project', () => {
  assert.equal(errors.getErrorMessage(failure(404, 'Member not found'), 'x'), 'Esa persona ya no integra el proyecto.');
});

test('unknown messages pass through and transport failures use the fallback', () => {
  assert.equal(errors.getVersionRoleErrorMessage(failure(400, 'Something else'), 'x'), 'Something else');
  assert.equal(errors.getVersionRoleErrorMessage(new Error('boom'), 'x'), 'boom');
  assert.equal(errors.getVersionRoleErrorMessage('nope', 'fallback'), 'fallback');
  assert.equal(errors.getErrorStatus(failure(403, 'x')), 403);
});

test('the 403 of a cascading delete is translated without naming versions', () => {
  assert.equal(
    errors.getVersionRoleErrorMessage(
      failure(403, 'You are not allowed to delete one or more versions below this one'),
      'x',
    ),
    'No podés eliminar esta versión porque tiene versiones derivadas que no tenés permiso para eliminar.',
  );
  assert.equal(
    errors.getErrorMessage(
      failure(403, 'You are not allowed to delete one or more versions below this one'),
      'x',
    ),
    'No podés eliminar esta versión porque tiene versiones derivadas que no tenés permiso para eliminar.',
  );
});
