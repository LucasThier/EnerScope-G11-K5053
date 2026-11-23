import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { createLoader } from './loadModule.mjs';

const src = (path) => fileURLToPath(new URL(`../src/${path}`, import.meta.url));
const load = createLoader();
const mode = load(src('hooks/sidebarMode.ts'));

test('the sidebar is a fixed column from 80rem and a drawer below it, with no third mode', () => {
  assert.equal(mode.FIXED_QUERY, '(min-width: 80rem)');
  assert.equal(mode.sidebarMode(true), 'fixed');
  assert.equal(mode.sidebarMode(false), 'drawer');
});

test('fixed: always on screen and pushing the content, whatever the drawer flag says', () => {
  for (const open of [false, true]) {
    assert.deepEqual(mode.sidebarLayout('fixed', open), {
      isFixed: true,
      isDrawer: false,
      isVisible: true,
      pushes: true,
    });
  }
});

test('drawer: on screen only while open, and it never pushes the content', () => {
  assert.deepEqual(mode.sidebarLayout('drawer', false), {
    isFixed: false,
    isDrawer: true,
    isVisible: false,
    pushes: false,
  });
  assert.deepEqual(mode.sidebarLayout('drawer', true), {
    isFixed: false,
    isDrawer: true,
    isVisible: true,
    pushes: false,
  });
});

test('the retired sidebarPinned key is removed, and a storage that throws or is missing is survived', () => {
  const removed = [];
  mode.forgetRetiredPreference({ removeItem: (key) => removed.push(key) });
  assert.deepEqual(removed, ['sidebarPinned']);
  assert.doesNotThrow(() =>
    mode.forgetRetiredPreference({
      removeItem: () => {
        throw new Error('SecurityError');
      },
    }),
  );
  assert.doesNotThrow(() => mode.forgetRetiredPreference(undefined));
});

test('the provider asks one media query, the shared one, and nothing about pins or 64rem', () => {
  const provider = readFileSync(src('hooks/SidebarProvider.tsx'), 'utf8');
  assert.equal((provider.match(/matchMedia\(/g) ?? []).length, 2); // initial read + listener
  assert.equal((provider.match(/matchMedia\(FIXED_QUERY\)/g) ?? []).length, 2);
  assert.doesNotMatch(provider, /min-width|64rem|DESKTOP_QUERY|PIN_QUERY|useLocalPreference|'sidebarPinned'|isPinned|togglePin/);
  const context = readFileSync(src('hooks/useSidebar.ts'), 'utf8');
  assert.doesNotMatch(context, /isPinned|canPin|togglePin|origin/);
});
