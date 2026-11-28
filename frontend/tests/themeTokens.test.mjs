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

/**
 * file -> reason it may keep a literal white fill, stroke or text. White is only right on a surface
 * that is the same in both themes; anywhere else it stays white on the dark page (`fill-white` was
 * the timeline dot of the economic summary) and must be `fill-surface` / `text-on-accent`.
 */
const WHITE_INK_EXCEPTIONS = {
  'components/dashboard/RecentProjectHero.tsx':
    'text-white on the spotlight plate, a petrol surface that is the same in both themes (10.61:1 on it, 6.28:1 on its lighter end)',
};

const WHITE_SURFACE = /\bbg-white\b/g;
const WHITE_INK = /\b(?:fill|stroke|text)-white\b/g;
const RAW_STATUS_PALETTE = /\b(?:bg|text|border|ring)-(?:red|amber)-\d{2,3}\b/g;

function whiteInkOffenders() {
  return files
    .filter((file) => !(file.name in WHITE_INK_EXCEPTIONS))
    .flatMap((file) => [...file.source.matchAll(WHITE_INK)].map((match) => `${file.name}: ${match[0]}`));
}

test('the scan reaches the app and the patterns match what they should', () => {
  assert.ok(files.length > 100, `only ${files.length} .tsx files found under src/`);
  assert.ok(files.some((file) => file.name === 'components/economics/EconomicEditor.tsx'));
  assert.equal('<p className="rounded-lg bg-white p-4">'.match(WHITE_SURFACE)?.length, 1);
  assert.equal('<p className="bg-surface text-white">'.match(WHITE_SURFACE), null);
  // Built from parts so Tailwind's source scan does not emit these utilities for the sake of a test string.
  const white = ['fill', 'stroke', 'text'].map((property) => `${property}-white`).join(' ');
  assert.equal(`<circle className="${white}" />`.match(WHITE_INK)?.length, 3);
  assert.equal('<circle className="fill-surface text-on-accent" />'.match(WHITE_INK), null);
  assert.equal('text-red-700 bg-amber-50 ring-red-200'.match(RAW_STATUS_PALETTE)?.length, 3);
  assert.equal('text-danger-700 bg-warn-50 text-redacted'.match(RAW_STATUS_PALETTE), null);
});

test('no component paints a surface with the literal bg-white (use bg-surface)', () => {
  assert.deepEqual(offenders(WHITE_SURFACE), []);
});

test('no component uses red-* or amber-* (use the danger-* and warn-* tokens)', () => {
  assert.deepEqual(offenders(RAW_STATUS_PALETTE), []);
});

test('no component draws a fill, stroke or text in literal white outside the documented exceptions', () => {
  assert.deepEqual(whiteInkOffenders(), []);
});

test('every white-ink exception still exists and still needs it', () => {
  for (const name of Object.keys(WHITE_INK_EXCEPTIONS)) {
    const file = files.find((candidate) => candidate.name === name);
    assert.ok(file, `${name} is listed as an exception but does not exist`);
    assert.ok(WHITE_INK.test(file.source), `${name} no longer uses a literal white and should leave the list`);
    WHITE_INK.lastIndex = 0;
  }
});
