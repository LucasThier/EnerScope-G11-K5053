import test from 'node:test';
import assert from 'node:assert/strict';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createLoader } from './loadModule.mjs';

const load = createLoader();
const root = fileURLToPath(new URL('../src/', import.meta.url));
const rules = load(resolve(root, 'utils/newVersion.ts'));

const mode = (over) => rules.newVersionMode({ loading: false, versionCount: 0, parentCount: 0, canCreateRoot: false, ...over });

test('while versions load the modal never shows a blocking message', () => {
  assert.equal(mode({ loading: true }), 'loading');
  assert.equal(rules.newVersionBlockMessage('loading'), null);
});

test('with no versions only root creators get the form', () => {
  assert.equal(mode({ canCreateRoot: true }), 'form');
  assert.equal(mode({ canCreateRoot: false }), 'blocked-first');
  assert.equal(rules.newVersionBlockMessage('blocked-first'), rules.FIRST_VERSION_ADMIN_MESSAGE);
});

test('with versions the form needs at least one branchable parent', () => {
  assert.equal(mode({ versionCount: 3, parentCount: 2 }), 'form');
  assert.equal(mode({ versionCount: 3, parentCount: 0 }), 'blocked-no-parent');
  assert.equal(mode({ versionCount: 3, parentCount: 0, canCreateRoot: true }), 'blocked-no-parent');
  assert.equal(rules.newVersionBlockMessage('blocked-no-parent'), rules.NO_EDITABLE_PARENT_MESSAGE);
  assert.equal(rules.newVersionBlockMessage('form'), null);
});

test('default parent prefers the requested one, then the root, then the first', () => {
  const parents = [{ id: 'b', parentVersionId: 'a' }, { id: 'a', parentVersionId: null }, { id: 'c', parentVersionId: 'a' }];
  assert.equal(rules.pickDefaultParent(parents, 'c'), 'c');
  assert.equal(rules.pickDefaultParent(parents, 'zzz'), 'a');
  assert.equal(rules.pickDefaultParent(parents), 'a');
  assert.equal(rules.pickDefaultParent(parents.filter((p) => p.id !== 'a')), 'b');
  assert.equal(rules.pickDefaultParent([], 'a'), null);
});

test('a requested parent that the caller cannot branch from falls back to a valid one', () => {
  const branchable = [{ id: 'c', parentVersionId: 'a' }];
  assert.equal(rules.pickDefaultParent(branchable, 'a'), 'c');
});

test('the name is trimmed and an empty one is rejected', () => {
  assert.deepEqual(rules.validateVersionName('  Base 2026  '), { name: 'Base 2026', error: null });
  assert.deepEqual(rules.validateVersionName('   '), { name: null, error: rules.NAME_REQUIRED_MESSAGE });
  assert.deepEqual(rules.validateVersionName(''), { name: null, error: rules.NAME_REQUIRED_MESSAGE });
});

test('the first version is created without a parent and later ones with the chosen parent', () => {
  assert.equal(rules.parentForNewVersion(0, 'stale'), null);
  assert.equal(rules.parentForNewVersion(2, 'p1'), 'p1');
});
