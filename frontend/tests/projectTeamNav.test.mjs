import test from 'node:test';
import assert from 'node:assert/strict';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createLoader } from './loadModule.mjs';

const state = { project: null, projects: [], loading: false, role: 'USER', member: true };
const stubs = {
  react: { useMemo: (fn) => fn() },
  'react/jsx-runtime': {
    jsx: (type, props) => (typeof type === 'function' ? type(props) : { type, props }),
    jsxs: (type, props) => (typeof type === 'function' ? type(props) : { type, props }),
  },
};
const load = createLoader((name) => {
  if (name in stubs) return stubs[name];
  if (name.endsWith('/useActiveProject')) {
    return { useActiveProject: () => ({ activeProject: state.project, projects: state.projects, error: null, isLoading: state.loading }) };
  }
  if (name.endsWith('/useAuth')) return { useAuth: () => ({ user: { platformRole: state.role } }) };
  if (name.endsWith('/useMyOrganizations')) return { useMyOrganizations: () => ({ belongsToOrganization: state.member, loading: false }) };
  return undefined;
});
const root = fileURLToPath(new URL('../src/', import.meta.url));
const { useNavigationTree } = load(resolve(root, 'hooks/useNavigationTree.ts'));

const projectsEntry = () => useNavigationTree()[0].items.find((item) => item.label === 'Proyectos');

test('the project team entry is visible to every member with an active project', () => {
  for (const role of ['ADMIN', 'USER']) {
    for (const myRole of ['ADMIN', 'EDITOR']) {
      const project = { id: 'p', name: 'P', myRole };
      Object.assign(state, { role, project, projects: [project], loading: false });
      const entry = projectsEntry();
      assert.deepEqual(
        entry.children.map((child) => [child.label, child.fullLabel, child.to]),
        [['Todos los proyectos', undefined, '/projects'], ['Equipo del proyecto', 'Equipo del proyecto: P', '/projects/p/team']],
        `${role}/${myRole}`,
      );
    }
  }
});

test('an editor sees Projects as a group with the team entry', () => {
  const project = { id: 'p', name: 'P', myRole: 'EDITOR' };
  Object.assign(state, { role: 'USER', member: false, project, projects: [project], loading: false });
  const entry = projectsEntry();
  assert.equal(entry.to, undefined);
  assert.equal(entry.children.length, 2);
});

test('without an active project Projects stays a single link with no team entry', () => {
  Object.assign(state, { role: 'USER', project: null, projects: [], loading: false });
  assert.deepEqual([projectsEntry().to, projectsEntry().children], ['/projects', undefined]);
});

test('while projects load the team entry is not shown yet', () => {
  const project = { id: 'p', name: 'P', myRole: 'EDITOR' };
  Object.assign(state, { role: 'USER', project, projects: [project], loading: true });
  assert.deepEqual(projectsEntry().children.map((child) => child.label), ['Todos los proyectos']);
});
