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
const { canManageOrganizationUsers, canAddOrganizationMembers } = load(resolve(root, 'utils/membershipGuards.ts'));
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

test('canAddOrganizationMembers: requires an active organization on top of the management rule', () => {
  const me = { id: 'u1', platformRole: 'USER' };
  const admin = { id: 'u9', platformRole: 'ADMIN' };
  const manager = [{ userId: 'u1', active: true, permissions: ['MANAGE_ORGANIZATION'] }];
  assert.equal(canAddOrganizationMembers(me, manager, { active: true }), true);
  assert.equal(canAddOrganizationMembers(me, manager, { active: false }), false);
  assert.equal(canAddOrganizationMembers(admin, [], { active: true }), true);
  assert.equal(canAddOrganizationMembers(admin, [], { active: false }), false);
  assert.equal(canAddOrganizationMembers(admin, [], null), false);
  assert.equal(canAddOrganizationMembers(me, [], { active: true }), false);
  assert.equal(canAddOrganizationMembers(null, manager, { active: true }), false);
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
  modalCanManage: (user, membership) => user?.platformRole === 'ADMIN' || membership?.memberType === 'ADMIN',
};
const USERS = [null, undefined, { id: 'u1', platformRole: 'ADMIN' }, { id: 'u1', platformRole: 'USER' }];

test('access module matches the previously inline checks', async () => {
  const access = load(resolve(root, 'permissions/access.ts'));
  for (const user of USERS) {
    assert.equal(access.isPlatformAdmin(user), legacy.isAdmin(user));
    for (const role of ['ADMIN', 'USER']) assert.equal(access.hasPlatformRole(user, role), user?.platformRole === role);
    for (const myRole of [...PROJECT_ROLES, undefined]) {
      const membership = myRole === undefined ? undefined : { memberType: myRole };
      assert.equal(access.canManageProject(membership?.memberType, access.isPlatformAdmin(user)), legacy.modalCanManage(user, membership));
    }
  }
});

test('platform role checks live only in the permissions module, except display labels and user administration lists', () => {
  const allowed = new Set(['permissions/access.ts', 'components/layout/UserMenu.tsx',
    'pages/ProfilePage.tsx', 'components/users/UsersTable.tsx', 'components/users/UserDetailModal.tsx', 'components/users/ChangeRoleModal.tsx',
    'pages/AdminUsersPage.tsx']);
  const walk = dir => readdirSync(dir, { withFileTypes: true }).flatMap(e => e.isDirectory() ? walk(join(dir, e.name)) : [join(dir, e.name)]);
  const offenders = walk(root).filter(f => /\.tsx?$/.test(f) && !f.includes('/types/'))
    .map(f => relative(root, f)).filter(f => !allowed.has(f))
    .filter(f => /platformRole\s*(===|!==)\s*'(ADMIN|USER)'/.test(readFileSync(resolve(root, f), 'utf8')));
  assert.deepEqual(offenders, []);
});
