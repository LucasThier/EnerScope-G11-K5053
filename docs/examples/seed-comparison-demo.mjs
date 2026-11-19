#!/usr/bin/env node
/*
 * Loads demo data to try scenario simulation and comparison, through the public API
 * (so every rule the app enforces applies). Run it against a local backend:
 *
 *   node docs/examples/seed-comparison-demo.mjs                 # data only, you simulate from the UI
 *   node docs/examples/seed-comparison-demo.mjs --simulate      # also runs a 10-round evaluation per scenario
 *
 * Options / environment:
 *   --api <url>        ENERSCOPE_API             default http://localhost:8080/api/v1
 *   --mail <mail>      ENERSCOPE_ADMIN_MAIL      default admin@enerscope.org (the dev seed admin)
 *   --password <pwd>   ENERSCOPE_ADMIN_PASSWORD  default admin12345 (the dev seed password)
 *   --simulate [--rounds N]                       default 10 rounds
 *
 * What it creates (organization "EnerScope Demo"):
 *   Project "Demo · Cuenca Neuquina"
 *     - "Escenario base"         2 wells (Pozo Norte, Pozo Sur), equipment USD 1.000
 *     - "Ampliación de pozos"    child of the base: Pozo Sur upgraded, new Pozo Este, equipment USD 1.800
 *     - "Escenario conservador"  child of the base: both wells with lower capacity
 *   Project "Demo · Cuenca Austral"
 *     - "Austral base"           2 wells of its own
 *
 * All four can be compared with each other, across both projects. The Neuquina child
 * versions share node identities with the base, so per-node flows line up.
 *
 * Refuses to run twice: it stops if the demo projects already exist. Never run it
 * against production.
 */
import { readFileSync } from 'node:fs';

const args = process.argv.slice(2);
const option = (name, fallback) => {
  const index = args.indexOf(name);
  return index >= 0 && args[index + 1] ? args[index + 1] : fallback;
};
const API = option('--api', process.env.ENERSCOPE_API ?? 'http://localhost:8080/api/v1').replace(/\/$/, '');
const MAIL = option('--mail', process.env.ENERSCOPE_ADMIN_MAIL ?? 'admin@enerscope.org');
const PASSWORD = option('--password', process.env.ENERSCOPE_ADMIN_PASSWORD ?? 'admin12345');
const SIMULATE = args.includes('--simulate');
const ROUNDS = Number(option('--rounds', '10'));

const ORGANIZATION = 'EnerScope Demo';
const NEUQUINA = 'Demo · Cuenca Neuquina';
const AUSTRAL = 'Demo · Cuenca Austral';

const fixtures = new URL('../../backend/src/test/resources/economic-examples/', import.meta.url);
const BASE_CONFIGURATION = JSON.parse(readFileSync(new URL('economic-configuration.json', fixtures), 'utf8'));
const WELL_TEMPLATE = JSON.parse(readFileSync(new URL('economic-well.json', fixtures), 'utf8'));
// Node ids the fixture configuration uses; replaced by the real ids of each scenario.
const FIXTURE_FIRST = '11111111-1111-1111-1111-111111111111';
const FIXTURE_SECOND = '22222222-2222-2222-2222-222222222222';

let token = null;

async function call(method, path, body) {
  const response = await fetch(API + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  const payload = text ? JSON.parse(text) : null;
  if (!response.ok || payload?.success === false) {
    throw new Error(`${method} ${path} → ${response.status}: ${payload?.message ?? text}`);
  }
  return payload?.data;
}

function well(name, capacity, startupDate, x, y) {
  return { ...structuredClone(WELL_TEMPLATE), name, maxCollectionCapacity: capacity, startupDate,
    graphData: { xposition: x, yposition: y, coordinates: 0 } };
}

async function nodeIdsByName(versionId) {
  const diagram = await call('GET', `/version/${versionId}/diagram`);
  return Object.fromEntries(diagram.nodes.map(node => [node.name, node.id]));
}

/** The fixture configuration pointed at the scenario's two first wells, with the given equipment cost. */
function configuration(firstId, secondId, equipmentCost) {
  const json = JSON.stringify(BASE_CONFIGURATION).replaceAll(FIXTURE_FIRST, firstId).replaceAll(FIXTURE_SECOND, secondId);
  const config = JSON.parse(json);
  config.assets[0].cost = equipmentCost;
  return config;
}

/** Adds a third well to a configuration: same cost/revenue rules as the second one, under new ids. */
function withExtraWell(config, nodeId, suffix) {
  const second = config.nodeProfiles[1];
  const profile = structuredClone(second);
  profile.nodeId = nodeId;
  profile.rules.forEach(rule => { rule.id = `${rule.id}-${suffix}`; });
  config.nodeProfiles.push(profile);
  const conversion = structuredClone(config.conversions.find(c => c.nodeId === second.nodeId));
  conversion.nodeId = nodeId;
  config.conversions.push(conversion);
  const sale = structuredClone(config.contracts.find(c => c.deliveryNodeId === second.nodeId));
  sale.id = `${sale.id}-${suffix}`;
  sale.deliveryNodeId = nodeId;
  sale.revenueRule.id = sale.id;
  config.contracts.push(sale);
  return config;
}

async function editCapacity(versionId, nodeId, name, capacity, startupDate, x, y) {
  const edited = await call('PATCH', `/version/${versionId}/node/${nodeId}`, well(name, capacity, startupDate, x, y));
  return edited.id; // editing an inherited node clones it: same identity, new id
}

async function saveConfiguration(projectId, versionId, config) {
  await call('PUT', `/projects/${projectId}/versions/${versionId}/economics/configuration`, config);
}

async function simulate(projectId, version, seed) {
  const base = `/projects/${projectId}/versions/${version.id}/economics`;
  let job = await call('POST', base + '/evaluation-jobs', { rounds: ROUNDS, seed: String(seed) });
  while (job.status === 'QUEUED' || job.status === 'RUNNING') {
    await new Promise(resolve => setTimeout(resolve, 1500));
    job = await call('GET', `${base}/evaluation-jobs/${job.id}`);
  }
  if (job.status !== 'COMPLETED') throw new Error(`Simulation of "${version.name}" ended ${job.status}: ${job.errorMessage}`);
  console.log(`  ✓ "${version.name}" simulated (${ROUNDS} rounds, seed ${seed})`);
}

async function main() {
  console.log(`API: ${API}`);
  const session = await call('POST', '/auth/login', { mail: MAIL, password: PASSWORD });
  token = session.accessToken;

  const projects = await call('GET', '/projects');
  const existing = projects.filter(p => p.name === NEUQUINA || p.name === AUSTRAL);
  if (existing.length) {
    throw new Error(`The demo data is already loaded (${existing.map(p => p.name).join(', ')}). Delete those projects to load it again.`);
  }

  const organizations = await call('GET', '/organizations');
  const organization = organizations.find(o => o.name === ORGANIZATION)
    ?? await call('POST', '/organizations', { name: ORGANIZATION });

  // ---------------------------------------------------------------- Project 1: three comparable scenarios
  const neuquina = await call('POST', '/projects', { name: NEUQUINA,
    description: 'Datos de demostración para simular y comparar escenarios.', organizationId: organization.id });
  console.log(`Project "${neuquina.name}"`);

  const base = await call('POST', `/projects/${neuquina.id}/version`, { name: 'Escenario base' });
  await call('POST', `/version/${base.id}/node`, well('Pozo Norte', 100, '2031-01-01T00:00:00Z', 0, 100));
  await call('POST', `/version/${base.id}/node`, well('Pozo Sur', 500, '2032-01-01T00:00:00Z', 250, 100));
  let ids = await nodeIdsByName(base.id);
  await saveConfiguration(neuquina.id, base.id, configuration(ids['Pozo Norte'], ids['Pozo Sur'], 1000));
  console.log('  + "Escenario base": Pozo Norte (100), Pozo Sur (500), equipment USD 1.000');

  const expansion = await call('POST', `/projects/${neuquina.id}/version`, { name: 'Ampliación de pozos', parentVersion: base.id });
  ids = await nodeIdsByName(expansion.id);
  const upgradedSouth = await editCapacity(expansion.id, ids['Pozo Sur'], 'Pozo Sur', 800, '2032-01-01T00:00:00Z', 250, 100);
  const east = await call('POST', `/version/${expansion.id}/node`, well('Pozo Este', 300, '2031-01-01T00:00:00Z', 500, 100));
  await saveConfiguration(neuquina.id, expansion.id,
    withExtraWell(configuration(ids['Pozo Norte'], upgradedSouth, 1800), east.id, 'este'));
  console.log('  + "Ampliación de pozos": Pozo Sur → 800, new Pozo Este (300), equipment USD 1.800');

  const conservative = await call('POST', `/projects/${neuquina.id}/version`, { name: 'Escenario conservador', parentVersion: base.id });
  ids = await nodeIdsByName(conservative.id);
  const lowNorth = await editCapacity(conservative.id, ids['Pozo Norte'], 'Pozo Norte', 60, '2031-01-01T00:00:00Z', 0, 100);
  const lowSouth = await editCapacity(conservative.id, ids['Pozo Sur'], 'Pozo Sur', 250, '2032-01-01T00:00:00Z', 250, 100);
  await saveConfiguration(neuquina.id, conservative.id, configuration(lowNorth, lowSouth, 1000));
  console.log('  + "Escenario conservador": Pozo Norte → 60, Pozo Sur → 250, equipment USD 1.000');

  // ---------------------------------------------------------------- Project 2: one scenario of another project
  const austral = await call('POST', '/projects', { name: AUSTRAL,
    description: 'Proyecto aparte para comparar escenarios de distintos proyectos.', organizationId: organization.id });
  console.log(`Project "${austral.name}"`);
  const australBase = await call('POST', `/projects/${austral.id}/version`, { name: 'Austral base' });
  await call('POST', `/version/${australBase.id}/node`, well('Pozo Austral 1', 200, '2031-01-01T00:00:00Z', 0, 100));
  await call('POST', `/version/${australBase.id}/node`, well('Pozo Austral 2', 400, '2031-01-01T00:00:00Z', 250, 100));
  ids = await nodeIdsByName(australBase.id);
  await saveConfiguration(austral.id, australBase.id, configuration(ids['Pozo Austral 1'], ids['Pozo Austral 2'], 1200));
  console.log('  + "Austral base": Pozo Austral 1 (200), Pozo Austral 2 (400), equipment USD 1.200');

  // ---------------------------------------------------------------- Ready to simulate?
  for (const project of [neuquina, austral]) {
    const readiness = await call('GET', `/projects/${project.id}/versions/simulation-readiness`);
    const blocked = readiness.filter(r => !r.ready);
    if (blocked.length) throw new Error(`Not ready to simulate in "${project.name}": ${JSON.stringify(blocked)}`);
  }
  console.log('All scenarios are ready to simulate.');

  if (SIMULATE) {
    console.log(`Simulating (${ROUNDS} rounds each)…`);
    let seed = 1001;
    for (const version of [base, expansion, conservative]) await simulate(neuquina.id, version, seed++);
    await simulate(austral.id, australBase, seed);
  }

  console.log(`\nDone. Sign in as ${MAIL}, pick "${NEUQUINA}" and open Análisis → Comparaciones${SIMULATE ? '' : ' (scenarios can be simulated from there)'}.`);
}

main().catch(error => {
  console.error('✗ ' + error.message);
  process.exitCode = 1;
});
