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
const { ProjectsTable } = load(resolve(root, 'components/projects/ProjectsTable.tsx'));
const labels = load(resolve(root, 'components/projects/projectMemberTypeLabels.ts'));

function collect(node, found = []) {
  if (Array.isArray(node)) {
    node.forEach((child) => collect(child, found));
  } else if (node && typeof node === 'object') {
    if (node.type === 'button') found.push(node.props);
    collect(node.props?.children, found);
  }
  return found;
}

const noop = () => undefined;
const project = (myRole) => ({ id: 'p1', name: 'Alfa', myRole, memberCount: 2 });

function buttons(myRole, isPlatformAdmin, overrides = {}) {
  const tree = ProjectsTable({
    projects: [project(myRole)],
    activeProjectId: null,
    isPlatformAdmin,
    onView: noop,
    onEdit: noop,
    onDelete: noop,
    onManageMembers: noop,
    onViewTeam: noop,
    onViewVersions: noop,
    ...overrides,
  });
  return collect(tree).map((props) => props['aria-label']);
}

test('a project member who does not administer sees "Ver equipo" instead of managing', () => {
  const found = buttons('EDITOR', false);
  assert.ok(found.includes('Ver equipo de Alfa'));
  assert.ok(!found.includes('Administrar los integrantes de Alfa'));
});

test('a project administrator keeps the manage button and gets no "Ver equipo"', () => {
  const found = buttons('ADMIN', false);
  assert.ok(found.includes('Administrar los integrantes de Alfa'));
  assert.ok(!found.includes('Ver equipo de Alfa'));
});

test('"Ver equipo" reports its own project', () => {
  let seen = null;
  const tree = ProjectsTable({
    projects: [project('EDITOR')],
    activeProjectId: null,
    isPlatformAdmin: false,
    onView: noop,
    onEdit: noop,
    onDelete: noop,
    onManageMembers: noop,
    onViewTeam: (p) => {
      seen = p.id;
    },
    onViewVersions: noop,
  });
  collect(tree).find((props) => props['aria-label'] === 'Ver equipo de Alfa').onClick();
  assert.equal(seen, 'p1');
});

test('project member types are labelled as project roles', () => {
  assert.equal(labels.PROJECT_MEMBER_TYPE_LABELS.ADMIN, 'Administrador del proyecto');
  assert.equal(labels.PROJECT_MEMBER_TYPE_LABELS.EDITOR, 'Editor del proyecto');
});
