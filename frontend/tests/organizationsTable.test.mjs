import test from 'node:test';
import assert from 'node:assert/strict';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createLoader } from './loadModule.mjs';

const stubs = {
  'react/jsx-runtime': {
    jsx: (type, props) => (typeof type === 'function' ? type(props) : { type, props }),
    jsxs: (type, props) => (typeof type === 'function' ? type(props) : { type, props }),
  },
};
const load = createLoader((name) => stubs[name]);
const root = fileURLToPath(new URL('../src/', import.meta.url));
const { OrganizationsTable } = load(resolve(root, 'components/organizations/OrganizationsTable.tsx'));

function collect(node, found = []) {
  if (Array.isArray(node)) {
    node.forEach((child) => collect(child, found));
  } else if (node && typeof node === 'object') {
    if (node.type === 'button') found.push(node.props);
    collect(node.props?.children, found);
  }
  return found;
}

const organization = (id, active) => ({ id, name: `Org ${id}`, active, memberCount: 0, createdAt: null });
const noop = () => undefined;

function labelsFor(organizations, handlers = {}) {
  const tree = OrganizationsTable({
    organizations,
    onViewMembers: noop,
    onAddMembers: noop,
    onRename: noop,
    onDeactivate: noop,
    onReactivate: async () => undefined,
    ...handlers,
  });
  return collect(tree).map((button) => button['aria-label']);
}

test('an active organization offers viewing, adding members, renaming and deactivating', () => {
  assert.deepEqual(labelsFor([organization('a', true)]), [
    'Ver integrantes de Org a',
    'Agregar integrantes a Org a',
    'Renombrar Org a',
    'Dar de baja Org a',
  ]);
});

test('an inactive organization does not offer adding members', () => {
  assert.deepEqual(labelsFor([organization('b', false)]), [
    'Ver integrantes de Org b',
    'Renombrar Org b',
    'Reactivar Org b',
  ]);
});

test('the add members button reports its own organization', () => {
  const picked = [];
  const orgs = [organization('a', true), organization('c', true)];
  const tree = OrganizationsTable({
    organizations: orgs,
    onViewMembers: noop,
    onAddMembers: (org) => picked.push(org.id),
    onRename: noop,
    onDeactivate: noop,
    onReactivate: async () => undefined,
  });
  const buttons = collect(tree).filter((b) => b['aria-label'].startsWith('Agregar integrantes'));
  buttons.forEach((b) => b.onClick());
  assert.deepEqual(picked, ['a', 'c']);
});
