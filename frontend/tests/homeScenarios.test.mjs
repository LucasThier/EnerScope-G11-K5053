import test from 'node:test';
import assert from 'node:assert/strict';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createLoader } from './loadModule.mjs';

const root = fileURLToPath(new URL('../src/', import.meta.url));
const load = createLoader();
const home = load(resolve(root, 'utils/homeScenarios.ts'));
const rules = load(resolve(root, 'utils/scenarioRules.ts'));
const { formatRelative } = load(resolve(root, 'utils/date.ts'));
const { mostRecentProject } = load(resolve(root, 'utils/recentProjects.ts'));

const scenario = (extra = {}) => ({
  id: 's1', versionId: 'v1', name: 'Escenario base', defaultScenario: false, configured: true, draftPending: false,
  status: 'UP_TO_DATE', latestEvaluatedAt: '2026-11-20T10:00:00Z', createdAt: '2026-11-01T10:00:00Z', lastModified: '2026-11-20T10:00:00Z', ...extra,
});
const versionAccess = (flags = {}) => ({
  versionId: 'v1', versionName: 'V', source: 'PROJECT_FALLBACK', roles: [], effectiveRole: null, editableVerticals: [], canView: true,
  canEdit: false, canEditConnections: false, canSimulate: false, canManage: false, canManageRoles: false, canViewEconomicResults: true,
  canViewEconomicConfiguration: true, canEditEconomicConfiguration: false, ...flags,
});
const access = (flags = {}, entries = [versionAccess()]) => ({
  projectId: 'p1', platformAdmin: false, projectRole: 'EDITOR', projectPermissions: ['VIEW_PROJECT', 'EDIT_PROJECT'],
  canManageProject: false, canCreateRootVersions: false, versions: entries, ...flags,
});
const permsOf = (entry) => rules.scenarioPermissions(access({}, [versionAccess(entry)]), 'v1');
const IDS = { projectId: 'p1', versionId: 'v1' };

// ---- chips: texts, tooltips and priority ------------------------------------------------------------------------------
test('each status chip keeps the app wording and carries a one-line meaning', () => {
  const labels = ['UP_TO_DATE', 'OUTDATED', 'SIMULATING', 'NEVER_SIMULATED'].map((status) => home.scenarioChips(scenario({ status }))[0]);
  assert.deepEqual(labels.map((chip) => chip.label), ['Al día', 'Desactualizado', 'Simulando…', 'Sin simular']);
  assert.deepEqual(labels.map((chip) => chip.tone), ['good', 'warn', 'info', 'neutral']);
  for (const chip of labels) {
    assert.ok(chip.hint.length > 10 && !chip.hint.includes('\n'), chip.label);
    assert.equal(chip.id, 'status');
    assert.equal(chip.essential, true);
  }
  assert.deepEqual(Object.keys(rules.SCENARIO_STATUS_HINTS).sort(), ['NEVER_SIMULATED', 'OUTDATED', 'SIMULATING', 'UP_TO_DATE']);
  assert.match(rules.DRAFT_HINT, /borrador sin publicar/);
});

test('a draft adds a "Borrador" chip after the status, never instead of it', () => {
  for (const status of ['UP_TO_DATE', 'OUTDATED', 'SIMULATING', 'NEVER_SIMULATED']) {
    const chips = home.scenarioChips(scenario({ status, draftPending: true }));
    assert.deepEqual(chips.map((chip) => chip.id), ['status', 'draft'], status);
    assert.equal(chips[1].label, 'Borrador');
    assert.equal(chips[1].tone, 'warn');
    assert.equal(chips[1].hint, rules.DRAFT_HINT);
  }
  assert.equal(home.scenarioChips(scenario({ draftPending: false })).length, 1);
});

test('when only one chip fits, Simulando > Borrador > Desactualizado/Sin simular > Al día', () => {
  const essential = (status, draftPending) => home.scenarioChips(scenario({ status, draftPending })).filter((chip) => chip.essential).map((chip) => chip.id);
  assert.deepEqual(essential('SIMULATING', true), ['status']);
  assert.deepEqual(essential('OUTDATED', true), ['draft']);
  assert.deepEqual(essential('NEVER_SIMULATED', true), ['draft']);
  assert.deepEqual(essential('UP_TO_DATE', true), ['draft']);
  assert.deepEqual(essential('OUTDATED', false), ['status']);
});

// ---- the action of a row -----------------------------------------------------------------------------------------------
test('Simular for what needs a run and may be run; Ver (to the right screen) for the rest', () => {
  const simulator = permsOf({ canSimulate: true });
  assert.deepEqual(home.scenarioRowAction(scenario({ status: 'OUTDATED' }), simulator, IDS), { kind: 'simulate' });
  assert.deepEqual(home.scenarioRowAction(scenario({ status: 'NEVER_SIMULATED', latestEvaluatedAt: null }), simulator, IDS), { kind: 'simulate' });
  // up to date: nothing to run, so it opens the results
  assert.deepEqual(home.scenarioRowAction(scenario({ id: 's9' }), simulator, IDS), { kind: 'view', to: '/projects/p1/versions/v1/scenarios/s9/results' });
  // a draft cannot be simulated: it opens the configuration, where it is completed or discarded
  assert.deepEqual(home.scenarioRowAction(scenario({ id: 's9', status: 'OUTDATED', draftPending: true }), simulator, IDS),
    { kind: 'view', to: '/projects/p1/versions/v1/scenarios/s9/configuration' });
  assert.deepEqual(home.scenarioRowAction(scenario({ id: 's9', status: 'NEVER_SIMULATED', configured: false, latestEvaluatedAt: null }), simulator, IDS),
    { kind: 'view', to: '/projects/p1/versions/v1/scenarios/s9/configuration' });
  // outdated without results to show opens the history
  assert.deepEqual(home.scenarioRowAction(scenario({ id: 's9', status: 'NEVER_SIMULATED', latestEvaluatedAt: null }), permsOf({}), IDS),
    { kind: 'view', to: '/projects/p1/versions/v1/scenarios/s9/history' });
});

test('a person who cannot simulate gets no Simular, and nothing while it is running or when the version is not visible', () => {
  const reader = permsOf({ canSimulate: false });
  assert.equal(home.scenarioRowAction(scenario({ status: 'OUTDATED' }), reader, IDS).kind, 'view');
  assert.equal(home.scenarioRowAction(scenario({ status: 'SIMULATING' }), permsOf({ canSimulate: true }), IDS).kind, 'none');
  assert.equal(home.scenarioRowAction(scenario({ status: 'OUTDATED' }), rules.scenarioPermissions(null, 'v1'), IDS).kind, 'none');
  const executive = rules.scenarioPermissions(access({ projectRole: 'EXECUTIVE', projectPermissions: ['VIEW_PROJECT'] }, [versionAccess({ canSimulate: true })]), 'v1');
  assert.equal(home.scenarioRowAction(scenario({ status: 'OUTDATED' }), executive, IDS).kind, 'view', 'a read-only role never simulates');
});

// ---- which version and which rows ----------------------------------------------------------------------------------------
test('the version is the oldest visible root; a branch only when no root is visible', () => {
  const v = (id, parentVersionId, createdAt) => ({ id, name: id, parentVersionId, createdAt });
  const versions = [v('branch', 'root-b', '2026-10-01T00:00:00Z'), v('root-b', null, '2026-09-10T00:00:00Z'), v('root-a', null, '2026-09-01T00:00:00Z')];
  const everything = access({}, versions.map((x) => versionAccess({ versionId: x.id })));
  assert.equal(home.pickHomeVersion(versions, everything).id, 'root-a');
  const noRootA = access({}, versions.map((x) => versionAccess({ versionId: x.id, canView: x.id !== 'root-a' })));
  assert.equal(home.pickHomeVersion(versions, noRootA).id, 'root-b');
  const onlyBranch = access({}, versions.map((x) => versionAccess({ versionId: x.id, canView: x.id === 'branch' })));
  assert.equal(home.pickHomeVersion(versions, onlyBranch).id, 'branch');
  const nothing = access({ projectPermissions: [] }, versions.map((x) => versionAccess({ versionId: x.id, canView: false })));
  assert.equal(home.pickHomeVersion(versions, nothing), null);
  assert.equal(home.pickHomeVersion([], everything), null);
});

test('5 rows, 4 on a short screen; the default scenario first, then the most recently modified', () => {
  assert.equal(home.scenarioRowLimit(false), 5);
  assert.equal(home.scenarioRowLimit(true), 4);
  const list = [
    scenario({ id: 'a', name: 'A', lastModified: '2026-11-01T00:00:00Z' }),
    scenario({ id: 'b', name: 'B', lastModified: '2026-11-10T00:00:00Z' }),
    scenario({ id: 'c', name: 'C', lastModified: '2026-11-05T00:00:00Z', defaultScenario: true }),
    scenario({ id: 'd', name: 'D', lastModified: '2026-11-12T00:00:00Z' }),
    scenario({ id: 'e', name: 'E', lastModified: '2026-11-03T00:00:00Z' }),
    scenario({ id: 'f', name: 'F', lastModified: '2026-11-20T00:00:00Z' }),
  ];
  const five = home.homeScenarioRows(list, home.scenarioRowLimit(false));
  assert.deepEqual(five.rows.map((row) => row.id), ['c', 'f', 'd', 'b', 'e']);
  assert.equal(five.hidden, 1);
  const four = home.homeScenarioRows(list, home.scenarioRowLimit(true));
  assert.deepEqual(four.rows.map((row) => row.id), ['c', 'f', 'd', 'b']);
  assert.equal(four.hidden, 2);
  assert.deepEqual(home.homeScenarioRows([], 5), { rows: [], hidden: 0 });
  assert.equal(list[0].id, 'a', 'the input is not reordered');
});

// ---- quick access chips ---------------------------------------------------------------------------------------------------
const nav = (flags = {}) => {
  const { compare = true, users = true, organizations = true, mine = true } = flags;
  const leaf = (label, extra) => ({ label, ...extra });
  return [
    { title: 'Análisis', items: [leaf('Comparaciones', compare ? { requires: 'scenarioComparison', to: '/projects/p1/comparisons', fullLabel: 'Comparación de Escenarios' } : { requires: 'scenarioComparison' })] },
    {
      title: 'Administración',
      items: [
        { label: 'Organizaciones', children: [
          ...(mine ? [leaf('Mis organizaciones', { to: '/organizations' })] : []),
          ...(organizations ? [leaf('Todas', { to: '/admin/organizations', requires: 'platformAdmin' })] : []),
        ] },
        ...(users ? [leaf('Usuarios', { to: '/admin/users', requires: 'platformAdmin' })] : []),
      ],
    },
  ];
};

test('an administrator gets Nuevo proyecto, Comparar escenarios, Usuarios and Organizaciones', () => {
  const actions = home.quickActionsFor(nav(), { isAdmin: true, canCreateProject: true });
  assert.deepEqual(actions.map((a) => a.id), ['new', 'compare', 'users', 'organizations']);
  assert.deepEqual(actions.map((a) => a.label), ['Nuevo proyecto', 'Comparar escenarios', 'Usuarios', 'Organizaciones']);
  assert.equal(actions[0].to, undefined, 'Nuevo proyecto opens the modal');
  assert.equal(actions[2].to, '/admin/users');
  assert.ok(actions.every((a) => a.hint.length > 0), 'every chip keeps its description for the tooltip');
});

test('everyone else gets Nuevo proyecto, Comparar escenarios, Mis organizaciones and Mi perfil', () => {
  const actions = home.quickActionsFor(nav({ users: false, organizations: false }), { isAdmin: false, canCreateProject: true });
  assert.deepEqual(actions.map((a) => a.id), ['new', 'compare', 'myOrganizations', 'profile']);
  assert.equal(actions[2].label, 'Mis organizaciones');
  assert.equal(actions[3].to, '/profile');
});

test('what the sidebar does not reach is left out, and there are never more than four chips', () => {
  // no active project: the sidebar leaves Comparaciones without an address
  assert.deepEqual(home.quickActionsFor(nav({ compare: false }), { isAdmin: false, canCreateProject: false }).map((a) => a.id), ['myOrganizations', 'profile']);
  // not an organization member
  assert.deepEqual(home.quickActionsFor(nav({ mine: false }), { isAdmin: false, canCreateProject: true }).map((a) => a.id), ['new', 'compare', 'profile']);
  // an admin the sidebar does not give the admin sections to (defensive): they are not invented
  assert.deepEqual(home.quickActionsFor(nav({ users: false, organizations: false }), { isAdmin: true, canCreateProject: true }).map((a) => a.id), ['new', 'compare']);
  assert.deepEqual(home.quickActionsFor([], { isAdmin: false, canCreateProject: false }).map((a) => a.id), ['profile']);
  for (const isAdmin of [true, false]) {
    assert.ok(home.quickActionsFor(nav(), { isAdmin, canCreateProject: true }).length <= home.MAX_QUICK_ACTIONS);
  }
});

test('Comparar points at the project Inicio shows when there is one, else at the sidebar address', () => {
  const shown = home.quickActionsFor(nav(), { isAdmin: true, canCreateProject: false, compareProjectId: 'p 9' }).find((a) => a.id === 'compare');
  assert.equal(shown.to, '/projects/p%209/comparisons');
  const fallback = home.quickActionsFor(nav(), { isAdmin: true, canCreateProject: false }).find((a) => a.id === 'compare');
  assert.equal(fallback.to, '/projects/p1/comparisons');
});

// ---- relative dates and the recent project ----------------------------------------------------------------------------------
test('relative dates read in plain Spanish and fall back to the short date after a month', () => {
  const now = Date.parse('2026-11-23T12:00:00Z');
  const ago = (ms) => new Date(now - ms).toISOString();
  assert.equal(formatRelative(ago(20_000), now), 'recién');
  assert.equal(formatRelative(ago(5 * 60_000), now), 'hace 5 min');
  assert.equal(formatRelative(ago(2 * 3600_000), now), 'hace 2 h');
  assert.equal(formatRelative(ago(30 * 3600_000), now), 'ayer');
  assert.equal(formatRelative(ago(3 * 86_400_000), now), 'hace 3 días');
  assert.match(formatRelative('2026-09-01T12:00:00Z', now), /^1 sep\w* 2026$/);
  assert.equal(formatRelative(new Date(now + 60_000).toISOString(), now), 'recién');
  assert.equal(formatRelative(null, now), null);
  assert.equal(formatRelative('no es una fecha', now), null);
});

test('the recent project is the most recently modified one, and none when recency is unreliable', () => {
  const project = (id, lastModified) => ({ id, name: id, lastModified });
  assert.equal(mostRecentProject([project('a', '2026-11-01T00:00:00Z'), project('b', '2026-11-20T00:00:00Z')]).id, 'b');
  assert.equal(mostRecentProject([project('a', 'no es una fecha'), project('b', 'x')]), null);
  assert.equal(mostRecentProject([]), null);
});

// ---- the footer of the Escenarios block ----------------------------------------------------------------------------------------
const footer = (extra) => home.scenarioFooter({ total: 3, rows: 3, pending: 0, canCreate: true, hasLastComparison: false, ...extra });

test('no scenario: say so and offer Crear escenario only to who may create', () => {
  assert.deepEqual(footer({ total: 0, rows: 0 }), { kind: 'create', canCreate: true });
  assert.deepEqual(footer({ total: 0, rows: 0, canCreate: false }), { kind: 'create', canCreate: false });
});

test('one scenario: suggest a second one, with the button only for who may create', () => {
  assert.deepEqual(footer({ total: 1, rows: 1 }), { kind: 'add', canCreate: true });
  assert.deepEqual(footer({ total: 1, rows: 1, canCreate: false }), { kind: 'add', canCreate: false });
  // the suggestion wins even if that one scenario is itself pending or a comparison exists
  assert.equal(footer({ total: 1, rows: 1, pending: 1, hasLastComparison: true }).kind, 'add');
});

test('two or three rows with something to simulate: just the count, and a singular', () => {
  assert.deepEqual(footer({ total: 2, rows: 2, pending: 1 }), { kind: 'pending', count: 1 });
  assert.deepEqual(footer({ total: 3, rows: 3, pending: 2, hasLastComparison: true }), { kind: 'pending', count: 2 });
  assert.equal(home.pendingLabel(1), '1 escenario por simular');
  assert.equal(home.pendingLabel(3), '3 escenarios por simular');
  assert.equal(home.pendingScenarios([{ status: 'OUTDATED' }, { status: 'NEVER_SIMULATED' }, { status: 'SIMULATING' }, { status: 'UP_TO_DATE' }]), 2);
});

test('everything up to date: the last saved comparison when there is one, else nothing', () => {
  assert.deepEqual(footer({ hasLastComparison: true }), { kind: 'comparison' });
  assert.equal(footer({ hasLastComparison: false }), null);
  assert.deepEqual(footer({ total: 2, rows: 2, hasLastComparison: true }), { kind: 'comparison' });
});

test('four or more rows fill the block and take no footer', () => {
  assert.equal(home.FILLED_ROWS, 4);
  assert.equal(footer({ total: 6, rows: 4, pending: 3, hasLastComparison: true }), null);
  assert.equal(footer({ total: 5, rows: 5, pending: 1 }), null);
});

test('Comparar warns about the two-scenario minimum below it, and the secondary action has an outline variant', () => {
  assert.equal(home.compareHint(0), 'Se necesitan al menos 2 escenarios');
  assert.equal(home.compareHint(1), 'Se necesitan al menos 2 escenarios');
  assert.equal(home.compareHint(2), undefined);
  const { buttonClasses } = load(resolve(root, 'components/ui/buttonClasses.ts'));
  const outline = buttonClasses('outline', 'compact').split(/\s+/);
  assert.ok(outline.includes('border-control-border') && outline.includes('bg-transparent'), 'an edge and no fill');
  assert.ok(!buttonClasses('outline', 'compact').includes('bg-fill-neutral'), 'lighter than the secondary button');
});
