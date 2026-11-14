import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';
const require = createRequire(import.meta.url);
const state = { projects: [], stored: null, loading: false, error: null, pathname: '/' };
const cache = new Map();
const matchProject = pathname => {
  const found = /^\/projects\/([^/]+)(?:\/(.*))?$/.exec(pathname);
  return found ? { params: { projectId: found[1], '*': found[2] ?? '' } } : null;
};
function load(path) {
  if (cache.has(path)) return cache.get(path).exports;
  const module = { exports: {} }; cache.set(path, module);
  const code = ts.transpileModule(readFileSync(path, 'utf8'), { compilerOptions: {
    module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, target: ts.ScriptTarget.ES2022,
  }}).outputText;
  const localRequire = name => {
    if (name === 'react/jsx-runtime') return { jsx: (type, props) => (typeof type === 'function' ? type(props) : { type, props }) };
    if (name === 'react-router-dom') return { Navigate: props => ({ navigate: props.to }), Outlet: () => 'outlet', useMatch: () => matchProject(state.pathname) };
    if (name.endsWith('/useActiveProject')) return { PROJECT_ROUTE_PATTERN: '/projects/:projectId/*', useActiveProject: () => ({ projects: state.projects, storedProjectId: state.stored, isLoading: state.loading, error: state.error }) };
    if (name.endsWith('/PageLoader')) return { PageLoader: () => 'loader' };
    if (!name.startsWith('.')) return require(name);
    const base = resolve(dirname(path), name);
    return load([base + '.ts', base + '.tsx', base + '/index.tsx'].find(existsSync));
  };
  new Function('require', 'module', 'exports', code)(localRequire, module, module.exports);
  return module.exports;
}
const root = fileURLToPath(new URL('../src/', import.meta.url));
const { resolveActiveProject, projectRouteRedirect, versionsIndexRedirect, isProjectAccessLoss } = load(resolve(root, 'utils/activeProject.ts'));
const { ProjectRouteGuard } = load(resolve(root, 'routes/ProjectRouteGuard.tsx'));
const p1 = { id: 'p1' }, p2 = { id: 'p2' };

test('the route project wins, then the stored one, then the first; a foreign route id resolves to none', () => {
  assert.equal(resolveActiveProject([p1, p2], 'p2', 'p1'), p2);
  assert.equal(resolveActiveProject([p1, p2], null, 'p2'), p2);
  assert.equal(resolveActiveProject([p1, p2], null, 'gone'), p1);
  assert.equal(resolveActiveProject([p1, p2], null, null), p1);
  assert.equal(resolveActiveProject([p1, p2], 'foreign', 'p2'), null);
  assert.equal(resolveActiveProject([], null, 'p1'), null);
});

test('a listed route project is never redirected', () => {
  assert.equal(projectRouteRedirect([p1, p2], 'p2', 'p1', 'versions'), null);
});

test('a foreign or deleted route project falls back to the same module of a listed project', () => {
  assert.equal(projectRouteRedirect([p1, p2], 'gone', 'p2', 'versions'), '/projects/p2/versions');
  assert.equal(projectRouteRedirect([p1, p2], 'gone', 'missing', 'versions/v9/economics/results/e1'), '/projects/p1/simulations');
  assert.equal(projectRouteRedirect([p1, p2], 'gone', null, 'versions/v9/economics/configuration'), '/projects/p1/scenario-configurations');
  assert.equal(projectRouteRedirect([p1], 'gone', null, 'team'), '/projects/p1/team');
  assert.equal(projectRouteRedirect([], 'gone', 'gone', 'versions'), '/projects');
});

const guardOutcome = pathname => {
  state.pathname = pathname;
  const out = ProjectRouteGuard();
  return out && typeof out === 'object' && 'navigate' in out ? out.navigate : out;
};
const followRedirects = start => {
  const visited = [start];
  let current = start;
  for (let hop = 0; hop < 5; hop++) {
    const next = current === '/versions'
      ? versionsIndexRedirect(resolveActiveProject(state.projects, null, state.stored)?.id ?? null)
      : (() => { const out = guardOutcome(current); return out === 'outlet' || out === 'loader' ? null : out; })();
    if (next === null) return visited;
    assert.ok(!visited.includes(next), `redirect loop: ${[...visited, next].join(' -> ')}`);
    visited.push(next);
    current = next;
  }
  assert.fail(`too many redirects from ${start}: ${visited.join(' -> ')}`);
};

test('guard renders the page, a loader or a redirect according to the project list', () => {
  Object.assign(state, { projects: [p1, p2], stored: 'p1', loading: false, error: null });
  assert.equal(guardOutcome('/projects/p2/versions'), 'outlet');
  assert.equal(guardOutcome('/projects/gone/simulations'), '/projects/p1/simulations');
  assert.equal(guardOutcome('/projects'), 'outlet');
  Object.assign(state, { projects: [], loading: true });
  assert.equal(guardOutcome('/projects/p1/versions'), 'loader');
  Object.assign(state, { projects: [p1], loading: true });
  assert.equal(guardOutcome('/projects/p1/versions'), 'outlet');
  Object.assign(state, { projects: [], loading: false, error: 'boom' });
  assert.equal(guardOutcome('/projects/p1/versions'), 'outlet');
});

test('no redirect loops: stale stored project with an empty list, deleted projects and the versions index', () => {
  const routes = ['/versions', '/projects/gone/versions', '/projects/gone/team', '/projects/gone/simulations',
    '/projects/gone/scenario-configurations', '/projects/gone/versions/v/economics/results/e', '/projects/p1/versions'];
  for (const [projects, stored] of [[[], 'gone'], [[], null], [[p1], 'gone'], [[p1, p2], 'p2'], [[p1, p2], null]]) {
    Object.assign(state, { projects, stored, loading: false, error: null });
    for (const route of routes) {
      const visited = followRedirects(route);
      assert.ok(visited.length <= 3, `${route}: ${visited.join(' -> ')}`);
      const last = visited[visited.length - 1];
      if (projects.length === 0) assert.ok(['/projects', '/versions'].includes(last), `${route} ended at ${last}`);
      else assert.ok(projects.some(p => last.startsWith(`/projects/${p.id}/`)), `${route} ended at ${last}`);
    }
  }
});

test('versions index redirects only when a project is active', () => {
  assert.equal(versionsIndexRedirect('p1'), '/projects/p1/versions');
  assert.equal(versionsIndexRedirect(null), null);
});

test('only lost access or a missing project on a read triggers a project list reload', () => {
  assert.equal(isProjectAccessLoss(403), true);
  assert.equal(isProjectAccessLoss(404), true);
  for (const status of [null, 400, 401, 409, 500]) assert.equal(isProjectAccessLoss(status), false);
});

test('save and mutation handlers never reload the project list on a forbidden response', () => {
  const sources = ['pages/ScenarioConfigurationPage.tsx', 'pages/EconomicsPage.tsx', 'pages/ScenariosPage.tsx']
    .map(file => readFileSync(resolve(root, file), 'utf8'));
  for (const source of sources) assert.equal(source.match(/isProjectAccessLoss\(/g)?.length, 1);
});
