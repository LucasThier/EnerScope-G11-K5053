import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';
const require = createRequire(import.meta.url);
let activeProject = null;
const cache = new Map();
function load(path) {
  if (cache.has(path)) return cache.get(path).exports;
  const module = { exports: {} }; cache.set(path, module);
  const code = ts.transpileModule(readFileSync(path, 'utf8'), { compilerOptions: {
    module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, target: ts.ScriptTarget.ES2022,
  }}).outputText;
  const localRequire = name => {
    if (name === 'react') return { useMemo: fn => fn() };
    if (name === './useActiveProject') return { useActiveProject: () => ({ activeProject, isLoading: false }) };
    if (name === './useAuth') return { useAuth: () => ({ user: { platformRole: 'USER' } }) };
    if (name === './useMyOrganizations') return { useMyOrganizations: () => ({ belongsToOrganization: false, loading: false }) };
    if (!name.startsWith('.')) return require(name);
    const base = resolve(dirname(path), name);
    const file = [base + '.ts', base + '.tsx', base + '/index.tsx'].find(existsSync);
    return load(file);
  };
  new Function('require', 'module', 'exports', code)(localRequire, module, module.exports);
  return module.exports;
}
const root = fileURLToPath(new URL('../src/', import.meta.url));
const nav = load(resolve(root, 'components/layout/navItems.tsx'));
const { useNavigationTree } = load(resolve(root, 'hooks/useNavigationTree.ts'));
test('economic links follow the active project and preserve navigation groups', () => {
  for (const id of ['project-a', 'project-b']) {
    activeProject = { id, name: id, myRole: 'OWNER' };
    const groups = useNavigationTree();
    assert.deepEqual(groups.slice(0, 2).map(g => g.title), ['Trabajo', 'Análisis']);
    const items = groups.find(g => g.title === 'Análisis').items;
    assert.equal(items.find(i => i.requires === 'scenarioConfiguration').to, '/projects/' + id + '/scenario-configurations');
    assert.equal(items.find(i => i.requires === 'scenarioSimulation').to, '/projects/' + id + '/simulations');
    const comparisons = items.find(i => i.label === 'Comparaciones');
    assert.equal(comparisons.availability, undefined);
    assert.equal(comparisons.to, '/projects/' + id + '/comparisons');
  }
});
test('economic links lead to project selection without an active project', () => {
  activeProject = null;
  const items = useNavigationTree().find(g => g.title === 'Análisis').items;
  for (const requirement of ['scenarioConfiguration', 'scenarioSimulation', 'scenarioComparison']) {
    const item = items.find(i => i.requires === requirement);
    assert.equal(item.to, '/projects'); assert.equal(item.availability, undefined);
  }
});
test('economic routes keep their module titles and project breadcrumbs', () => {
  for (const [suffix, title] of [
    ['scenario-configurations', 'Configuración del Escenario'],
    ['versions/v/economics/configuration', 'Configuración del Escenario'],
    ['simulations', 'Simulación de Escenarios'],
    ['versions/v/economics/history', 'Simulación de Escenarios'],
    ['versions/v/economics/results/e', 'Simulación de Escenarios'],
    ['comparisons', 'Comparación de Escenarios'],
    ['comparisons/c1', 'Comparación de Escenarios'],
  ]) {
    const path = '/projects/p/' + suffix;
    assert.equal(nav.moduleTitleFor(path), title);
    assert.equal(nav.VERSIONS_PATH_PATTERN.test(path), false);
    assert.deepEqual(nav.breadcrumbsFor(path, 'Proyecto').map(c => c.label), ['Proyectos', 'Proyecto', title]);
    assert.equal(nav.documentSectionFor(path, 'Proyecto', null), title + ' · Proyecto');
  }
  assert.equal(nav.moduleTitleFor('/projects/p/versions'), 'Versionado');
});
