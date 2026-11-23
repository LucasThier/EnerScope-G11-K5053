import test from 'node:test';
import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

/*
 * Colour comes from tokens, not from Tailwind's raw palette. A literal
 * `bg-white` stays white in the dark theme, so the box it paints sits on the
 * dark page with ink text that turned light, and `red-*` / `amber-*` do not
 * restate themselves per theme the way `danger-*` and `warn-*` do. This is a
 * static scan of src/**\/*.tsx; it is the cheap guard for the 2026-11-23
 * clean-up of the economic module (docs/considerations.md).
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

/** file -> reason it may keep the literal. Empty today: add an entry only with a justification. */
const EXCEPTIONS = {};

function offenders(pattern) {
  return files
    .filter((file) => !(file.name in EXCEPTIONS))
    .flatMap((file) => [...file.source.matchAll(pattern)].map((match) => `${file.name}: ${match[0]}`));
}

const WHITE_SURFACE = /\bbg-white\b/g;
const RAW_STATUS_PALETTE = /\b(?:bg|text|border|ring)-(?:red|amber)-\d{2,3}\b/g;

test('the scan reaches the app and the patterns match what they should', () => {
  assert.ok(files.length > 100, `only ${files.length} .tsx files found under src/`);
  assert.ok(files.some((file) => file.name === 'components/economics/EconomicEditor.tsx'));
  assert.equal('<p className="rounded-lg bg-white p-4">'.match(WHITE_SURFACE)?.length, 1);
  assert.equal('<p className="bg-surface text-white">'.match(WHITE_SURFACE), null);
  assert.equal('text-red-700 bg-amber-50 ring-red-200'.match(RAW_STATUS_PALETTE)?.length, 3);
  assert.equal('text-danger-700 bg-warn-50 text-redacted'.match(RAW_STATUS_PALETTE), null);
});

test('no component paints a surface with the literal bg-white (use bg-surface)', () => {
  assert.deepEqual(offenders(WHITE_SURFACE), []);
});

test('no component uses red-* or amber-* (use the danger-* and warn-* tokens)', () => {
  assert.deepEqual(offenders(RAW_STATUS_PALETTE), []);
});
