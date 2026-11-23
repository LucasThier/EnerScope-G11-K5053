import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

/*
 * The dark theme's numbers, read from the `html[data-theme='dark']` block of
 * index.css so a token edit that breaks a contract fails here and not in a
 * reviewer's eyes. Three contracts: text and links clear WCAG AA (4.5:1), the
 * focus ring and the control border clear the 3:1 of non-text UI, and the
 * layers separate by luminance (surface over page, raised over surface) enough
 * that a card still reads as a sheet without a shadow. The sidebar tokens are
 * guarded by sidebarTheme.test.mjs; the light theme by nothing here.
 */
const css = readFileSync(fileURLToPath(new URL('../src/index.css', import.meta.url)), 'utf8');

function block(selector) {
  const open = css.indexOf('{', css.indexOf(selector));
  let depth = 0;
  for (let i = open; i < css.length; i++) {
    if (css[i] === '{') depth++;
    if (css[i] === '}' && --depth === 0) return css.slice(open + 1, i);
  }
  throw new Error(`${selector} is not closed`);
}

function tokens(body) {
  const found = {};
  for (const [, name, value] of body.matchAll(/--color-([\w-]+):\s*(#[0-9a-fA-F]{6})\s*;/g)) {
    found[name] = value.toLowerCase();
  }
  return found;
}

const dark = tokens(block("html[data-theme='dark']"));

const channel = (c) => ((c /= 255) <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4);
const luminance = (hex) =>
  [1, 3, 5].map((i) => channel(parseInt(hex.slice(i, i + 2), 16))).reduce((sum, value, index) =>
    sum + value * [0.2126, 0.7152, 0.0722][index], 0);
function ratio(a, b) {
  const [hi, lo] = [luminance(dark[a] ?? a), luminance(dark[b] ?? b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}

const GROUNDS = ['surface', 'raised', 'ink-50'];

test('the dark block declares every token the contracts below read', () => {
  for (const name of ['page', 'surface', 'raised', 'ink-50', 'ink-500', 'ink-900', 'brand-800', 'brand-900',
    'on-accent', 'focus', 'control-border', 'fill-neutral', 'fill-neutral-strong', 'info-50', 'info-800']) {
    assert.ok(name in dark, `--color-${name} is not declared in the dark block`);
  }
});

for (const ground of GROUNDS) {
  for (const ink of ['ink-500', 'ink-600', 'ink-700', 'ink-800', 'ink-900']) {
    test(`text ${ink} on ${ground} clears 4.5:1`, () => {
      assert.ok(ratio(ink, ground) >= 4.5, `${ink} on ${ground} is ${ratio(ink, ground).toFixed(2)}:1`);
    });
  }
}

for (const ground of ['page', 'surface', 'raised']) {
  test(`a brand-800 link on ${ground} clears 4.5:1`, () => {
    assert.ok(ratio('brand-800', ground) >= 4.5, `${ratio('brand-800', ground).toFixed(2)}:1`);
  });
}

test('the primary button label (on-accent) clears 4.5:1 on brand-800 and on its hover brand-900', () => {
  assert.ok(ratio('on-accent', 'brand-800') >= 4.5, `${ratio('on-accent', 'brand-800').toFixed(2)}:1`);
  assert.ok(ratio('on-accent', 'brand-900') >= 4.5, `${ratio('on-accent', 'brand-900').toFixed(2)}:1`);
});

test('on-accent clears 4.5:1 on the neutral fills (secondary button, avatar, chart tooltips)', () => {
  assert.ok(ratio('on-accent', 'fill-neutral') >= 4.5, `${ratio('on-accent', 'fill-neutral').toFixed(2)}:1`);
  assert.ok(ratio('on-accent', 'fill-neutral-strong') >= 4.5, `${ratio('on-accent', 'fill-neutral-strong').toFixed(2)}:1`);
});

test('the status tints keep their text at 4.5:1 (alerts and chips)', () => {
  const pairs = [['brand-800', 'brand-50'], ['danger-700', 'danger-50'], ['warn-800', 'warn-50'], ['info-800', 'info-50']];
  for (const [text, fill] of pairs) {
    assert.ok(ratio(text, fill) >= 4.5, `${text} on ${fill} is ${ratio(text, fill).toFixed(2)}:1`);
  }
});

for (const ground of ['surface', 'raised']) {
  test(`the focus ring and the control border clear 3:1 on ${ground}`, () => {
    assert.ok(ratio('focus', ground) >= 3, `focus ${ratio('focus', ground).toFixed(2)}:1`);
    assert.ok(ratio('control-border', ground) >= 3, `control-border ${ratio('control-border', ground).toFixed(2)}:1`);
  });
}

test('the layers separate by luminance: surface over page at 1.2:1, raised over surface at 1.15:1', () => {
  assert.ok(ratio('surface', 'page') >= 1.2, `surface/page ${ratio('surface', 'page').toFixed(2)}:1`);
  assert.ok(ratio('raised', 'surface') >= 1.15, `raised/surface ${ratio('raised', 'surface').toFixed(2)}:1`);
});

test('the inner highlight is drawn only in the dark theme', () => {
  assert.match(css, /html\[data-theme='dark'\] \.shadow-raise,\s*html\[data-theme='dark'\] \.shadow-float\s*\{\s*--tw-inset-shadow:/);
  assert.equal(block('@theme {').includes('--tw-inset-shadow'), false);
});
