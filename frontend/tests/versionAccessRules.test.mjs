import test from 'node:test';
import assert from 'node:assert/strict';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createLoader } from './loadModule.mjs';

const stubs = {
  'react/jsx-runtime': {
    jsx: (type, props) => (typeof type === 'function' ? type(props) : { type, props }),
    jsxs: (type, props) => (typeof type === 'function' ? type(props) : { type, props }),
  },
};
const load = createLoader((name) => stubs[name]);
const root = fileURLToPath(new URL('../src/', import.meta.url));
const rules = load(resolve(root, 'utils/versionAccessRules.ts'));
const { VersionTree } = load(resolve(root, 'components/versions/VersionTree.tsx'));

const versionAccess = (versionId, overrides = {}) => ({
  versionId,
  canEdit: false,
  canManage: false,
  editableVerticals: [],
  ...overrides,
});
const access = {
  projectId: 'p',
  versions: [
    versionAccess('root', { canEdit: true, canManage: true }),
    versionAccess('child', { canEdit: true }),
    versionAccess('other'),
  ],
};

test('merging needs to manage the parent version', () => {
  assert.equal(rules.canMergeIntoParent(access, 'root'), true);
  assert.equal(rules.canMergeIntoParent(access, 'child'), false);
  assert.equal(rules.canMergeIntoParent(access, 'unknown'), false);
  assert.equal(rules.canMergeIntoParent(null, 'root'), false);
});

test('branching needs to edit the chosen parent version', () => {
  assert.equal(rules.canBranchFromParent(access, 'child'), true);
  assert.equal(rules.canBranchFromParent(access, 'other'), false);
  assert.equal(rules.canBranchFromParent(access, null), false);
});

test('the vertical check lists the scenario verticals the caller cannot edit', () => {
  const nodes = [{ type: 'WELL' }, { type: 'PIPELINE' }, { type: 'WELL' }];
  assert.deepEqual(rules.uneditableVerticals(nodes, ['EXTRACTION', 'TRANSPORTATION']), []);
  assert.deepEqual(rules.uneditableVerticals(nodes, ['EXTRACTION']), ['TRANSPORTATION']);
  assert.deepEqual(rules.uneditableVerticals([], []), []);
});

test('a node of an unknown type blocks the vertical check', () => {
  assert.deepEqual(rules.uneditableVerticals([{ type: 'MYSTERY' }], ['EXTRACTION']), [rules.UNKNOWN_VERTICAL]);
});

function treeLabels(canMergeInto, canImportInto = () => false) {
  const version = (id, parentVersionId) => ({ id, name: id, parentVersionId, createdAt: null });
  const tree = VersionTree({
    nodes: [
      {
        version: version('root', null),
        children: [{ version: version('child', 'root'), children: [] }],
      },
    ],
    canEdit: true,
    canMergeInto,
    canImportInto,
    unresolvedConflicts: {},
    onDelete: () => undefined,
    onImport: () => undefined,
    onMerge: () => undefined,
    onResolveConflicts: () => undefined,
  });
  const labels = [];
  (function walk(node) {
    if (Array.isArray(node)) node.forEach(walk);
    else if (node && typeof node === 'object') {
      if (node.type === 'button') labels.push(node.props['aria-label']);
      walk(node.props?.children);
    }
  })(tree);
  return labels;
}

function mergeLabels(canMergeInto) {
  return treeLabels(canMergeInto).filter((label) => label?.startsWith('Mergear'));
}

function importLabels(canImportInto) {
  return treeLabels(() => false, canImportInto).filter((label) => label?.startsWith('Importar'));
}

test('the merge button appears only when the parent can be managed', () => {
  assert.deepEqual(mergeLabels((parentId) => rules.canMergeIntoParent(access, parentId)), [
    'Mergear child con root',
  ]);
  assert.deepEqual(mergeLabels(() => false), []);
});

test('the import button appears only on the versions the caller can import into', () => {
  assert.deepEqual(importLabels(() => false), []);
  assert.deepEqual(importLabels((versionId) => versionId === 'child'), ['Importar CSV en child']);
  assert.deepEqual(importLabels(() => true), ['Importar CSV en root', 'Importar CSV en child']);
  const noRoot = { ...access, versions: access.versions.map((v) => (v.versionId === 'root' ? { ...v, canEdit: false } : v)) };
  assert.deepEqual(importLabels((versionId) => rules.findVersionAccess(noRoot, versionId)?.canEdit === true), ['Importar CSV en child']);
});

const full = (overrides = {}) => ({
  versionId: 'v1',
  canEdit: false,
  canSimulate: false,
  canViewEconomicResults: true,
  canViewEconomicConfiguration: true,
  canEditEconomicConfiguration: false,
  editableVerticals: [],
  ...overrides,
});

test('scenario editing: a missing access or version denies every write', () => {
  const denied = { canSimulate: false, canViewResults: true, canViewConfiguration: true, canEditConfiguration: false, editableVerticals: [] };
  assert.deepEqual(rules.versionEconomics(null, 'v1'), denied);
  assert.deepEqual(rules.versionEconomics({ versions: [full()] }, 'missing'), denied);
  assert.deepEqual(rules.versionEconomics({ versions: [full()] }, undefined), denied);
});

test('scenario editing: each flag comes from the version access entry', () => {
  const entry = full({ canSimulate: true, canEditEconomicConfiguration: true, editableVerticals: ['EXPORT'], canViewEconomicResults: false });
  assert.deepEqual(rules.versionEconomics({ versions: [entry] }, 'v1'), {
    canSimulate: true,
    canViewResults: false,
    canViewConfiguration: true,
    canEditConfiguration: true,
    editableVerticals: ['EXPORT'],
  });
});

test('scenario editing: a simulator without configuration rights cannot edit but can simulate', () => {
  const caps = rules.versionEconomics({ versions: [full({ canSimulate: true })] }, 'v1');
  assert.equal(caps.canSimulate, true);
  assert.equal(caps.canEditConfiguration, false);
});

test('access failure message distinguishes forbidden, unavailable and healthy', () => {
  assert.equal(rules.accessFailureMessage(null, false), null);
  assert.equal(rules.accessFailureMessage(null, true), rules.ACCESS_FORBIDDEN_MESSAGE);
  assert.equal(rules.accessFailureMessage('boom', false), rules.ACCESS_UNAVAILABLE_MESSAGE);
  assert.equal(rules.accessFailureMessage('boom', true), rules.ACCESS_FORBIDDEN_MESSAGE);
});

test('copy is blocked while access failed, regardless of the editable verticals', () => {
  const reason = rules.scenarioCopyBlockedReason([{ type: 'WELL' }], ['EXTRACTION'], true);
  assert.match(reason, /No pudimos verificar/);
});

test('copy is blocked listing the missing verticals, and allowed when all are editable', () => {
  const nodes = [{ type: 'WELL' }, { type: 'PIPELINE' }];
  assert.equal(rules.scenarioCopyBlockedReason(nodes, ['EXTRACTION', 'TRANSPORTATION'], false), null);
  assert.match(rules.scenarioCopyBlockedReason(nodes, ['EXTRACTION'], false), /Te falta permiso en: .+\.$/);
  assert.match(rules.scenarioCopyBlockedReason([{ type: 'MYSTERY' }], ['EXTRACTION'], false), /tipo de nodo desconocido/);
  assert.equal(rules.scenarioCopyBlockedReason([], [], false), null);
});

test('versioning: without access nothing can be created, edited or managed', () => {
  assert.deepEqual(rules.projectVersioning(null, 0), { canManage: false, canCreateRoot: false, canEdit: false, canCreateVersion: false });
  assert.deepEqual(rules.projectVersioning(null, 3), { canManage: false, canCreateRoot: false, canEdit: false, canCreateVersion: false });
});

test('versioning: the first version needs root creation, later ones need an editable parent', () => {
  const editor = { canManageProject: false, canCreateRootVersions: false, versions: [full({ canEdit: true })] };
  assert.equal(rules.projectVersioning(editor, 0).canCreateVersion, false);
  assert.equal(rules.projectVersioning(editor, 1).canCreateVersion, true);
  assert.equal(rules.projectVersioning(editor, 1).canEdit, true);
  const admin = { canManageProject: true, canCreateRootVersions: true, versions: [] };
  assert.equal(rules.projectVersioning(admin, 0).canCreateVersion, true);
  assert.equal(rules.projectVersioning(admin, 2).canCreateVersion, false);
  assert.equal(rules.projectVersioning(admin, 2).canEdit, true);
  const viewer = { canManageProject: false, canCreateRootVersions: false, versions: [full()] };
  assert.equal(rules.projectVersioning(viewer, 1).canEdit, false);
});
