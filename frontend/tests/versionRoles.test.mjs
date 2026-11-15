import test from 'node:test';
import assert from 'node:assert/strict';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createLoader } from './loadModule.mjs';

const root = fileURLToPath(new URL('../src/', import.meta.url));
const load = createLoader();
const labels = load(resolve(root, 'components/projects/versionRoleLabels.ts'));
const roles = load(resolve(root, 'utils/versionRoles.ts'));

const ALL_VERTICALS = ['EXTRACTION', 'TRANSPORTATION', 'LIQUEFACTION', 'EXPORT', 'INTERNAL_CONSUMPTION'];

const entry = (over = {}) => ({
  versionId: 'v1',
  versionName: 'Base',
  source: 'PROJECT_FALLBACK',
  roles: [],
  effectiveRole: 'EDITOR',
  editableVerticals: ALL_VERTICALS,
  canView: true,
  canEdit: true,
  canEditConnections: true,
  canSimulate: true,
  canManage: false,
  canManageRoles: false,
  canViewEconomicResults: true,
  canViewEconomicConfiguration: true,
  canEditEconomicConfiguration: true,
  ...over,
});

const access = (versions, over = {}) => ({
  projectId: 'p',
  platformAdmin: false,
  projectRole: 'EDITOR',
  projectPermissions: [],
  canManageProject: false,
  canCreateVersions: true,
  versions,
  ...over,
});

const summary = (id, name, parentVersionId, createdAt) => ({ id, name, parentVersionId, createdAt });

const person = (userId, firstName, over = {}) => ({
  id: `m-${userId}`,
  userId,
  userMail: `${userId}@example.com`,
  firstName,
  lastName: 'Perez',
  jobTitle: null,
  active: true,
  memberType: 'EDITOR',
  permissions: [],
  ...over,
});

test('role labels use the product names, with the vertical for editors', () => {
  assert.equal(labels.versionRoleLabel('ADMIN', null), 'Administrador de versión');
  assert.equal(labels.versionRoleLabel('SIMULATOR', null), 'Simulador');
  assert.equal(labels.versionRoleLabel('EDITOR', 'EXTRACTION'), 'Editor de Extracción');
  assert.equal(labels.versionRoleLabel('EDITOR', 'INTERNAL_CONSUMPTION'), 'Editor de Consumo interno');
  assert.equal(labels.versionRoleLabel('EDITOR', null), 'Editor');
  assert.equal(labels.PROJECT_ROLE_ACCESS_LABELS.ADMIN, 'Administrador del proyecto');
  assert.equal(labels.PROJECT_ROLE_ACCESS_LABELS.EDITOR, 'Editor del proyecto');
  assert.equal(labels.ALL_VERSIONS_ADMIN_LABEL, 'Administra todas las versiones');
});

test('caller project role label falls back to platform administrator only without a project role', () => {
  assert.equal(roles.callerProjectRoleLabel('EDITOR', false), 'Editor del proyecto');
  assert.equal(roles.callerProjectRoleLabel('ADMIN', true), 'Administrador del proyecto');
  assert.equal(roles.callerProjectRoleLabel(null, true), 'Administrador de plataforma');
  assert.equal(roles.callerProjectRoleLabel(undefined, false), null);
});

test('grants sort admin first, then editors by vertical order, then simulator', () => {
  const sorted = roles.sortGrants([
    { type: 'SIMULATOR', vertical: null },
    { type: 'EDITOR', vertical: 'EXPORT' },
    { type: 'ADMIN', vertical: null },
    { type: 'EDITOR', vertical: 'EXTRACTION' },
  ]);
  assert.deepEqual(sorted.map((grant) => `${grant.type}/${grant.vertical}`), [
    'ADMIN/null', 'EDITOR/EXTRACTION', 'EDITOR/EXPORT', 'SIMULATOR/null',
  ]);
  const memberRoles = roles.sortMemberRoles([
    { roleId: 'r2', memberType: 'EDITOR', vertical: 'TRANSPORTATION', permissions: [] },
    { roleId: 'r1', memberType: 'EDITOR', vertical: 'EXTRACTION', permissions: [] },
  ]);
  assert.deepEqual(memberRoles.map((role) => role.roleId), ['r1', 'r2']);
});

test('a project admin administers every version and roles without effect are reported', () => {
  const summaryOf = roles.summarizeAccess(access([
    entry({ versionId: 'a', source: 'PROJECT_ADMIN', canManage: true, roles: [{ type: 'SIMULATOR', vertical: null }] }),
    entry({ versionId: 'b', source: 'PROJECT_ADMIN', canManage: true }),
  ], { projectRole: 'ADMIN', canManageProject: true }));
  assert.equal(summaryOf.administersAll, true);
  assert.deepEqual(summaryOf.withoutEffect.map((item) => item.versionId), ['a']);
  assert.deepEqual(summaryOf.explicit, []);
  assert.equal(summaryOf.fallbackCount, 0);
});

test('a project member sees explicit versions apart from the ones that follow the project role', () => {
  const summaryOf = roles.summarizeAccess(access([
    entry({ versionId: 'a', source: 'VERSION_ROLES', roles: [{ type: 'SIMULATOR', vertical: null }] }),
    entry({ versionId: 'b' }),
    entry({ versionId: 'c' }),
  ]));
  assert.equal(summaryOf.administersAll, false);
  assert.deepEqual(summaryOf.explicit.map((item) => item.versionId), ['a']);
  assert.equal(summaryOf.fallbackCount, 2);
  assert.equal(summaryOf.projectRole, 'EDITOR');
});

test('a platform admin administers everything even when not a project member', () => {
  const summaryOf = roles.summarizeAccess(access(
    [entry({ source: 'PLATFORM_ADMIN', canManage: true })],
    { platformAdmin: true, projectRole: null, canManageProject: true },
  ));
  assert.equal(summaryOf.administersAll, true);
  assert.equal(summaryOf.platformAdmin, true);
  assert.equal(summaryOf.projectRole, null);
});

test('managing the roles of a version is decided only by the canManageRoles flag from /access', () => {
  assert.equal(roles.canManageVersionRoles(entry({ canManageRoles: true })), true);
  assert.equal(roles.canManageVersionRoles(entry({ canManage: true, canManageRoles: false })), false);
  assert.equal(roles.canManageVersionRoles(entry({ canManageRoles: false, source: 'PROJECT_ADMIN' })), false);
  assert.equal(roles.canManageVersionRoles(undefined), false);
  assert.equal(roles.canManageVersionRoles(null), false);
  assert.equal(roles.administersEveryVersion(entry({ source: 'PROJECT_ADMIN' })), true);
  assert.equal(roles.administersEveryVersion(entry({ source: 'PLATFORM_ADMIN' })), true);
  assert.equal(roles.administersEveryVersion(entry({ source: 'VERSION_ROLES' })), false);
});

test('capability labels follow the backend flags', () => {
  assert.deepEqual(
    roles.capabilityLabels(entry({ canEdit: false, canEditConnections: false, editableVerticals: [], canManage: false })),
    ['Ver', 'Simular'],
  );
  assert.deepEqual(
    roles.capabilityLabels(entry({ editableVerticals: ['EXTRACTION', 'INTERNAL_CONSUMPTION'], canManage: false })),
    ['Ver', 'Editar (Extracción, Consumo interno)', 'Editar conexiones', 'Simular'],
  );
  assert.deepEqual(
    roles.capabilityLabels(entry({ canManage: true })),
    ['Ver', 'Editar todas las verticales', 'Editar conexiones', 'Simular', 'Administrar'],
  );
});

test('the picker badge marks versions the caller administers or holds roles in', () => {
  assert.equal(roles.versionBadge(entry({ canManage: true })), 'Administrás');
  assert.equal(roles.versionBadge(entry({ source: 'VERSION_ROLES' })), 'Con roles');
  assert.equal(roles.versionBadge(entry()), null);
});

test('assignable people exclude project admins and inactive accounts, sorted by name', () => {
  const list = [
    person('u1', 'Zoe'),
    person('u2', 'Ana', { memberType: 'ADMIN' }),
    person('u3', 'Bruno', { active: false }),
    person('u4', 'Carla'),
  ];
  assert.deepEqual(roles.assignableMembers(list).map((member) => member.userId), ['u4', 'u1']);
});

test('role choices subtract what the person already holds', () => {
  const none = roles.availableChoices([]);
  assert.deepEqual(none, { admin: true, simulator: true, editorVerticals: ALL_VERTICALS });
  assert.deepEqual(roles.availableRoleTypes(none), ['ADMIN', 'EDITOR', 'SIMULATOR']);

  const some = roles.availableChoices([
    { roleId: 'r1', memberType: 'ADMIN', vertical: null, permissions: [] },
    { roleId: 'r2', memberType: 'EDITOR', vertical: 'EXPORT', permissions: [] },
  ]);
  assert.equal(some.admin, false);
  assert.equal(some.simulator, true);
  assert.deepEqual(some.editorVerticals, ['EXTRACTION', 'TRANSPORTATION', 'LIQUEFACTION', 'INTERNAL_CONSUMPTION']);
  assert.deepEqual(roles.availableRoleTypes(some), ['EDITOR', 'SIMULATOR']);

  const everything = roles.availableChoices([
    { roleId: 'r1', memberType: 'ADMIN', vertical: null, permissions: [] },
    { roleId: 'r2', memberType: 'SIMULATOR', vertical: null, permissions: [] },
    ...ALL_VERTICALS.map((vertical, index) => ({ roleId: `e${index}`, memberType: 'EDITOR', vertical, permissions: [] })),
  ]);
  assert.deepEqual(roles.availableRoleTypes(everything), []);
});

test('an editor grant makes one request per vertical and the others omit the vertical', () => {
  assert.deepEqual(roles.grantRequests('u1', 'EDITOR', ['EXPORT', 'EXTRACTION']), [
    { userId: 'u1', memberType: 'EDITOR', vertical: 'EXTRACTION' },
    { userId: 'u1', memberType: 'EDITOR', vertical: 'EXPORT' },
  ]);
  assert.deepEqual(roles.grantRequests('u1', 'ADMIN', ['EXPORT']), [{ userId: 'u1', memberType: 'ADMIN' }]);
  assert.deepEqual(roles.grantRequests('u1', 'SIMULATOR', []), [{ userId: 'u1', memberType: 'SIMULATOR' }]);
  assert.deepEqual(roles.grantRequests('u1', 'EDITOR', []), []);
});

test('the last role of a member is detected', () => {
  assert.equal(roles.isLastRole({ roles: [{}] }), true);
  assert.equal(roles.isLastRole({ roles: [{}, {}] }), false);
});

test('version rows nest by parent and flatten while filtering, ignoring accents', () => {
  const projectAccess = access([
    entry({ versionId: 'root', versionName: 'Base' }),
    entry({ versionId: 'child', versionName: 'Extracción alta', canManage: true, canManageRoles: true }),
    entry({ versionId: 'grand', versionName: 'Escenario final' }),
  ]);
  const summaries = [
    summary('root', 'Base', null, '2026-01-01T00:00:00Z'),
    summary('child', 'Extracción alta', 'root', '2026-02-01T00:00:00Z'),
    summary('grand', 'Escenario final', 'child', '2026-03-01T00:00:00Z'),
  ];
  assert.deepEqual(roles.versionRows(projectAccess, summaries, '', false).map((row) => [row.id, row.depth]), [
    ['root', 0], ['child', 1], ['grand', 2],
  ]);
  assert.deepEqual(roles.versionRows(projectAccess, summaries, 'extraccion', false).map((row) => [row.id, row.depth]), [
    ['child', 0],
  ]);
  assert.deepEqual(roles.versionRows(projectAccess, summaries, '', true).map((row) => row.id), ['child']);
  assert.deepEqual(roles.versionRows(projectAccess, summaries, 'zzz', false), []);
});

test('the manageable filter and the default version follow canManageRoles, not canManage', () => {
  const projectAccess = access([
    entry({ versionId: 'a', versionName: 'A', canManage: true }),
    entry({ versionId: 'b', versionName: 'B', canManage: true, canManageRoles: true }),
  ]);
  assert.deepEqual(roles.versionRows(projectAccess, [], '', true).map((row) => row.id), ['b']);
  assert.equal(roles.defaultVersionId(roles.versionRows(projectAccess, [], '', false), null), 'b');
});

test('without version summaries every version is listed flat', () => {
  const projectAccess = access([entry({ versionId: 'a', versionName: 'A' }), entry({ versionId: 'b', versionName: 'B' })]);
  const rows = roles.versionRows(projectAccess, [], '', false);
  assert.deepEqual(rows.map((row) => [row.id, row.depth]), [['a', 0], ['b', 0]]);
});

test('the default version is the requested one, else the first the caller manages, else the first', () => {
  const rows = [
    { id: 'a', name: 'A', depth: 0, access: entry({ canManage: true }) },
    { id: 'b', name: 'B', depth: 0, access: entry({ canManageRoles: true }) },
  ];
  assert.equal(roles.defaultVersionId(rows, 'a'), 'a');
  assert.equal(roles.defaultVersionId(rows, 'gone'), 'b');
  assert.equal(roles.defaultVersionId(rows, null), 'b');
  assert.equal(roles.defaultVersionId([rows[0]], null), 'a');
  assert.equal(roles.defaultVersionId([], null), null);
});

test('member listings sort by full name', () => {
  const sorted = roles.sortVersionMembers([
    { id: '1', firstName: 'Zoe', lastName: 'Gil', roles: [] },
    { id: '2', firstName: 'Ana', lastName: 'Gil', roles: [] },
  ]);
  assert.deepEqual(sorted.map((member) => member.id), ['2', '1']);
  assert.equal(roles.fullName({ firstName: 'Ana', lastName: 'Gil' }), 'Ana Gil');
});
