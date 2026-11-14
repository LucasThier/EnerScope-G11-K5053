import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync, writeFileSync, readdirSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, resolve, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';
const require = createRequire(import.meta.url);
const state = { project: null, projects: [], loading: false, role: 'ADMIN', member: true, user: null, loggedOut: false };
const cache = new Map();
function load(path) {
  if (cache.has(path)) return cache.get(path).exports;
  const module = { exports: {} }; cache.set(path, module);
  const code = ts.transpileModule(readFileSync(path, 'utf8'), { compilerOptions: {
    module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, target: ts.ScriptTarget.ES2022,
  }}).outputText;
  const localRequire = name => {
    if (name === 'react') return { useMemo: fn => fn() };
    if (name === 'react/jsx-runtime') return { jsx: (type, props) => (typeof type === 'function' ? type(props) : { type, props }), jsxs: (type, props) => (typeof type === 'function' ? type(props) : { type, props }) };
    if (name === 'react-router-dom') return { Navigate: props => ({ navigate: props.to }), Outlet: () => 'outlet' };
    if (name.endsWith('/useActiveProject')) return { useActiveProject: () => ({ activeProject: state.project, projects: state.projects, error: null, isLoading: state.loading }) };
    if (name.endsWith('/useAuth')) return { useAuth: () => ({ user: state.loggedOut ? null : (state.user ?? { platformRole: state.role }) }) };
    if (name.endsWith('/useMyOrganizations')) return { useMyOrganizations: () => ({ belongsToOrganization: state.member, loading: false }) };
    if (!name.startsWith('.')) return require(name);
    const base = resolve(dirname(path), name);
    return load([base + '.ts', base + '.tsx', base + '/index.tsx'].find(existsSync));
  };
  new Function('require', 'module', 'exports', code)(localRequire, module, module.exports);
  return module.exports;
}
const root = fileURLToPath(new URL('../src/', import.meta.url));
const goldenPath = fileURLToPath(new URL('./permissionsNav.golden.json', import.meta.url));
const { useNavigationTree } = load(resolve(root, 'hooks/useNavigationTree.ts'));
const { canManageProject } = load(resolve(root, 'utils/projectPermissions.ts'));
const { canManageOrganizationUsers } = load(resolve(root, 'utils/membershipGuards.ts'));
const { RoleRoute } = load(resolve(root, 'routes/RoleRoute.tsx'));

const ROLES = ['ADMIN', 'USER'];
const PROJECT_ROLES = ['ADMIN', 'EDITOR', null];

test('canManageProject: platform admin or project ADMIN only', () => {
  for (const admin of [true, false]) for (const role of [...PROJECT_ROLES, undefined]) {
    assert.equal(canManageProject(role, admin), admin || role === 'ADMIN');
  }
});

test('canManageOrganizationUsers: platform admin or MANAGE_ORGANIZATION on an active own membership', () => {
  const me = { id: 'u1', platformRole: 'USER' };
  const admin = { id: 'u9', platformRole: 'ADMIN' };
  const member = (over = {}) => ({ userId: 'u1', active: true, permissions: ['MANAGE_ORGANIZATION'], ...over });
  assert.equal(canManageOrganizationUsers(null, [member()]), false);
  assert.equal(canManageOrganizationUsers(admin, []), true);
  assert.equal(canManageOrganizationUsers(me, [member()]), true);
  assert.equal(canManageOrganizationUsers(me, [member({ active: false })]), false);
  assert.equal(canManageOrganizationUsers(me, [member({ permissions: ['VIEW_ORGANIZATION'] })]), false);
  assert.equal(canManageOrganizationUsers(me, [member({ userId: 'u2' })]), false);
  assert.equal(canManageOrganizationUsers(me, []), false);
});

test('RoleRoute: redirects logged-out to /login, role mismatch to /, match renders the outlet', () => {
  state.loggedOut = true;
  assert.deepEqual(RoleRoute({ role: 'ADMIN' }), { navigate: '/login' });
  state.loggedOut = false;
  Object.assign(state, { user: { platformRole: 'ADMIN' } });
  assert.equal(RoleRoute({ role: 'ADMIN' }), 'outlet');
  Object.assign(state, { user: { platformRole: 'USER' } });
  assert.deepEqual(RoleRoute({ role: 'ADMIN' }), { navigate: '/' });
  assert.equal(RoleRoute({ role: 'USER' }), 'outlet');
  state.user = null;
});

function signature() {
  const out = {};
  for (const role of ROLES) for (const member of [false, true]) for (const myRole of PROJECT_ROLES) for (const withProject of [false, true]) for (const loading of [false, true]) {
    const project = withProject ? { id: 'p', name: 'P', myRole } : null;
    Object.assign(state, { user: null, role, member, loading, project, projects: withProject ? [project] : [] });
    out[[role, member, myRole, withProject, loading].join('|')] = useNavigationTree().map(group => ({
      title: group.title,
      items: group.items.map(item => ({
        label: item.label, fullLabel: item.fullLabel, to: item.to, availability: item.availability, reason: item.reason,
        children: item.children?.map(child => ({ label: child.label, fullLabel: child.fullLabel, to: child.to, availability: child.availability, reason: child.reason })),
      })),
    }));
  }
  state.user = null;
  return out;
}

test('navigation tree is identical to the recorded behaviour for every role/project combination', () => {
  const current = JSON.parse(JSON.stringify(signature()));
  if (process.env.RECORD_PERMISSIONS_GOLDEN === '1') writeFileSync(goldenPath, JSON.stringify(current, null, 1));
  assert.deepEqual(current, JSON.parse(readFileSync(goldenPath, 'utf8')));
});

const legacy = {
  isAdmin: user => user?.platformRole === 'ADMIN',
  versionsCanEdit: (user, myRole) => user?.platformRole === 'ADMIN' || myRole === 'ADMIN' || myRole === 'EDITOR',
  modalCanManage: (user, membership) => user?.platformRole === 'ADMIN' || membership?.memberType === 'ADMIN',
  permissionsCanEdit: (user, members) => user?.platformRole === 'ADMIN' || !!members?.some(m => m.userId === user?.id && m.active && m.permissions.includes('EDIT_PROJECT')),
};
const USERS = [null, undefined, { id: 'u1', platformRole: 'ADMIN' }, { id: 'u1', platformRole: 'USER' }];
const MEMBER_SETS = [null, undefined, [], [{ userId: 'u1', active: true, permissions: ['EDIT_PROJECT'] }],
  [{ userId: 'u1', active: true, permissions: ['VIEW_PROJECT'] }], [{ userId: 'u1', active: false, permissions: ['EDIT_PROJECT'] }],
  [{ userId: 'u2', active: true, permissions: ['EDIT_PROJECT', 'MANAGE_PROJECT'] }],
  [{ userId: 'u2', active: true, permissions: ['VIEW_PROJECT'] }, { userId: 'u1', active: true, permissions: ['MANAGE_PROJECT', 'EDIT_PROJECT'] }]];

test('access module matches the previously inline checks', async () => {
  const access = load(resolve(root, 'permissions/access.ts'));
  for (const user of USERS) {
    assert.equal(access.isPlatformAdmin(user), legacy.isAdmin(user));
    for (const role of ['ADMIN', 'USER']) assert.equal(access.hasPlatformRole(user, role), user?.platformRole === role);
    for (const myRole of [...PROJECT_ROLES, undefined]) {
      assert.equal(access.canEditProjectByRole(myRole, access.isPlatformAdmin(user)), legacy.versionsCanEdit(user, myRole));
      const membership = myRole === undefined ? undefined : { memberType: myRole };
      assert.equal(access.canManageProject(membership?.memberType, access.isPlatformAdmin(user)), legacy.modalCanManage(user, membership));
    }
    for (const members of MEMBER_SETS) assert.equal(access.canEditProjectByPermissions(user, members), legacy.permissionsCanEdit(user, members));
  }
});

test('executive role is inactive by default and never alters the resolved capabilities', () => {
  const access = load(resolve(root, 'permissions/access.ts'));
  assert.equal(access.ROLE_FEATURES.executive, false);
  const onlySimulator = [{ type: 'SIMULATOR', vertical: null }];
  for (const platformAdmin of [true, false]) for (const projectRole of PROJECT_ROLES) {
    const caps = access.resolveVersionCapabilities({ platformAdmin, projectRole, versionRoles: onlySimulator });
    const canEdit = platformAdmin || projectRole === 'ADMIN' || projectRole === 'EDITOR';
    assert.equal(caps.isExecutive, false);
    assert.equal(caps.canSimulate, canEdit);
    assert.equal(caps.canEditConnections, canEdit);
    assert.equal(caps.canEditVertical('EXTRACTION'), canEdit);
    assert.equal(caps.canManageTeam, platformAdmin || projectRole === 'ADMIN');
    assert.equal(caps.canViewEconomics, true);
  }
});

test('executive role when enabled: view and simulate only, economics is a separate switch', () => {
  const access = load(resolve(root, 'permissions/access.ts'));
  const base = { platformAdmin: false, projectRole: 'EDITOR', versionRoles: [{ type: 'SIMULATOR', vertical: null }], executiveEnabled: true };
  const caps = access.resolveVersionCapabilities(base);
  assert.equal(caps.isExecutive, true);
  assert.equal(caps.canView, true);
  assert.equal(caps.canSimulate, true);
  assert.equal(caps.canEditConnections, false);
  assert.equal(caps.canManageTeam, false);
  for (const vertical of ['EXTRACTION', 'TRANSPORTATION', 'LIQUEFACTION', 'EXPORT', 'INTERNAL_CONSUMPTION']) assert.equal(caps.canEditVertical(vertical), false);
  assert.equal(access.EXECUTIVE_CAN_VIEW_ECONOMICS, true);
  assert.equal(caps.canViewEconomics, true);
  assert.equal(access.resolveVersionCapabilities({ ...base, executiveCanViewEconomics: false }).canViewEconomics, false);
  assert.equal(access.resolveVersionCapabilities({ ...base, versionRoles: [{ type: 'SIMULATOR', vertical: null }, { type: 'EDITOR', vertical: 'EXPORT' }] }).isExecutive, false);
  assert.equal(access.resolveVersionCapabilities({ ...base, versionRoles: [] }).isExecutive, false);
  assert.equal(access.resolveVersionCapabilities({ ...base, platformAdmin: true }).isExecutive, false);
});

test('platform role checks live only in the permissions module, except display labels and user administration lists', () => {
  const allowed = new Set(['permissions/access.ts', 'components/layout/UserMenu.tsx', 'components/dashboard/AccountCard.tsx',
    'pages/ProfilePage.tsx', 'components/users/UsersTable.tsx', 'components/users/UserDetailModal.tsx', 'components/users/ChangeRoleModal.tsx',
    'pages/AdminUsersPage.tsx']);
  const walk = dir => readdirSync(dir, { withFileTypes: true }).flatMap(e => e.isDirectory() ? walk(join(dir, e.name)) : [join(dir, e.name)]);
  const offenders = walk(root).filter(f => /\.tsx?$/.test(f) && !f.includes('/types/'))
    .map(f => relative(root, f)).filter(f => !allowed.has(f))
    .filter(f => /platformRole\s*(===|!==)\s*'(ADMIN|USER)'/.test(readFileSync(resolve(root, f), 'utf8')));
  assert.deepEqual(offenders, []);
});
