import test from 'node:test';
import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/*
 * The economic module used to write its own selects, buttons, cards and dialog.
 * They come from components/ui now (SelectField / FilterSelect, Button, Card,
 * Modal), which is what gives every control the same height, the same
 * control-border and the same focus ring. A static scan keeps it that way.
 */
const root = fileURLToPath(new URL('../src/', import.meta.url));

function walk(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const full = path.join(directory, entry.name);
    if (entry.isDirectory()) return walk(full);
    return entry.name.endsWith('.tsx') ? [full] : [];
  });
}
const files = walk(root).map((file) => ({
  name: path.relative(root, file).split(path.sep).join('/'),
  source: readFileSync(file, 'utf8'),
}));
const economics = files.filter((file) => file.name.startsWith('components/economics/'));

/**
 * Hand-written `<button>` that may stay in components/economics, and why. The
 * count is exact: removing one without updating this list fails too.
 * - EconomicEditor: the vertical section menu (aria-current, brand-50 fill on
 *   the current one — Button has no "selected" state, and Tabs is a horizontal
 *   tablist with panels) and the two in-text "go to section" links of the summary.
 * - AdvancedEconomicEditor: the same section menu.
 * - ScenarioResults: the Operación / Económico segmented tablist with Home and
 *   End keys and both panels kept mounted — Tabs would change that behaviour.
 */
const BUTTON_EXCEPTIONS = {
  'components/economics/EconomicEditor.tsx': 3,
  'components/economics/AdvancedEconomicEditor.tsx': 1,
  'components/economics/ScenarioResults.tsx': 1,
};

test('the scan reaches the economic module', () => {
  assert.ok(economics.length >= 10, `${economics.length} files`);
  assert.ok(economics.some((file) => file.name === 'components/economics/EconomicEditor.tsx'));
});

test('no <select> is written by hand in the economic module (use SelectField or FilterSelect)', () => {
  assert.deepEqual(economics.filter((file) => /<select\b/.test(file.source)).map((file) => file.name), []);
});

test('no <button> is written by hand in the economic module beyond the documented exceptions', () => {
  const found = Object.fromEntries(
    economics
      .map((file) => [file.name, (file.source.match(/<button\b/g) ?? []).length])
      .filter(([, count]) => count > 0),
  );
  assert.deepEqual(found, BUTTON_EXCEPTIONS);
});

test('no card is drawn by hand in the economic pages and components (rounded border + bg-surface)', () => {
  const targets = [...economics, ...files.filter((file) => file.name === 'pages/ScenarioConfigurationPage.tsx')];
  const offenders = targets.flatMap((file) =>
    [...file.source.matchAll(/rounded-(?:lg|xl)[^"'`]*border[^"'`]*bg-surface|bg-surface[^"'`]*rounded-(?:lg|xl)[^"'`]*border/g)]
      .map((match) => `${file.name}: ${match[0]}`),
  );
  assert.deepEqual(offenders, []);
});

test('no native <dialog> anywhere: dialogs are the Modal', () => {
  assert.deepEqual(files.filter((file) => /<dialog\b|\.showModal\(/.test(file.source)).map((file) => file.name), []);
});

test('the simulation dialog is a Modal and keeps blocking every way out while it submits', () => {
  const source = files.find((file) => file.name === 'components/economics/SimulationDialog.tsx').source;
  assert.match(source, /<Modal\b/);
  assert.match(source, /onClose=\{\(\)=>\{if\(!busy\)onClose\(\);\}\}/);
});
