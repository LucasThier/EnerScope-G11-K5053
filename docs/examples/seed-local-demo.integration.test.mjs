// Integration test for the local demo seed, against a RUNNING backend with a disposable database.
// Skipped unless ENERSCOPE_SEED_IT_API is set; the same double guard as the script applies.
//
//   ENERSCOPE_SEED_IT_API=http://localhost:18080/api/v1 ENERSCOPE_SEED_LOCAL=yes \
//   ENERSCOPE_ADMIN_PASSWORD= ENERSCOPE_DEMO_PASSWORD= \
//     node --test docs/examples/seed-local-demo.integration.test.mjs
//
// Start from an empty database (only the bootstrap admin) to cover "seed on an empty database". Never point it at
// a database with real data: it creates the demo data and leaves it there.
import test from 'node:test';
import assert from 'node:assert/strict';
import {
  assertLocalTarget, client, readCredentials, seed, USERS, NEUQUINA, AUSTRAL, ROOT_VERSION, BRANCH_VERSION,
  ORGANIZATION, COMPARISON, DRAFT_SCENARIO, HIGH_COSTS_SCENARIO, DEFAULT_SCENARIO,
} from './seed-local-demo.mjs';

const target = process.env.ENERSCOPE_SEED_IT_API;
const skip = target ? false : 'set ENERSCOPE_SEED_IT_API to a local backend with a disposable database';

/** Everything the seed owns, read through the API as the platform admin, in a comparable form. */
async function snapshot(api, credentials) {
  const call = api.call;
  await api.login(credentials.adminMail, credentials.adminPassword);
  const users = (await call('GET', '/users')).filter((u) => u.mail.endsWith('@enerscope.test'))
    .map((u) => `${u.mail}:${u.platformRole}:${u.active}`).sort();
  const organizations = (await call('GET', '/organizations')).filter((o) => o.name === ORGANIZATION);
  const orgMembers = (await call('GET', `/organizations/${organizations[0].id}/members`))
    .map((m) => `${m.userMail}:${m.memberType}`).sort();
  const projects = (await call('GET', '/projects')).filter((p) => p.name.startsWith('Demo - '));
  const perProject = {};
  for (const project of projects) {
    const members = (await call('GET', `/projects/${project.id}/members`)).map((m) => `${m.userMail}:${m.memberType}`).sort();
    const versions = {};
    for (const v of await call('GET', `/projects/${project.id}/versions`)) {
      const d = await call('GET', `/version/${v.id}/diagram`);
      const roles = (await call('GET', `/version/${v.id}/members`))
        .flatMap((m) => m.roles.map((r) => `${m.userMail}:${r.memberType}:${r.vertical ?? '-'}`)).sort();
      const scenarios = [];
      for (const s of await call('GET', `/projects/${project.id}/versions/${v.id}/scenarios`)) {
        const evaluations = await call('GET', `/projects/${project.id}/scenarios/${s.id}/evaluations`);
        scenarios.push(`${s.name}:configured=${s.configured}:draft=${s.draftPending}:evaluations=${evaluations.length}`);
      }
      versions[v.name] = { nodes: d.nodes.map((n) => n.name).sort(), connections: d.connections.length, roles, scenarios: scenarios.sort() };
    }
    const comparisons = (await call('GET', `/projects/${project.id}/comparisons`)).map((c) => c.name).sort();
    perProject[project.name] = { members, versions, comparisons };
  }
  return { users, organizations: organizations.length, orgMembers, projects: projects.map((p) => p.name).sort(), perProject };
}

test('seedRunsOnAnEmptyDatabaseAndASecondRunDuplicatesNothing', { skip, timeout: 600_000 }, async () => {
  const api = assertLocalTarget(target, process.env);
  const credentials = readCredentials(process.env, { needDemo: true });

  const first = await seed({ api, simulate: true, credentials });
  assert.ok(first.created > 0, 'the first run creates the demo data');
  const afterFirst = await snapshot(client(api), credentials);

  const second = await seed({ api, simulate: true, credentials });
  assert.equal(second.created, 0, 'the second run creates nothing');
  const afterSecond = await snapshot(client(api), credentials);
  assert.deepEqual(afterSecond, afterFirst);

  // What the seed promises (docs/demo-data.md).
  assert.equal(afterFirst.users.length, 9);
  assert.equal(afterFirst.organizations, 1);
  assert.ok(afterFirst.orgMembers.includes('demo-owner@enerscope.test:OWNER'));
  assert.ok(afterFirst.users.includes('demo-admin-plataforma@enerscope.test:ADMIN:true'));
  assert.deepEqual(afterFirst.projects, [AUSTRAL, NEUQUINA].sort());
  const neuquina = afterFirst.perProject[NEUQUINA];
  assert.equal(neuquina.versions[ROOT_VERSION].nodes.length, 13);
  assert.equal(neuquina.versions[ROOT_VERSION].connections, 12);
  assert.deepEqual(neuquina.versions[ROOT_VERSION].scenarios, [
    `${DRAFT_SCENARIO}:configured=false:draft=true:evaluations=0`,
    `${HIGH_COSTS_SCENARIO}:configured=true:draft=false:evaluations=1`,
    `${DEFAULT_SCENARIO}:configured=true:draft=false:evaluations=1`,
  ].sort());
  assert.ok(neuquina.versions[BRANCH_VERSION].roles.includes('demo-admin-version@enerscope.test:ADMIN:-'));
  assert.ok(!neuquina.versions[ROOT_VERSION].roles.some((r) => r.startsWith('demo-admin-version@')));
  assert.deepEqual(neuquina.comparisons, [COMPARISON]);
  assert.deepEqual(afterFirst.perProject[AUSTRAL].members, ['demo-admin-proyecto@enerscope.test:ADMIN']);
});

test('seededRolesSeeOnlyWhatTheyShould', { skip, timeout: 120_000 }, async () => {
  const api = assertLocalTarget(target, process.env);
  const credentials = readCredentials(process.env, { needDemo: true });
  const as = async (key) => {
    const c = client(api);
    await c.login(USERS.find((u) => u.key === key).mail, credentials.demoPassword);
    return c;
  };
  const projectNames = async (c) => (await c.call('GET', '/projects')).map((p) => p.name).sort();

  assert.deepEqual(await projectNames(await as('noAccess')), [], 'organization membership alone opens no project');
  assert.deepEqual(await projectNames(await as('executive')), [NEUQUINA], 'no leak into the other project');
  assert.deepEqual(await projectNames(await as('editorExtraction')), [NEUQUINA]);

  // The executive cannot read the other project's comparisons: one generic 404 or 403, never data.
  const admin = await as('projectAdmin');
  const austral = (await admin.call('GET', '/projects')).find((p) => p.name === AUSTRAL);
  const executive = await as('executive');
  await assert.rejects(executive.call('GET', `/projects/${austral.id}/comparisons`), (e) => [403, 404].includes(e.status));

  // The version admin is not a project admin: listing version roles stays with the project admin.
  const neuquina = (await admin.call('GET', '/projects')).find((p) => p.name === NEUQUINA);
  const branch = (await admin.call('GET', `/projects/${neuquina.id}/versions`)).find((v) => v.name === BRANCH_VERSION);
  const versionAdmin = await as('versionAdmin');
  await assert.rejects(versionAdmin.call('GET', `/version/${branch.id}/members`), (e) => e.status === 403);
  const access = await versionAdmin.call('GET', `/projects/${neuquina.id}/access`);
  assert.ok(JSON.stringify(access).includes(branch.id));
});
