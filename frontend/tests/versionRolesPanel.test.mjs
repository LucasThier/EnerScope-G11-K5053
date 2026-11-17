import test from 'node:test';
import assert from 'node:assert/strict';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createLoader } from './loadModule.mjs';

const requested = [];
const components = () => new Proxy({}, { get: (_, name) => (props) => ({ type: name, props }) });
const stubs = {
  react: { useState: (initial) => [initial, () => undefined] },
  'react/jsx-runtime': {
    jsx: (type, props) => (typeof type === 'function' ? type(props) : { type, props }),
    jsxs: (type, props) => (typeof type === 'function' ? type(props) : { type, props }),
  },
};
const load = createLoader((name) => {
  if (name in stubs) return stubs[name];
  if (name.endsWith('/useVersionMembers')) {
    return {
      useVersionMembers: (versionId) => {
        requested.push(versionId);
        return { members: [], loading: false, error: null, reload: async () => undefined };
      },
    };
  }
  if (/\/(ui\/[A-Za-z]+|icons|AssignVersionRoleForm|RemoveVersionRoleDialog)$/.test(name)) return components();
  return undefined;
});
const root = fileURLToPath(new URL('../src/', import.meta.url));
const { VersionRolesPanel } = load(resolve(root, 'components/versionRoles/VersionRolesPanel.tsx'));

const entry = (over = {}) => ({
  versionId: 'v1',
  versionName: 'Base',
  source: 'VERSION_ROLES',
  roles: [{ type: 'ADMIN', vertical: null }],
  effectiveRole: 'ADMIN',
  editableVerticals: [],
  canView: true,
  canEdit: true,
  canEditConnections: true,
  canSimulate: true,
  canManage: true,
  canManageRoles: false,
  ...over,
});

function texts(node, found = []) {
  if (typeof node === 'string') found.push(node);
  else if (Array.isArray(node)) node.forEach((child) => texts(child, found));
  else if (node && typeof node === 'object') texts(node.props?.children, found);
  return found;
}

function types(node, found = []) {
  if (Array.isArray(node)) node.forEach((child) => types(child, found));
  else if (node && typeof node === 'object') {
    found.push(node.type);
    types(node.props?.children, found);
  }
  return found;
}

const render = (version) =>
  VersionRolesPanel({ version, projectMembers: [], callerUserId: 'u1', onChanged: async () => undefined });

test('a version administrator who cannot manage roles sees only their own access and a notice', () => {
  requested.length = 0;
  const tree = render(entry());
  const text = texts(tree).join(' ');
  assert.deepEqual(requested, [null]);
  assert.match(text, /Solo los Administradores del proyecto asignan, cambian o quitan roles de versión/);
  assert.match(text, /Administrás esta versión por tu rol de Administrador de versión/);
  assert.match(text, /Tus accesos en esta versión/);
  assert.equal(types(tree).includes('AssignVersionRoleForm'), false);
});

test('someone who can manage roles loads the members and gets the assignment form', () => {
  requested.length = 0;
  const tree = render(entry({ source: 'PROJECT_ADMIN', canManageRoles: true }));
  assert.deepEqual(requested, ['v1']);
  assert.equal(types(tree).includes('AssignVersionRoleForm'), true);
  assert.doesNotMatch(texts(tree).join(' '), /Solo los Administradores del proyecto asignan/);
});
