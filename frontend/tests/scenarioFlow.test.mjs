import test from 'node:test';
import assert from 'node:assert/strict';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createLoader } from './loadModule.mjs';

const root = fileURLToPath(new URL('../src/', import.meta.url));
const load = createLoader();
const rules = load(resolve(root, 'utils/scenarioRules.ts'));
const routes = load(resolve(root, 'utils/scenarioRoutes.ts'));
const workspace = load(resolve(root, 'utils/workspaceRoutes.ts'));
const { translateBackendMessage } = load(resolve(root, 'api/errors.ts'));

// ---- permissions -----------------------------------------------------------------------------------------------
const version = (flags = {}) => ({ versionId: 'v1', versionName: 'V', source: 'PROJECT_FALLBACK', roles: [], effectiveRole: null,
  editableVerticals: [], canView: true, canEdit: false, canEditConnections: false, canSimulate: false, canManage: false,
  canManageRoles: false, canViewEconomicResults: true, canViewEconomicConfiguration: true, canEditEconomicConfiguration: false, ...flags });
const access = (flags = {}, entry = {}) => ({ projectId: 'p1', platformAdmin: false, projectRole: 'EDITOR', projectPermissions: ['VIEW_PROJECT', 'EDIT_PROJECT'],
  canManageProject: false, canCreateRootVersions: false, versions: [version(entry)], ...flags });

const ROLES = {
  EJECUTIVO: access({ projectRole: 'EXECUTIVE', projectPermissions: ['VIEW_PROJECT'] }),
  SIMULADOR: access({}, { canSimulate: true }),
  EDITOR: access({}, { canEdit: true, canSimulate: true }),
  ADMIN_DE_VERSION: access({}, { canEdit: true, canSimulate: true, canManage: true }),
  ADMIN_DEL_PROYECTO: access({ projectRole: 'ADMIN', canManageProject: true }, { canEdit: true, canSimulate: true, canManage: true }),
  ADMIN_DE_PLATAFORMA: access({ platformAdmin: true, projectRole: null, projectPermissions: [] }, { canEdit: true, canSimulate: true, canManage: true }),
};
const EXPECTED = { // canView, canEdit, canDelete, canSimulate
  EJECUTIVO: [true, false, false, false],
  SIMULADOR: [true, false, false, true],
  EDITOR: [true, true, false, true],
  ADMIN_DE_VERSION: [true, true, true, true],
  ADMIN_DEL_PROYECTO: [true, true, true, true],
  ADMIN_DE_PLATAFORMA: [true, true, true, true],
};

test('each role gets the scenario permissions the backend matrix gives it', () => {
  for (const [role, entry] of Object.entries(ROLES)) {
    const p = rules.scenarioPermissions(entry, 'v1');
    assert.deepEqual([p.canView, p.canEdit, p.canDelete, p.canSimulate], EXPECTED[role], role);
    assert.equal(p.reasons.edit === null, p.canEdit, role);
    assert.equal(p.reasons.delete === null, p.canDelete, role);
    assert.equal(p.reasons.simulate === null, p.canSimulate, role);
  }
});

test('permissions deny by default: no access data, or a version the person has no entry for', () => {
  for (const missing of [null, undefined]) {
    const p = rules.scenarioPermissions(missing, 'v1');
    assert.deepEqual([p.canView, p.canEdit, p.canDelete, p.canSimulate], [false, false, false, false]);
    assert.ok(p.reasons.edit && p.reasons.delete && p.reasons.simulate);
  }
  const p = rules.scenarioPermissions(ROLES.ADMIN_DE_VERSION, 'other-version');
  assert.deepEqual([p.canEdit, p.canDelete, p.canSimulate], [false, false, false]);
});

test('a role that cannot edit never edits even with version flags: the project permission is required too', () => {
  const p = rules.scenarioPermissions(access({ projectPermissions: ['VIEW_PROJECT'] }, { canEdit: true, canManage: true, canSimulate: true }), 'v1');
  assert.deepEqual([p.canEdit, p.canDelete, p.canSimulate], [false, false, false]);
});

test('the reason of a blocked action says why: read-only role, missing right, or the state of the scenario', () => {
  assert.match(rules.scenarioPermissions(ROLES.EJECUTIVO, 'v1').reasons.edit, /solo lectura/);
  assert.match(rules.scenarioPermissions(ROLES.SIMULADOR, 'v1').reasons.edit, /editar esta versión/);
  assert.match(rules.scenarioPermissions(ROLES.EDITOR, 'v1').reasons.delete, /administra la versión/);
  const scenario = (extra = {}) => ({ id: 's', name: 'A', configured: true, draftPending: false, status: 'UP_TO_DATE', ...extra });
  const editor = rules.scenarioPermissions(ROLES.EDITOR, 'v1');
  assert.equal(rules.simulateBlockedReason(editor, scenario()), null);
  assert.match(rules.simulateBlockedReason(editor, scenario({ configured: false })), /Configurá/);
  assert.match(rules.simulateBlockedReason(editor, scenario({ draftPending: true })), /borrador pendiente/);
  assert.match(rules.simulateBlockedReason(editor, scenario({ status: 'SIMULATING' })), /en curso/);
  assert.match(rules.simulateBlockedReason(rules.scenarioPermissions(ROLES.EJECUTIVO, 'v1'), scenario()), /solo lectura/);
  assert.equal(rules.createBlockedReason(editor, 3), null);
  assert.match(rules.createBlockedReason(editor, 20), /máximo de 20/);
  assert.match(rules.createBlockedReason(rules.scenarioPermissions(ROLES.SIMULADOR, 'v1'), 1), /editar esta versión/);
  assert.equal(rules.limitReached(19), false);
  assert.equal(rules.limitReached(20), true);
});

// ---- name ------------------------------------------------------------------------------------------------------
test('a scenario name is required, trimmed, at most 120 characters and unique in the version ignoring case', () => {
  const others = [{ id: 'a', name: 'Ampliación' }, { id: 'b', name: 'Escenario base' }];
  assert.deepEqual(rules.validateScenarioName('  Conservador  ', others), { ok: true, name: 'Conservador' });
  for (const blank of ['', '   ']) assert.equal(rules.validateScenarioName(blank, others).ok, false);
  assert.match(rules.validateScenarioName('', others).error, /Escribí un nombre/);
  assert.equal(rules.validateScenarioName('x'.repeat(120), others).ok, true);
  assert.match(rules.validateScenarioName('x'.repeat(121), others).error, /120/);
  assert.match(rules.validateScenarioName('AMPLIACIÓN', others).error, /Ya hay un escenario/);
  assert.match(rules.validateScenarioName(' escenario BASE ', others).error, /Ya hay un escenario/);
});

test('renaming lets a scenario keep or re-case its own name but not take another one', () => {
  const others = [{ id: 'a', name: 'Ampliación' }, { id: 'b', name: 'Conservador' }];
  assert.equal(rules.validateScenarioName('AMPLIACIÓN', others, 'a').ok, true);
  assert.equal(rules.validateScenarioName('conservador', others, 'a').ok, false);
});

// ---- status ----------------------------------------------------------------------------------------------------
test('the status the API returns reads in plain words, and a changed base carries the notice', () => {
  assert.deepEqual(['UP_TO_DATE', 'OUTDATED', 'NEVER_SIMULATED', 'SIMULATING'].map(s => rules.scenarioStatusView(s).label),
    ['Al día', 'Desactualizado', 'Sin simular', 'Simulando…']);
  assert.equal(rules.scenarioStatusView('OUTDATED').tone, 'warn');
  assert.equal(rules.scenarioStatusView('UP_TO_DATE').tone, 'good');
  assert.equal(rules.OUTDATED_NOTICE, 'La base cambió: revisá este escenario.');
  assert.equal(rules.IGNORED_OPERATIONS_NOTICE, 'Este borrador traía cambios de operación; editalos en el nodo.');
  assert.match(rules.DELETE_SCENARIO_NOTE, /resultados y las comparaciones guardadas/);
});

test('the default scenario is the one marked, else the first', () => {
  assert.equal(rules.defaultScenarioOf([{ id: 'a', defaultScenario: false }, { id: 'b', defaultScenario: true }]).id, 'b');
  assert.equal(rules.defaultScenarioOf([{ id: 'a', defaultScenario: false }]).id, 'a');
  assert.equal(rules.defaultScenarioOf([]), null);
});

// ---- translations ----------------------------------------------------------------------------------------------
test('every scenario error the backend can answer has its own plain Spanish message', () => {
  const cases = [
    ['Ya existe un escenario llamado «Ampliación» en esta versión. Elegí otro nombre (no se distinguen mayúsculas).', /Ya hay un escenario con ese nombre/],
    ['Una versión admite como máximo 20 escenarios. Eliminá alguno antes de crear otro.', /máximo de 20 escenarios/],
    ['No se puede eliminar el único escenario de la versión.', /siempre conserva al menos uno/],
    ['El escenario tiene una simulación en cola o en un curso. Esperá.', null],
    ['El escenario tiene una simulación en cola o en curso. Esperá a que termine para eliminarlo.', /en cola o en curso/],
    ['El escenario cambió desde otra pantalla. Conservá tus cambios y recargá la configuración antes de guardar.', /recargá/],
    ['Los valores de operación de los nodos (Pozo 1) se editan en cada nodo de la versión, no en el escenario.', /Editar operación en la versión/],
    ['Scenario not found', /no existe, fue eliminado o no pertenece/],
    ['El nombre del escenario es obligatorio.', /Escribí un nombre/],
    ['El nombre del escenario admite hasta 120 caracteres.', /120 caracteres/],
    ['La versión tiene simulaciones o comparaciones guardadas y no se puede eliminar.', /alguna que deriva de ella, tiene simulaciones o comparaciones guardadas/],
  ];
  for (const [message, expected] of cases) {
    if (expected) assert.match(translateBackendMessage(message), expected, message);
  }
  assert.equal(translateBackendMessage('Something unknown'), 'Something unknown');
});

// ---- routes ----------------------------------------------------------------------------------------------------
test('scenario routes are built under the version and keep each module to its own sidebar entry', () => {
  assert.equal(routes.versionScenariosPath('p1', 'v1'), '/projects/p1/versions/v1/scenarios');
  assert.equal(routes.versionSimulationsPath('p1', 'v1'), '/projects/p1/versions/v1/simulations');
  assert.equal(routes.scenarioConfigurationPath('p1', 'v1', 's1'), '/projects/p1/versions/v1/scenarios/s1/configuration');
  assert.equal(routes.scenarioResultsPath('p1', 'v1', 's1', 'e1'), '/projects/p1/versions/v1/scenarios/s1/results/e1');
  assert.equal(routes.versionOperationPath('v1'), '/editor?versionId=v1');
  const { CONFIGURATION_PATTERN: config, SIMULATION_PATTERN: simulation, VERSION_MANAGEMENT_PATTERN: versioning } = workspace;
  for (const path of ['scenario-configurations', 'versions/v1/scenarios', 'versions/v1/scenarios/s1/configuration', 'versions/v1/economics']) {
    assert.ok(config.test('/projects/p1/' + path), path);
    assert.equal(simulation.test('/projects/p1/' + path), false, path);
    assert.equal(versioning.test('/projects/p1/' + path), false, path);
  }
  for (const path of ['simulations', 'versions/v1/simulations', 'versions/v1/scenarios/s1/history', 'versions/v1/scenarios/s1/results', 'versions/v1/scenarios/s1/results/e1']) {
    assert.ok(simulation.test('/projects/p1/' + path), path);
    assert.equal(config.test('/projects/p1/' + path), false, path);
    assert.equal(versioning.test('/projects/p1/' + path), false, path);
  }
});

test('switching projects from any scenario screen opens that module list of the new project, never a foreign id', () => {
  for (const path of ['versions/v1/scenarios', 'versions/v1/scenarios/s1/configuration']) assert.equal(workspace.projectDestination('p2', path), '/projects/p2/scenario-configurations');
  for (const path of ['versions/v1/simulations', 'versions/v1/scenarios/s1/history', 'versions/v1/scenarios/s1/results/e1']) assert.equal(workspace.projectDestination('p2', path), '/projects/p2/simulations');
});

test('the old version-level economics routes are read and sent to the default scenario', () => {
  const read = routes.parseLegacyEconomicsPath;
  assert.deepEqual(read('/projects/p1/versions/v1/economics'), { projectId: 'p1', versionId: 'v1', tab: 'configuration', evaluationId: undefined });
  assert.equal(read('/projects/p1/versions/v1/economics/configuration').tab, 'configuration');
  assert.equal(read('/projects/p1/versions/v1/economics/history/').tab, 'history');
  assert.deepEqual(read('/projects/p1/versions/v1/economics/results/e9'), { projectId: 'p1', versionId: 'v1', tab: 'results', evaluationId: 'e9' });
  for (const other of ['/projects/p1/versions/v1/scenarios', '/projects/p1/versions/v1/economics/other', '/projects/p1/versions/v1/economics/configuration/e1', '/projects/p1/economics']) assert.equal(read(other), null, other);
  const target = (path) => routes.legacyEconomicsTarget(read(path), 's0');
  assert.equal(target('/projects/p1/versions/v1/economics'), '/projects/p1/versions/v1/scenarios/s0/configuration');
  assert.equal(target('/projects/p1/versions/v1/economics/history'), '/projects/p1/versions/v1/scenarios/s0/history');
  assert.equal(target('/projects/p1/versions/v1/economics/results'), '/projects/p1/versions/v1/scenarios/s0/results');
  assert.equal(target('/projects/p1/versions/v1/economics/results/e9'), '/projects/p1/versions/v1/scenarios/s0/results/e9');
});
