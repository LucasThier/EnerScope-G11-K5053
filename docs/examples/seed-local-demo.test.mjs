// Unit tests for the local demo seed: guard, options, credentials and the pure data builders.
// Run: node --test docs/examples/seed-local-demo.test.mjs   (no server, no network)
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import {
  assertLocalTarget, parseArgs, readCredentials, selectCleanup, isDemoMail, isDemoName, economicConfiguration,
  advancedEditor, main, node, USERS, NEUQUINA_NODES, NEUQUINA_CONNECTIONS, AUSTRAL_NODES, AUSTRAL_CONNECTIONS,
  FIXTURE_URL, FIXTURE_FIRST, FIXTURE_SECOND, PREFIX, ORGANIZATION, NEUQUINA, AUSTRAL, ROOT_VERSION, BRANCH_VERSION,
} from './seed-local-demo.mjs';

const LOCAL = { ENERSCOPE_SEED_LOCAL: 'yes' };

test('guardAcceptsLoopbackHttpWithExplicitConfirmation', () => {
  assert.equal(assertLocalTarget('http://localhost:8080/api/v1/', LOCAL), 'http://localhost:8080/api/v1');
  assert.equal(assertLocalTarget('http://127.0.0.1:18080/api/v1', LOCAL), 'http://127.0.0.1:18080/api/v1');
  assert.equal(assertLocalTarget('http://[::1]:8080/api/v1', LOCAL), 'http://[::1]:8080/api/v1');
});

test('guardRejectsMissingOrWrongLocalConfirmation', () => {
  for (const env of [{}, { ENERSCOPE_SEED_LOCAL: '' }, { ENERSCOPE_SEED_LOCAL: 'true' }, { ENERSCOPE_SEED_LOCAL: 'YES' }]) {
    assert.throws(() => assertLocalTarget('http://localhost:8080/api/v1', env), /ENERSCOPE_SEED_LOCAL=yes/);
  }
});

test('guardRejectsRemoteHosts', () => {
  for (const url of ['http://enerscope.org/api/v1', 'http://api.enerscope.org:8080/api/v1', 'http://10.0.0.5:8080/api/v1',
    'http://localhost.enerscope.org/api/v1', 'http://192.168.1.10:8080/api/v1']) {
    assert.throws(() => assertLocalTarget(url, LOCAL), /no es local/, url);
  }
});

test('guardRejectsHttpsInvalidUrlsAndEmbeddedCredentials', () => {
  assert.throws(() => assertLocalTarget('https://localhost:8080/api/v1', LOCAL), /solo se acepta http/);
  assert.throws(() => assertLocalTarget('not a url', LOCAL), /inválida/);
  assert.throws(() => assertLocalTarget('http://user:pw@localhost:8080/api/v1', LOCAL), /credenciales/);
});

test('mainStopsAtTheGuardBeforeAnyNetworkCall', async () => {
  // fetch must never be reached: replace it with one that fails the test.
  const original = globalThis.fetch;
  globalThis.fetch = () => { throw new Error('network reached'); };
  try {
    const env = { ENERSCOPE_ADMIN_PASSWORD: 'x', ENERSCOPE_DEMO_PASSWORD: 'yyyyyyyy' };
    await assert.rejects(main([], env), /ENERSCOPE_SEED_LOCAL=yes/);
    await assert.rejects(main(['--api', 'http://enerscope.org/api/v1'], { ...env, ...LOCAL }), /no es local/);
    await assert.rejects(main([], { ...env, ...LOCAL, ENERSCOPE_API: 'https://enerscope.org/api/v1' }), /solo se acepta http/);
    await assert.rejects(main(['--cleanup', '--yes', '--api', 'http://qa.enerscope.org/api/v1'], { ...env, ...LOCAL }), /no es local/);
  } finally {
    globalThis.fetch = original;
  }
});

test('passwordsAreRequiredWithoutDefaults', () => {
  assert.throws(() => readCredentials({}, { needDemo: true }), /ENERSCOPE_ADMIN_PASSWORD, ENERSCOPE_DEMO_PASSWORD/);
  assert.throws(() => readCredentials({ ENERSCOPE_ADMIN_PASSWORD: 'a' }, { needDemo: true }), /ENERSCOPE_DEMO_PASSWORD/);
  assert.throws(() => readCredentials({ ENERSCOPE_ADMIN_PASSWORD: 'a', ENERSCOPE_DEMO_PASSWORD: 'short' }, { needDemo: true }), /8 caracteres/);
  const cleanup = readCredentials({ ENERSCOPE_ADMIN_PASSWORD: 'a' }, { needDemo: false });
  assert.equal(cleanup.demoPassword, null);
  assert.equal(cleanup.adminMail, 'admin@enerscope.org');
});

test('missingPasswordErrorNeverEchoesValues', () => {
  try {
    readCredentials({ ENERSCOPE_ADMIN_PASSWORD: 'super-secret-value', ENERSCOPE_DEMO_PASSWORD: 'tiny' }, { needDemo: true });
    assert.fail('expected an error');
  } catch (error) {
    assert.doesNotMatch(error.message, /super-secret-value|tiny/);
  }
});

test('parseArgsAcceptsTheDocumentedModes', () => {
  assert.deepEqual(parseArgs([]), { simulate: false, cleanup: false, dryRun: false, yes: false, api: null });
  assert.equal(parseArgs(['--simulate']).simulate, true);
  assert.equal(parseArgs(['--cleanup', '--dry-run']).dryRun, true);
  assert.equal(parseArgs(['--cleanup', '--yes']).yes, true);
  assert.equal(parseArgs(['--api', 'http://localhost:1/api/v1']).api, 'http://localhost:1/api/v1');
});

test('cleanupRequiresDryRunOrExplicitYes', () => {
  assert.throws(() => parseArgs(['--cleanup']), /--dry-run.*--yes/);
  assert.throws(() => parseArgs(['--cleanup', '--dry-run', '--yes']), /no los dos/);
  assert.throws(() => parseArgs(['--yes']), /solo valen con --cleanup/);
  assert.throws(() => parseArgs(['--dry-run']), /solo valen con --cleanup/);
  assert.throws(() => parseArgs(['--cleanup', '--yes', '--simulate']), /no se combina/);
  assert.throws(() => parseArgs(['--cleanp']), /desconocida/);
  assert.throws(() => parseArgs(['--api']), /necesita una URL/);
});

test('cleanupSelectsOnlyDemoMarkedData', () => {
  const selection = selectCleanup({
    adminMail: 'admin@enerscope.org',
    organizations: [{ id: 'o1', name: ORGANIZATION }, { id: 'o2', name: 'YPF' }, { id: 'o3', name: 'Demostración real' }],
    projects: [
      { id: 'p1', name: NEUQUINA, organizationId: 'o1' },
      { id: 'p2', name: 'Demo - de otra org', organizationId: 'o2' },
      { id: 'p3', name: 'Proyecto real', organizationId: 'o1' },
    ],
    users: [
      { id: 'u1', mail: 'demo-owner@enerscope.test' }, { id: 'u2', mail: 'admin@enerscope.org' },
      { id: 'u3', mail: 'persona@empresa.com' }, { id: 'u4', mail: 'DEMO-X@ENERSCOPE.TEST' },
      { id: 'u5', mail: 'demo@enerscope.test.evil.com' },
    ],
  });
  assert.deepEqual(selection.organizations.map((o) => o.id), ['o1']);
  assert.deepEqual(selection.projects.map((p) => p.id), ['p1']);
  assert.deepEqual(selection.users.map((u) => u.id), ['u1', 'u4']);
});

test('cleanupNeverSelectsTheBootstrapAdminEvenWithADemoMail', () => {
  const selection = selectCleanup({ adminMail: 'root@enerscope.test', organizations: [], projects: [],
    users: [{ id: 'u1', mail: 'root@enerscope.test' }, { id: 'u2', mail: 'demo-owner@enerscope.test' }] });
  assert.deepEqual(selection.users.map((u) => u.id), ['u2']);
});

test('demoMarkersAreStrict', () => {
  assert.equal(isDemoName('Demo - Base'), true);
  assert.equal(isDemoName('Demo Base'), false);
  assert.equal(isDemoName('demo - base'), false);
  assert.equal(isDemoName(undefined), false);
  assert.equal(isDemoMail('demo-owner@enerscope.test'), true);
  assert.equal(isDemoMail('demo-owner@enerscope.org'), false);
});

test('everyDemoNameCarriesThePrefixAndEveryMailTheDemoDomain', () => {
  for (const name of [ORGANIZATION, NEUQUINA, AUSTRAL, ROOT_VERSION, BRANCH_VERSION]) assert.ok(name.startsWith(PREFIX), name);
  assert.equal(USERS.length, 9);
  assert.equal(new Set(USERS.map((u) => u.mail)).size, 9);
  for (const user of USERS) assert.ok(isDemoMail(user.mail), user.mail);
});

test('usersCoverEveryRoleAndRespectTheRoleRules', () => {
  assert.equal(USERS.filter((u) => u.org === 'OWNER').length, 1);
  assert.equal(USERS.filter((u) => u.platform === 'ADMIN').length, 1);
  const neuquinaRoles = USERS.map((u) => u.projects[NEUQUINA]).filter(Boolean).sort();
  assert.deepEqual(neuquinaRoles, ['ADMIN', 'EDITOR', 'EDITOR', 'EDITOR', 'EDITOR', 'EXECUTIVE']);
  for (const user of USERS) {
    for (const role of user.versions) {
      // A version role needs project membership; an EXECUTIVE never holds one; only EDITOR carries a vertical.
      assert.ok(user.projects[NEUQUINA], `${user.mail} needs to be in the project`);
      assert.notEqual(user.projects[NEUQUINA], 'EXECUTIVE');
      assert.equal(role.memberType === 'EDITOR', role.vertical !== null, user.mail);
    }
  }
  const versionAdmin = USERS.find((u) => u.key === 'versionAdmin');
  assert.equal(versionAdmin.projects[NEUQUINA], 'EDITOR');
  assert.deepEqual(versionAdmin.versions, [{ version: BRANCH_VERSION, memberType: 'ADMIN', vertical: null }]);
  const noAccess = USERS.find((u) => u.key === 'noAccess');
  assert.deepEqual(noAccess.projects, {});
});

test('rootNetworkCoversAllTwelveNodeTypesWithCoherentConnections', () => {
  const types = new Set(NEUQUINA_NODES.map((n) => n.type.nodeType));
  assert.equal(NEUQUINA_NODES.length, 13);
  assert.equal(types.size, 12);
  const names = new Set(NEUQUINA_NODES.map((n) => n.name));
  assert.equal(names.size, NEUQUINA_NODES.length, 'node names are unique');
  const byName = Object.fromEntries(NEUQUINA_NODES.map((n) => [n.name, n]));
  for (const [from, to] of [...NEUQUINA_CONNECTIONS]) {
    assert.ok(byName[from] && byName[to], `${from} → ${to}`);
    assert.notEqual(byName[from].type.role, 'SINK', `a sink does not feed anything: ${from}`);
    assert.notEqual(byName[to].type.role, 'GENERATOR', `nothing feeds a generator: ${to}`);
  }
  const connected = new Set(NEUQUINA_CONNECTIONS.flat());
  assert.deepEqual([...names].filter((n) => !connected.has(n)), [], 'every node is connected');
  const australByName = Object.fromEntries(AUSTRAL_NODES.map((n) => [n.name, n]));
  for (const [from, to] of AUSTRAL_CONNECTIONS) {
    assert.ok(australByName[from] && australByName[to], `${from} → ${to}`);
    assert.notEqual(australByName[to].type.role, 'GENERATOR', `nothing feeds a generator: ${to}`);
  }
});

test('nodeVerticalAndRoleFollowTheBackendTypeTable', () => {
  const expected = {
    WELL: 'EXTRACTION', GATHERING_NETWORK: 'EXTRACTION', TREATMENT_PLANT: 'EXTRACTION', PIPELINE: 'TRANSPORTATION',
    PIPELINE_CONECTION: 'TRANSPORTATION', COMPRESSING_PLANT: 'TRANSPORTATION', GROUND_LIQUEFACTION_PLANT: 'LIQUEFACTION',
    FLNG_UNIT: 'LIQUEFACTION', SEAPORT_TERMINAL: 'EXPORT', LNG_CAMER: 'EXPORT', INTERNAL_CONSUMPTION: 'INTERNAL_CONSUMPTION',
    INDUSTRIAL_CONSUMPTION: 'INTERNAL_CONSUMPTION',
  };
  for (const n of NEUQUINA_NODES) assert.equal(n.type.vertical, expected[n.type.nodeType], n.name);
  // Same table as backend NodeTypeEnum: read it so a change there fails here.
  const source = readFileSync(new URL('../../backend/src/main/java/org/enerscope/node/model/enums/NodeTypeEnum.java', import.meta.url), 'utf8');
  for (const [type, vertical] of Object.entries(expected)) {
    const match = source.match(new RegExp(`${type}\\(VerticalEnum\\.(\\w+), StructuralRoleEnum\\.(\\w+)\\)`));
    assert.ok(match, type);
    assert.equal(match[1], vertical, type);
    assert.equal(node(type, 'x', 0, 0, {}).type.role, match[2], type);
  }
});

test('economicConfigurationPointsTheFixtureAtTheGivenWells', () => {
  const fixture = JSON.parse(readFileSync(FIXTURE_URL, 'utf8'));
  const config = economicConfiguration(fixture, ['a-id', 'b-id'], { equipmentCost: 1800, fixedFactor: 2 });
  const json = JSON.stringify(config);
  assert.doesNotMatch(json, new RegExp(`${FIXTURE_FIRST}|${FIXTURE_SECOND}`));
  assert.match(json, /a-id/);
  assert.match(json, /b-id/);
  assert.equal(config.assets[0].cost, 1800);
  const fixed = (c) => c.nodeProfiles.flatMap((p) => p.rules).filter((r) => r.driver === 'FIXED').map((r) => r.unitValue);
  assert.deepEqual(fixed(config), fixed(fixture).map((v) => v * 2));
  assert.equal(fixture.assets[0].cost, 1000, 'the fixture itself is not mutated');
});

test('advancedEditorCarriesEveryCollectionAsAnArray', () => {
  const editor = advancedEditor({ taxEntities: [1], boundary: ['a'] });
  assert.equal(editor.mode, 'advanced');
  for (const field of ['taxEntities', 'boundary', 'nodeProfiles', 'adjustments', 'contracts', 'assets', 'taxTreatments', 'conversions']) {
    assert.ok(Array.isArray(editor.value[field]), field);
  }
});
