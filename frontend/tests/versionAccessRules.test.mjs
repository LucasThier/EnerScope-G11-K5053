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

function mergeLabels(canMergeInto) {
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
    unresolvedConflicts: {},
    onDelete: () => undefined,
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
  return labels.filter((label) => label?.startsWith('Mergear'));
}

test('the merge button appears only when the parent can be managed', () => {
  assert.deepEqual(mergeLabels((parentId) => rules.canMergeIntoParent(access, parentId)), [
    'Mergear child con root',
  ]);
  assert.deepEqual(mergeLabels(() => false), []);
});
