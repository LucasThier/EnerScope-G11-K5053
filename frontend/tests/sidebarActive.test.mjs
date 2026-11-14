import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';
const require = createRequire(import.meta.url);
const state = { project: null, projects: [], loading: false, role: 'ADMIN', member: true };
const cache = new Map();
function load(path) {
  if (cache.has(path)) return cache.get(path).exports;
  const module = { exports: {} }; cache.set(path, module);
  const code = ts.transpileModule(readFileSync(path, 'utf8'), { compilerOptions: {
    module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, target: ts.ScriptTarget.ES2022,
  }}).outputText;
  const localRequire = name => {
    if (name === 'react') return { useMemo: fn => fn() };
    if (name === './useActiveProject') return { useActiveProject: () => ({ activeProject: state.project, projects: state.projects, error: null, isLoading: state.loading }) };
    if (name === './useAuth') return { useAuth: () => ({ user: { platformRole: state.role } }) };
    if (name === './useMyOrganizations') return { useMyOrganizations: () => ({ belongsToOrganization: state.member, loading: false }) };
    if (!name.startsWith('.')) return require(name);
    const base = resolve(dirname(path), name);
    return load([base + '.ts', base + '.tsx', base + '/index.tsx'].find(existsSync));
  };
  new Function('require', 'module', 'exports', code)(localRequire, module, module.exports);
  return module.exports;
}
const root = fileURLToPath(new URL('../src/', import.meta.url));
const { activeLeaf, NAV_GROUPS } = load(resolve(root, 'components/layout/navItems.tsx'));
const { useNavigationTree } = load(resolve(root, 'hooks/useNavigationTree.ts'));
const project = { id: 'p', name: 'P', myRole: 'ADMIN' };
const labelsAt = pathname => {
  const groups = useNavigationTree();
  const leaf = activeLeaf(groups, pathname);
  return leaf ? leaf.fullLabel ?? leaf.label : null;
};
const ROUTES = [
  '/app', '/projects', '/projects/p/team', '/projects/p/versions', '/versions', '/editor',
  '/projects/p/scenario-configurations', '/projects/p/versions/v/economics', '/projects/p/versions/v/economics/configuration',
  '/projects/p/simulations', '/projects/p/versions/v/economics/history', '/projects/p/versions/v/economics/results/e',
  '/organizations', '/organizations/o', '/admin/organizations', '/admin/users', '/profile',
];
test('at most one sidebar leaf is current on every route, with and without a project', () => {
  for (const withProject of [false, true]) for (const role of ['ADMIN', 'USER']) for (const member of [false, true]) {
    Object.assign(state, { project: withProject ? project : null, projects: withProject ? [project] : [], role, member });
    const groups = useNavigationTree();
    for (const route of ROUTES) {
      const leaves = groups.flatMap(g => g.items.flatMap(i => i.children ?? [i]));
      const current = leaves.filter(l => l === activeLeaf(groups, route));
      assert.ok(current.length <= 1, route);
      assert.ok(current.every(l => l.to !== undefined), route);
    }
  }
});
test('Projects alone is current on /projects without a project', () => {
  Object.assign(state, { project: null, projects: [], loading: false, role: 'ADMIN', member: true });
  assert.equal(labelsAt('/projects'), 'Proyectos');
  assert.equal(labelsAt('/projects/p/simulations'), null);
});
test('route to current item matrix with a manageable project', () => {
  Object.assign(state, { project, projects: [project], loading: false, role: 'ADMIN', member: true });
  const expected = {
    '/app': 'Inicio', '/projects': 'Todos los proyectos', '/projects/p/team': 'Equipo del proyecto: P',
    '/projects/p/versions': 'Versiones del proyecto: P', '/versions': 'Todas las versiones', '/editor': 'Mapa de la Cadena de Valor',
    '/projects/p/scenario-configurations': 'Configuración del Escenario',
    '/projects/p/versions/v/economics': 'Configuración del Escenario',
    '/projects/p/versions/v/economics/configuration': 'Configuración del Escenario',
    '/projects/p/simulations': 'Simulación de Escenarios',
    '/projects/p/versions/v/economics/history': 'Simulación de Escenarios',
    '/projects/p/versions/v/economics/results/e': 'Simulación de Escenarios',
    '/organizations': 'Mis organizaciones', '/organizations/o': 'Mis organizaciones',
    '/admin/organizations': 'Todas las organizaciones', '/admin/users': 'Gestión de usuarios', '/profile': null,
  };
  for (const [route, label] of Object.entries(expected)) assert.equal(labelsAt(route), label, route);
});
test('analysis items without a project have no destination and are never current', () => {
  for (const [projects, loading, reason] of [[[], false, 'Sin proyectos'], [[project], false, 'Elegí un proyecto'], [[], true, undefined]]) {
    Object.assign(state, { project: null, projects, loading, role: 'ADMIN', member: true });
    const groups = useNavigationTree();
    const items = groups.find(g => g.title === 'Análisis').items.filter(i => i.requires);
    assert.equal(items.length, 2);
    for (const item of items) {
      assert.equal(item.to, undefined); assert.equal(item.reason, reason);
      assert.ok(['Proyectos', 'Todos los proyectos'].includes(activeLeaf(groups, '/projects').label));
    }
  }
});
test('the configuration entry has a short label, a full accessible name and its own icon', () => {
  const analysis = NAV_GROUPS.find(g => g.title === 'Análisis').items.find(i => i.requires === 'scenarioConfiguration');
  const projects = NAV_GROUPS[0].items.find(i => i.id === 'projects');
  assert.equal(analysis.label, 'Configuración');
  assert.equal(analysis.fullLabel, 'Configuración del Escenario');
  assert.notEqual(analysis.icon.type, projects.icon.type);
});
