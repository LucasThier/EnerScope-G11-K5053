#!/usr/bin/env node
/*
 * Demo data for a LOCAL EnerScope, loaded through the public API so every business rule applies
 * (permissions, vertical and role derived from the node type, default scenario per version, scenario
 * limit, unique names). Runs only on demand; nothing loads it on startup.
 *
 *   ENERSCOPE_SEED_LOCAL=yes ENERSCOPE_ADMIN_PASSWORD= ENERSCOPE_DEMO_PASSWORD= \
 *     node docs/examples/seed-local-demo.mjs [--simulate] [--api http://localhost:8080/api/v1]
 *
 *   ... node docs/examples/seed-local-demo.mjs --cleanup --dry-run   # lists what --cleanup would touch
 *   ... node docs/examples/seed-local-demo.mjs --cleanup --yes       # removes / deactivates the demo data
 *
 * Environment (no passwords have defaults, none is ever printed):
 *   ENERSCOPE_SEED_LOCAL=yes      required: explicit "this is my local machine"
 *   ENERSCOPE_ADMIN_PASSWORD      required: password of the platform admin used to bootstrap
 *   ENERSCOPE_DEMO_PASSWORD       required for the seed (min. 8 chars): shared by every demo user
 *   ENERSCOPE_ADMIN_MAIL          optional, default admin@enerscope.org
 *   ENERSCOPE_API                 optional, default http://localhost:8080/api/v1 (only localhost is accepted)
 *
 * Idempotent: everything is looked up by name or mail and only created when missing. Demo data is marked by the
 * "Demo - " prefix and the @enerscope.test mail domain; nothing else is ever modified or removed.
 * See docs/demo-data.md.
 */
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

export const PREFIX = 'Demo - ';
export const MAIL_DOMAIN = '@enerscope.test';
export const DEFAULT_API = 'http://localhost:8080/api/v1';
export const LOCAL_HOSTS = new Set(['localhost', '127.0.0.1', '[::1]', '::1']);

export const ORGANIZATION = `${PREFIX}EnerScope`;
export const NEUQUINA = `${PREFIX}Cuenca Neuquina`;
export const AUSTRAL = `${PREFIX}Cuenca Austral`;
export const ROOT_VERSION = `${PREFIX}Base`;
export const BRANCH_VERSION = `${PREFIX}Ampliación`;
export const AUSTRAL_VERSION = `${PREFIX}Austral base`;
export const DEFAULT_SCENARIO = 'Escenario base';
export const HIGH_COSTS_SCENARIO = `${PREFIX}Costos altos`;
export const DRAFT_SCENARIO = `${PREFIX}Borrador`;
export const COMPARISON = `${PREFIX}Base vs Costos altos`;
export const ROUNDS = 10;

// ------------------------------------------------------------------------------------------ pure helpers (tested)

/** Parses the command line. Unknown flags are an error so a typo never runs the wrong mode. */
export function parseArgs(argv) {
  const options = { simulate: false, cleanup: false, dryRun: false, yes: false, api: null };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === '--simulate') options.simulate = true;
    else if (arg === '--cleanup') options.cleanup = true;
    else if (arg === '--dry-run') options.dryRun = true;
    else if (arg === '--yes') options.yes = true;
    else if (arg === '--api') {
      if (!argv[i + 1]) throw new Error('--api necesita una URL.');
      options.api = argv[++i];
    } else throw new Error(`Opción desconocida: ${arg}`);
  }
  if ((options.dryRun || options.yes) && !options.cleanup) throw new Error('--dry-run y --yes solo valen con --cleanup.');
  if (options.cleanup && options.simulate) throw new Error('--simulate no se combina con --cleanup.');
  if (options.cleanup && options.dryRun && options.yes) throw new Error('Elegí --dry-run o --yes, no los dos.');
  if (options.cleanup && !options.dryRun && !options.yes) {
    throw new Error('--cleanup borra datos Demo: agregá --dry-run para ver la lista o --yes para confirmar.');
  }
  return options;
}

/**
 * The double guard: the API must be plain http on a loopback host AND ENERSCOPE_SEED_LOCAL must be "yes".
 * There is no flag to skip it. Returns the normalized base URL.
 */
export function assertLocalTarget(apiUrl, env) {
  if (env.ENERSCOPE_SEED_LOCAL !== 'yes') {
    throw new Error('Guarda: este script solo corre en un entorno local. Definí ENERSCOPE_SEED_LOCAL=yes para confirmarlo.');
  }
  let url;
  try {
    url = new URL(apiUrl);
  } catch {
    throw new Error(`Guarda: URL de API inválida: ${apiUrl}`);
  }
  if (url.protocol !== 'http:') throw new Error(`Guarda: solo se acepta http:// local, no ${url.protocol}//.`);
  if (!LOCAL_HOSTS.has(url.hostname)) {
    throw new Error(`Guarda: el host "${url.hostname}" no es local (se acepta localhost, 127.0.0.1 o ::1).`);
  }
  if (url.username || url.password) throw new Error('Guarda: la URL de la API no puede llevar credenciales.');
  return url.href.replace(/\/$/, '');
}

/** Reads the passwords from the environment. Never returns or prints anything else. */
export function readCredentials(env, { needDemo }) {
  const missing = [];
  if (!env.ENERSCOPE_ADMIN_PASSWORD) missing.push('ENERSCOPE_ADMIN_PASSWORD');
  if (needDemo && !env.ENERSCOPE_DEMO_PASSWORD) missing.push('ENERSCOPE_DEMO_PASSWORD');
  if (missing.length) throw new Error(`Faltan variables de entorno obligatorias: ${missing.join(', ')}.`);
  if (needDemo && env.ENERSCOPE_DEMO_PASSWORD.length < 8) {
    throw new Error('ENERSCOPE_DEMO_PASSWORD debe tener al menos 8 caracteres.');
  }
  return {
    adminMail: env.ENERSCOPE_ADMIN_MAIL || 'admin@enerscope.org',
    adminPassword: env.ENERSCOPE_ADMIN_PASSWORD,
    demoPassword: needDemo ? env.ENERSCOPE_DEMO_PASSWORD : null,
  };
}

export const isDemoName = (name) => typeof name === 'string' && name.startsWith(PREFIX);
export const isDemoMail = (mail) => typeof mail === 'string' && mail.toLowerCase().endsWith(MAIL_DOMAIN);

/**
 * The demo users. `platform`: platform role. `org`: organization role. `projects`: project role per project.
 * `versions`: version roles per version name (EDITOR carries a vertical).
 */
export const USERS = [
  { key: 'owner', mail: `demo-owner${MAIL_DOMAIN}`, firstName: 'Demo', lastName: 'Owner', jobTitle: 'Dueña de la organización',
    platform: 'USER', org: 'OWNER', projects: {}, versions: [] },
  { key: 'platformAdmin', mail: `demo-admin-plataforma${MAIL_DOMAIN}`, firstName: 'Demo', lastName: 'Admin Plataforma',
    jobTitle: 'Administración de plataforma', platform: 'ADMIN', org: 'MEMBER', projects: {}, versions: [] },
  { key: 'projectAdmin', mail: `demo-admin-proyecto${MAIL_DOMAIN}`, firstName: 'Demo', lastName: 'Admin Proyecto',
    jobTitle: 'Líder de proyecto', platform: 'USER', org: 'MEMBER', projects: { [NEUQUINA]: 'ADMIN', [AUSTRAL]: 'ADMIN' }, versions: [] },
  { key: 'editorExtraction', mail: `demo-editor-extraccion${MAIL_DOMAIN}`, firstName: 'Demo', lastName: 'Editor Extracción',
    jobTitle: 'Ingeniería de reservorios', platform: 'USER', org: 'MEMBER', projects: { [NEUQUINA]: 'EDITOR' },
    versions: [{ version: ROOT_VERSION, memberType: 'EDITOR', vertical: 'EXTRACTION' }] },
  { key: 'editorTransport', mail: `demo-editor-transporte${MAIL_DOMAIN}`, firstName: 'Demo', lastName: 'Editor Transporte',
    jobTitle: 'Ingeniería de ductos', platform: 'USER', org: 'MEMBER', projects: { [NEUQUINA]: 'EDITOR' },
    versions: [{ version: ROOT_VERSION, memberType: 'EDITOR', vertical: 'TRANSPORTATION' }] },
  { key: 'simulator', mail: `demo-simulador${MAIL_DOMAIN}`, firstName: 'Demo', lastName: 'Simulador',
    jobTitle: 'Análisis económico', platform: 'USER', org: 'MEMBER', projects: { [NEUQUINA]: 'EDITOR' },
    versions: [{ version: ROOT_VERSION, memberType: 'SIMULATOR', vertical: null }] },
  { key: 'executive', mail: `demo-ejecutivo${MAIL_DOMAIN}`, firstName: 'Demo', lastName: 'Ejecutivo',
    jobTitle: 'Dirección', platform: 'USER', org: 'MEMBER', projects: { [NEUQUINA]: 'EXECUTIVE' }, versions: [] },
  { key: 'versionAdmin', mail: `demo-admin-version${MAIL_DOMAIN}`, firstName: 'Demo', lastName: 'Admin Versión',
    jobTitle: 'Responsable de la ampliación', platform: 'USER', org: 'MEMBER', projects: { [NEUQUINA]: 'EDITOR' },
    versions: [{ version: BRANCH_VERSION, memberType: 'ADMIN', vertical: null }] },
  { key: 'noAccess', mail: `demo-sin-acceso${MAIL_DOMAIN}`, firstName: 'Demo', lastName: 'Sin Acceso',
    jobTitle: 'Sin proyectos', platform: 'USER', org: 'MEMBER', projects: {}, versions: [] },
];

const COMMON = {
  state: 'RUNNING', startupDate: '2031-01-01T00:00:00Z', lifespanInMonths: 240, upkeepCosts: 1000,
  maintenanceIntervalInDays: 0, maintenanceDuration: 0, operatingCosts: 5000, wastePercentage: 0.02,
  investmentCost: { components: [] },
};
const TYPES = {
  WELL: ['EXTRACTION', 'GENERATOR'], GATHERING_NETWORK: ['EXTRACTION', 'INTERMEDIATE'],
  TREATMENT_PLANT: ['EXTRACTION', 'INTERMEDIATE'], PIPELINE: ['TRANSPORTATION', 'INTERMEDIATE'],
  PIPELINE_CONECTION: ['TRANSPORTATION', 'INTERMEDIATE'], COMPRESSING_PLANT: ['TRANSPORTATION', 'INTERMEDIATE'],
  GROUND_LIQUEFACTION_PLANT: ['LIQUEFACTION', 'INTERMEDIATE'], FLNG_UNIT: ['LIQUEFACTION', 'INTERMEDIATE'],
  SEAPORT_TERMINAL: ['EXPORT', 'SINK'], LNG_CAMER: ['EXPORT', 'SINK'],
  INTERNAL_CONSUMPTION: ['INTERNAL_CONSUMPTION', 'SINK'], INDUSTRIAL_CONSUMPTION: ['INTERNAL_CONSUMPTION', 'SINK'],
};

/** A node request body; vertical and role come from the same table the backend checks (NodeTypeEnum). */
export function node(nodeType, name, x, y, specific) {
  const [vertical, role] = TYPES[nodeType];
  return { ...COMMON, ...specific, name, type: { vertical, role, nodeType },
    graphData: { graphPosition: { x, y } } };
}

const well = (name, capacity, x, y) => node('WELL', name, x, y, {
  maxCollectionCapacity: capacity, declineCurve: { type: 'CONSTANT', val_a: 0, val_b: 0 }, gasRichness: 0.85,
  dtmTime: 0, dtmCost: 0, surface: 1200,
});

/** The 13 nodes of the root version: every one of the 12 types, two wells. */
export const NEUQUINA_NODES = [
  well('Pozo Norte', 100, 0, 0),
  well('Pozo Sur', 500, 0, 200),
  node('GATHERING_NETWORK', 'Red de recolección', 250, 100, { maxTransportCapacity: 2000, length: 15000, lossPerMeter: 0, connectedWells: 2 }),
  node('TREATMENT_PLANT', 'Planta de tratamiento', 500, 100, { maxTreatmentCapacity: 2000, contaminantWaste: 0.01, intermediateStorage: 100, treatmentCost: 1 }),
  node('COMPRESSING_PLANT', 'Planta compresora', 750, 100, { maxCompressionCapacity: 2000, processWaste: 0.01, gasConsumption: 0 }),
  node('PIPELINE', 'Gasoducto troncal', 1000, 100, { maxFlowCapacity: 2000, length: 120, lossPerKm: 0.001 }),
  node('PIPELINE_CONECTION', 'Empalme', 1250, 100, { transferCapacity: 2000, outputPriority: 1 }),
  node('GROUND_LIQUEFACTION_PLANT', 'Licuefacción en tierra', 1500, -100, { maxProcessingCapacity: 500, mtpaRatio: 1, intermediateStorage: 100, gasConsumption: 0 }),
  node('SEAPORT_TERMINAL', 'Terminal portuaria', 1750, -100, { intermediateStorage: 1000, portDepth: 15, shipCapacity: 100 }),
  node('FLNG_UNIT', 'Unidad FLNG', 1500, 50, { maxProcessingCapacity: 500, mtpaRatio: 1, intermediateStorage: 100, vesselDepth: 20, hiringCost: 0, shipCapacity: 100, gasConsumption: 0 }),
  node('LNG_CAMER', 'Buque metanero', 1750, 50, { exportFrequency: 30, shipCapacity: 100, fullLoadTime: 1, hiringCost: 0, timeToDestination: 10 }),
  node('INTERNAL_CONSUMPTION', 'Consumo interno', 1500, 200, { consumptionSummer: 50, consumptionAutumn: 60, consumptionWinter: 80, consumptionSpring: 60 }),
  node('INDUSTRIAL_CONSUMPTION', 'Consumo industrial', 1500, 350, { consumption: 100 }),
];

export const NEUQUINA_CONNECTIONS = [
  ['Pozo Norte', 'Red de recolección'], ['Pozo Sur', 'Red de recolección'],
  ['Red de recolección', 'Planta de tratamiento'], ['Planta de tratamiento', 'Planta compresora'],
  ['Planta compresora', 'Gasoducto troncal'], ['Gasoducto troncal', 'Empalme'],
  ['Empalme', 'Licuefacción en tierra'], ['Licuefacción en tierra', 'Terminal portuaria'],
  ['Empalme', 'Unidad FLNG'], ['Unidad FLNG', 'Buque metanero'],
  ['Empalme', 'Consumo interno'], ['Empalme', 'Consumo industrial'],
];

export const AUSTRAL_NODES = [
  well('Pozo Austral 1', 200, 0, 0),
  well('Pozo Austral 2', 400, 0, 200),
  node('PIPELINE', 'Gasoducto Austral', 250, 100, { maxFlowCapacity: 1000, length: 80, lossPerKm: 0.001 }),
];
export const AUSTRAL_CONNECTIONS = [['Pozo Austral 1', 'Gasoducto Austral'], ['Pozo Austral 2', 'Gasoducto Austral']];

/** The branch raises "Pozo Sur" from 500 to 800: editing an inherited node clones it (same identity, new id). */
export const BRANCH_EDIT = { name: 'Pozo Sur', body: well('Pozo Sur', 800, 0, 200) };

// The backend's own economic example (also used by its tests): two wells, two tax entities, sales, assets.
export const FIXTURE_URL = new URL('../../backend/src/test/resources/economic-examples/economic-configuration.json', import.meta.url);
export const FIXTURE_FIRST = '11111111-1111-1111-1111-111111111111';
export const FIXTURE_SECOND = '22222222-2222-2222-2222-222222222222';

/**
 * The fixture configuration pointed at the version's two wells, with the given equipment cost and every fixed
 * expense multiplied by `fixedFactor`. Only node ids of the version may appear in a configuration.
 */
export function economicConfiguration(fixture, [firstId, secondId], { equipmentCost, fixedFactor = 1 }) {
  const config = JSON.parse(JSON.stringify(fixture).replaceAll(FIXTURE_FIRST, firstId).replaceAll(FIXTURE_SECOND, secondId));
  config.assets[0].cost = equipmentCost;
  for (const profile of config.nodeProfiles) {
    for (const rule of profile.rules) if (rule.driver === 'FIXED') rule.unitValue *= fixedFactor;
  }
  return config;
}

/** Editor payload that the scenario screen stores: the advanced mode needs every collection as an array. */
export function advancedEditor(configuration) {
  const value = {};
  for (const field of ['taxEntities', 'boundary', 'nodeProfiles', 'adjustments', 'contracts', 'assets', 'taxTreatments', 'conversions']) {
    value[field] = configuration?.[field] ?? [];
  }
  return { mode: 'advanced', value };
}

/** What --cleanup would touch, chosen only by the demo markers. Pure so the selection itself is tested. */
export function selectCleanup({ organizations, projects, users, adminMail }) {
  const demoOrgs = organizations.filter((o) => isDemoName(o.name));
  const orgIds = new Set(demoOrgs.map((o) => o.id));
  return {
    organizations: demoOrgs,
    // Both markers: the prefix AND a demo organization, so a real project named "Demo - …" elsewhere is never touched.
    projects: projects.filter((p) => isDemoName(p.name) && orgIds.has(p.organizationId)),
    users: users.filter((u) => isDemoMail(u.mail) && u.mail.toLowerCase() !== String(adminMail).toLowerCase()),
  };
}

// ------------------------------------------------------------------------------------------ HTTP

export function client(api, log = () => {}) {
  let token = null;
  async function call(method, path, body, headers = {}) {
    const response = await fetch(api + path, {
      method,
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}), ...headers },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const text = await response.text();
    let payload = null;
    try { payload = text ? JSON.parse(text) : null; } catch { payload = null; }
    if (!response.ok || payload?.success === false) {
      const error = new Error(`${method} ${path} → ${response.status}: ${payload?.message ?? text.slice(0, 300)}`);
      error.status = response.status;
      throw error;
    }
    return payload?.data;
  }
  return {
    call,
    async login(mail, password) {
      token = null;
      try {
        const session = await call('POST', '/auth/login', { mail, password });
        token = session.accessToken;
      } catch (error) {
        // Never echo the password; say which account failed.
        throw new Error(`No se pudo iniciar sesión como ${mail} (${error.status ?? 'sin respuesta'}). Revisá la contraseña de ese usuario.`);
      }
      log(`  sesión: ${mail}`);
    },
  };
}

// ------------------------------------------------------------------------------------------ seed

function makeReport() {
  const counts = { created: 0, existing: 0 };
  return {
    counts,
    created(msg) { counts.created++; console.log(`  + ${msg}`); },
    existing(msg) { counts.existing++; console.log(`  = ${msg}`); },
    info(msg) { console.log(msg); },
  };
}

async function ensureOrganization(api, report) {
  const organizations = await api.call('GET', '/organizations');
  const found = organizations.find((o) => o.name === ORGANIZATION);
  if (!found) {
    const created = await api.call('POST', '/organizations', { name: ORGANIZATION });
    report.created(`organización "${ORGANIZATION}"`);
    return created.id;
  }
  if (!found.active) {
    await api.call('POST', `/organizations/${found.id}/reactivate`);
    report.created(`organización "${ORGANIZATION}" reactivada`);
  } else report.existing(`organización "${ORGANIZATION}"`);
  return found.id;
}

async function ensureUsers(api, organizationId, demoPassword, report) {
  const ids = {};
  let all = await api.call('GET', '/users');
  let members = await api.call('GET', `/organizations/${organizationId}/members`);
  for (const user of USERS) {
    let existing = all.find((u) => u.mail.toLowerCase() === user.mail);
    if (!existing) {
      await api.call('POST', `/organizations/${organizationId}/users`, {
        mail: user.mail, firstName: user.firstName, lastName: user.lastName, password: demoPassword, jobTitle: user.jobTitle,
      });
      report.created(`usuario ${user.mail}`);
      all = await api.call('GET', '/users');
      members = await api.call('GET', `/organizations/${organizationId}/members`);
      existing = all.find((u) => u.mail.toLowerCase() === user.mail);
    } else {
      if (!existing.active) {
        await api.call('POST', `/users/${existing.id}/reactivate`);
        report.created(`usuario ${user.mail} reactivado`);
      } else report.existing(`usuario ${user.mail}`);
      if (!members.some((m) => m.userId === existing.id)) {
        await api.call('POST', `/organizations/${organizationId}/members`, { userId: existing.id, memberType: user.org });
        report.created(`${user.mail} agregado a la organización`);
        members = await api.call('GET', `/organizations/${organizationId}/members`);
      }
    }
    ids[user.key] = existing.id;
    const member = members.find((m) => m.userId === existing.id);
    if (member && member.memberType !== user.org) {
      await api.call('PATCH', `/organizations/${organizationId}/members/${member.id}`, { memberType: user.org });
      report.created(`${user.mail} → ${user.org} de la organización`);
    }
    if (existing.platformRole !== user.platform) {
      await api.call('PATCH', `/users/${existing.id}/role`, { platformRole: user.platform });
      report.created(`${user.mail} → ${user.platform} de la plataforma`);
    }
  }
  return ids;
}

async function ensureProject(api, organizationId, name, description, report) {
  const projects = await api.call('GET', `/projects?organizationId=${organizationId}`);
  const found = projects.find((p) => p.name === name);
  if (found) { report.existing(`proyecto "${name}"`); return found.id; }
  const created = await api.call('POST', '/projects', { name, description, organizationId });
  report.created(`proyecto "${name}"`);
  return created.id;
}

async function ensureProjectMembers(api, projectId, projectName, userIds, report) {
  let members = await api.call('GET', `/projects/${projectId}/members`);
  for (const user of USERS) {
    const wanted = user.projects[projectName];
    if (!wanted) continue;
    const member = members.find((m) => m.userId === userIds[user.key]);
    if (!member) {
      await api.call('POST', `/projects/${projectId}/members`, { userId: userIds[user.key], memberType: wanted });
      report.created(`${user.mail} → ${wanted} de "${projectName}"`);
    } else if (member.memberType !== wanted) {
      await api.call('PATCH', `/projects/${projectId}/members/${member.id}`, { memberType: wanted });
      report.created(`${user.mail} → ${wanted} de "${projectName}"`);
    } else report.existing(`${user.mail} es ${wanted} de "${projectName}"`);
  }
  members = await api.call('GET', `/projects/${projectId}/members`);
  return members;
}

async function ensureVersion(api, projectId, name, parentVersion, report) {
  const versions = await api.call('GET', `/projects/${projectId}/versions`);
  const found = versions.find((v) => v.name === name && (v.parentVersionId ?? null) === (parentVersion ?? null));
  if (found) { report.existing(`versión "${name}"`); return { id: found.id, created: false }; }
  const body = parentVersion ? { name, parentVersion } : { name };
  const created = await api.call('POST', `/projects/${projectId}/version`, body);
  report.created(`versión "${name}"${parentVersion ? ' (rama)' : ' (raíz)'}`);
  return { id: created.id, created: true };
}

async function diagram(api, versionId) {
  const d = await api.call('GET', `/version/${versionId}/diagram`);
  return { nodes: d.nodes ?? [], connections: d.connections ?? [], byName: Object.fromEntries((d.nodes ?? []).map((n) => [n.name, n])) };
}

async function ensureNetwork(api, versionId, nodes, connections, report) {
  let d = await diagram(api, versionId);
  let createdNodes = 0;
  for (const body of nodes) {
    if (d.byName[body.name]) continue;
    await api.call('POST', `/version/${versionId}/node`, body);
    createdNodes++;
  }
  if (createdNodes) report.created(`${createdNodes} nodo(s)`); else report.existing(`${nodes.length} nodos`);
  d = await diagram(api, versionId);
  let createdConnections = 0;
  for (const [from, to] of connections) {
    const fromId = d.byName[from].id;
    const toId = d.byName[to].id;
    if (d.connections.some((c) => c.fromNodeId === fromId && c.toNodeId === toId)) continue;
    await api.call('POST', `/version/${versionId}/connection`, { fromNodeId: fromId, toNodeId: toId });
    createdConnections++;
  }
  if (createdConnections) report.created(`${createdConnections} conexión(es)`); else report.existing(`${connections.length} conexiones`);
  return diagram(api, versionId);
}

async function ensureVersionRoles(api, versionId, versionName, userIds, report, extra = []) {
  const wanted = [
    ...USERS.flatMap((u) => u.versions.filter((r) => r.version === versionName).map((r) => ({ user: u, ...r }))),
    ...extra,
  ];
  for (const role of wanted) {
    const members = await api.call('GET', `/version/${versionId}/members`);
    const member = members.find((m) => m.userId === userIds[role.user.key]);
    const has = member?.roles.some((r) => r.memberType === role.memberType && (r.vertical ?? null) === (role.vertical ?? null));
    const label = `${role.user.mail} → ${role.memberType}${role.vertical ? '/' + role.vertical : ''} en "${versionName}"`;
    if (has) { report.existing(label); continue; }
    await api.call('POST', `/version/${versionId}/members`, { userId: userIds[role.user.key], memberType: role.memberType, vertical: role.vertical });
    report.created(label);
  }
}

async function ensureScenario(api, projectId, versionId, name, report) {
  const scenarios = await api.call('GET', `/projects/${projectId}/versions/${versionId}/scenarios`);
  const found = scenarios.find((s) => s.name.toLowerCase() === name.toLowerCase());
  if (found) { report.existing(`escenario "${name}"`); return found; }
  const created = await api.call('POST', `/projects/${projectId}/versions/${versionId}/scenarios`, { name });
  report.created(`escenario "${name}"`);
  return created;
}

async function ensureConfiguration(api, projectId, scenario, configuration, report) {
  const base = `/projects/${projectId}/scenarios/${scenario.id}/configuration`;
  const current = await api.call('GET', base);
  if (current.configuration) { report.existing(`configuración económica de "${scenario.name}"`); return; }
  const saved = await api.call('PUT', base, { token: current.token, sourceToken: current.sourceToken,
    editor: advancedEditor(configuration), configuration, operations: {} });
  if (saved.errors?.length) throw new Error(`La configuración de "${scenario.name}" quedó como borrador: ${saved.errors.join(' | ')}`);
  report.created(`configuración económica de "${scenario.name}"`);
}

async function ensureDraft(api, projectId, scenario, configuration, report) {
  const base = `/projects/${projectId}/scenarios/${scenario.id}/configuration`;
  const current = await api.call('GET', base);
  if (current.draft || current.configuration) { report.existing(`borrador de "${scenario.name}"`); return; }
  // Incomplete on purpose (no configuration): the backend stores it as the scenario's draft.
  await api.call('PUT', base, { token: current.token, sourceToken: current.sourceToken,
    editor: advancedEditor({ ...configuration, assets: [] }), configuration: null, operations: {} });
  report.created(`borrador de "${scenario.name}"`);
}

async function simulate(api, projectId, scenario, seed, report) {
  const base = `/projects/${projectId}/scenarios/${scenario.id}`;
  const evaluations = await api.call('GET', `${base}/evaluations`);
  if (evaluations.length) { report.existing(`simulación de "${scenario.name}"`); return true; }
  let job = await api.call('POST', `${base}/simulations`, { rounds: ROUNDS, seed: String(seed) });
  while (job.status === 'QUEUED' || job.status === 'RUNNING') {
    await new Promise((r) => setTimeout(r, 1000));
    job = await api.call('GET', `${base}/simulations/${job.id}`);
  }
  if (job.status !== 'COMPLETED') {
    report.info(`  ! la simulación de "${scenario.name}" terminó ${job.status}: ${job.errorMessage ?? ''}`);
    return false;
  }
  report.created(`simulación de "${scenario.name}" (${ROUNDS} rondas, semilla ${seed})`);
  return true;
}

export async function seed({ api: apiUrl, simulate: runSimulations, credentials }) {
  const report = makeReport();
  const api = client(apiUrl);
  report.info(`API: ${apiUrl}`);

  await api.login(credentials.adminMail, credentials.adminPassword);
  report.info('Organización y usuarios');
  const organizationId = await ensureOrganization(api, report);
  const userIds = await ensureUsers(api, organizationId, credentials.demoPassword, report);

  // From here on the project admin works as themselves, so the platform admin never joins a demo project.
  const projectAdmin = USERS.find((u) => u.key === 'projectAdmin');
  await api.login(projectAdmin.mail, credentials.demoPassword);

  report.info(`Proyecto "${NEUQUINA}"`);
  const neuquina = await ensureProject(api, organizationId, NEUQUINA, 'Datos de demostración: red completa, roles y escenarios.', report);
  await ensureProjectMembers(api, neuquina, NEUQUINA, userIds, report);
  const root = await ensureVersion(api, neuquina, ROOT_VERSION, null, report);
  const rootNetwork = await ensureNetwork(api, root.id, NEUQUINA_NODES, NEUQUINA_CONNECTIONS, report);
  // Roles on the root before the branch exists, so creating the branch copies them (that is what the app does).
  await ensureVersionRoles(api, root.id, ROOT_VERSION, userIds, report);

  const branch = await ensureVersion(api, neuquina, BRANCH_VERSION, root.id, report);
  const copied = USERS.flatMap((u) => u.versions.filter((r) => r.version === ROOT_VERSION).map((r) => ({ user: u, ...r })));
  await ensureVersionRoles(api, branch.id, BRANCH_VERSION, userIds, report, copied);
  const branchNetwork = await diagram(api, branch.id);
  // The branch gets its own copy of every node (same identity, new id); the edit is detected by its value.
  const branchWell = branchNetwork.byName[BRANCH_EDIT.name];
  const detail = await api.call('GET', `/version/${branch.id}/node/${branchWell.id}`);
  // The node detail carries the type-specific values under `attributes`.
  if (Number(detail?.attributes?.maxCollectionCapacity) !== BRANCH_EDIT.body.maxCollectionCapacity) {
    await api.call('PATCH', `/version/${branch.id}/node/${branchWell.id}`, BRANCH_EDIT.body);
    report.created(`"${BRANCH_EDIT.name}" editado en la rama (capacidad ${BRANCH_EDIT.body.maxCollectionCapacity})`);
  } else report.existing(`"${BRANCH_EDIT.name}" editado en la rama`);

  const wellIds = [rootNetwork.byName['Pozo Norte'].id, rootNetwork.byName['Pozo Sur'].id];
  const baseScenario = await ensureScenario(api, neuquina, root.id, DEFAULT_SCENARIO, report);
  const highCosts = await ensureScenario(api, neuquina, root.id, HIGH_COSTS_SCENARIO, report);
  const draft = await ensureScenario(api, neuquina, root.id, DRAFT_SCENARIO, report);
  const fixture = JSON.parse(readFileSync(FIXTURE_URL, 'utf8'));
  const baseConfig = economicConfiguration(fixture, wellIds, { equipmentCost: 1000 });
  await ensureConfiguration(api, neuquina, baseScenario, baseConfig, report);
  await ensureConfiguration(api, neuquina, highCosts, economicConfiguration(fixture, wellIds, { equipmentCost: 1800, fixedFactor: 2 }), report);
  await ensureDraft(api, neuquina, draft, baseConfig, report);

  report.info(`Proyecto "${AUSTRAL}"`);
  const austral = await ensureProject(api, organizationId, AUSTRAL, 'Proyecto aparte para comprobar que no hay fuga entre proyectos.', report);
  await ensureProjectMembers(api, austral, AUSTRAL, userIds, report);
  const australRoot = await ensureVersion(api, austral, AUSTRAL_VERSION, null, report);
  await ensureNetwork(api, australRoot.id, AUSTRAL_NODES, AUSTRAL_CONNECTIONS, report);

  const simulated = [];
  if (runSimulations) {
    report.info('Simulaciones');
    const comparable = await api.call('GET', `/projects/${neuquina}/comparisons/scenarios`);
    let seedValue = 1001;
    for (const scenario of [baseScenario, highCosts]) {
      const row = comparable.find((c) => c.scenarioId === scenario.id);
      if (row && !row.ready && !row.latestEvaluationId) {
        report.info(`  ! "${scenario.name}" no está listo para simular: ${row.readinessErrors.join(' | ')}`);
        continue;
      }
      if (await simulate(api, neuquina, scenario, seedValue++, report)) simulated.push(scenario);
    }
  }

  const comparisons = await api.call('GET', `/projects/${neuquina}/comparisons`);
  if (comparisons.some((c) => c.name === COMPARISON)) report.existing(`comparación "${COMPARISON}"`);
  else if (simulated.length === 2) {
    await api.call('POST', `/projects/${neuquina}/comparisons`, {
      name: COMPARISON, description: 'Comparación de demostración.', items: simulated.map((s) => ({ scenarioId: s.id })),
    });
    report.created(`comparación "${COMPARISON}"`);
  } else report.info(`  · comparación "${COMPARISON}" pendiente: corré con --simulate para crearla`);

  report.info(`\nListo: ${report.counts.created} cambio(s), ${report.counts.existing} ya existían.`);
  return report.counts;
}

// ------------------------------------------------------------------------------------------ cleanup

export async function cleanup({ api: apiUrl, credentials, dryRun }) {
  const api = client(apiUrl);
  console.log(`API: ${apiUrl}`);
  await api.login(credentials.adminMail, credentials.adminPassword);
  const organizations = await api.call('GET', '/organizations');
  const projects = await api.call('GET', '/projects');
  const users = await api.call('GET', '/users');
  const target = selectCleanup({ organizations, projects, users, adminMail: credentials.adminMail });

  const plan = [];
  for (const project of target.projects) {
    for (const c of await api.call('GET', `/projects/${project.id}/comparisons`)) {
      plan.push({ label: `borrar comparación "${c.name}" (${project.name})`, run: () => api.call('DELETE', `/projects/${project.id}/comparisons/${c.id}`) });
    }
    const versions = await api.call('GET', `/projects/${project.id}/versions`);
    for (const v of versions.filter((v) => !v.parentVersionId)) {
      plan.push({ label: `borrar versión "${v.name}" y sus ramas (${project.name}); si tiene simulaciones queda inactiva con el proyecto`,
        run: async () => {
          try { await api.call('DELETE', `/version/${v.id}`); }
          catch (error) { if (error.status === 409) return 'tiene simulaciones: queda inactiva'; throw error; }
        } });
    }
    plan.push({ label: `desactivar proyecto "${project.name}"`, run: () => api.call('DELETE', `/projects/${project.id}`) });
  }
  for (const u of target.users.filter((u) => u.active)) {
    plan.push({ label: `desactivar usuario ${u.mail}`, run: () => api.call('DELETE', `/users/${u.id}`) });
  }
  for (const o of target.organizations.filter((o) => o.active)) {
    plan.push({ label: `desactivar organización "${o.name}"`, run: () => api.call('DELETE', `/organizations/${o.id}`) });
  }

  if (!plan.length) { console.log('No hay datos Demo para limpiar.'); return { planned: 0 }; }
  console.log(dryRun ? 'Se haría (simulacro, no se toca nada):' : 'Limpiando:');
  for (const step of plan) {
    if (dryRun) { console.log(`  · ${step.label}`); continue; }
    const note = await step.run();
    console.log(`  ✓ ${step.label}${note ? ` → ${note}` : ''}`);
  }
  return { planned: plan.length };
}

// ------------------------------------------------------------------------------------------ entry point

export async function main(argv = process.argv.slice(2), env = process.env) {
  const options = parseArgs(argv);
  const api = assertLocalTarget(options.api ?? env.ENERSCOPE_API ?? DEFAULT_API, env);
  const credentials = readCredentials(env, { needDemo: !options.cleanup });
  if (options.cleanup) return cleanup({ api, credentials, dryRun: options.dryRun });
  return seed({ api, simulate: options.simulate, credentials });
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    console.error('✗ ' + error.message);
    process.exitCode = 1;
  });
}
