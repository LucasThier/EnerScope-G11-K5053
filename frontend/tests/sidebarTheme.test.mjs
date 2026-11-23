import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { inflateSync } from 'node:zlib';
import { fileURLToPath } from 'node:url';

/*
 * The green sidebar is the one green surface in the app (index.css → the
 * exception under "WHERE each colour goes"). These tests hold its sidebar-*
 * tokens to WCAG AA in both themes: 4.5:1 for text, 3:1 for the focus ring and
 * for icons, 1.8:1 for the current-row pill against the rail, and 3:1 for every
 * opaque colour of the logo cut drawn on it.
 *
 * Until 2026-11-23 the two themes shared one set of values; the dark theme now
 * has its own, deeper rail (#0e2f26) and a mid-green pill, so the same ratios
 * are asserted on each theme's own tokens and the light values are frozen.
 */
const src = new URL('../src/', import.meta.url);
const css = readFileSync(new URL('index.css', src), 'utf8');

function block(selector) {
  const start = css.indexOf(selector);
  assert.notEqual(start, -1, `${selector} not found in index.css`);
  const open = css.indexOf('{', start);
  let depth = 0;
  for (let i = open; i < css.length; i++) {
    if (css[i] === '{') depth++;
    if (css[i] === '}' && --depth === 0) return css.slice(open + 1, i);
  }
  throw new Error(`${selector} is not closed`);
}

function sidebarTokens(body) {
  const tokens = {};
  for (const [, name, value] of body.matchAll(/--color-(sidebar[\w-]*):\s*(#[0-9a-fA-F]{6})\s*;/g)) {
    tokens[name] = value.toLowerCase();
  }
  return tokens;
}

const channel = (c) => ((c /= 255) <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4);
const luminance = ([r, g, b]) => 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
const rgb = (hex) => [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16));
function ratio(a, b) {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}

const NAMES = [
  'sidebar',
  'sidebar-hover',
  'sidebar-text',
  'sidebar-muted',
  'sidebar-icon-muted',
  'sidebar-line',
  'sidebar-active',
  'sidebar-active-text',
  'sidebar-focus',
];

/** [foreground, background, minimum, what it is] */
const PAIRS = [
  ['sidebar-text', 'sidebar', 4.5, 'resting and locked row labels'],
  ['sidebar-text', 'sidebar-hover', 4.5, 'a row label under the pointer'],
  ['sidebar-text', 'sidebar-hover', 4.5, 'the module holding the current row (label and icon)'],
  ['sidebar-muted', 'sidebar', 4.5, 'section titles'],
  ['sidebar-active-text', 'sidebar-active', 4.5, 'the current row label and icon'],
  ['sidebar-icon-muted', 'sidebar', 3, 'a locked row icon and padlock'],
  ['sidebar-focus', 'sidebar', 3, 'the focus ring on the rail'],
  ['sidebar-focus', 'sidebar-hover', 3, 'the focus ring on a hovered row'],
  ['sidebar-active', 'sidebar', 1.8, 'the current row pill against the rail'],
];

/** The light theme's values, frozen: the dark theme's sidebar must never move them. */
const LIGHT = {
  sidebar: '#114636',
  'sidebar-hover': '#245546',
  'sidebar-text': '#ffffff',
  'sidebar-muted': '#dbe3e1',
  'sidebar-icon-muted': '#a0b5af',
  'sidebar-line': '#356254',
  'sidebar-active': '#d6f5c3',
  'sidebar-active-text': '#1b5e20',
  'sidebar-focus': '#ffffff',
};

const themes = {
  light: sidebarTokens(block('@theme')),
  dark: sidebarTokens(block("html[data-theme='dark']")),
};

test('every sidebar token is declared in both themes', () => {
  for (const [theme, tokens] of Object.entries(themes)) {
    for (const name of NAMES) assert.ok(tokens[name], `--color-${name} missing in ${theme}`);
  }
});

test('the light theme keeps exactly its sidebar values', () => {
  assert.deepEqual(themes.light, LIGHT);
});

test('the dark theme has its own deeper rail and a quiet mid-green pill, 1.3-1.5:1 above the page', () => {
  const dark = themes.dark;
  assert.notEqual(dark.sidebar, themes.light.sidebar);
  assert.notEqual(dark['sidebar-active'], themes.light['sidebar-active']);
  const page = css.match(/html\[data-theme='dark'\][^]*?--color-page:\s*(#[0-9a-fA-F]{6})/)[1];
  const measured = ratio(rgb(dark.sidebar), rgb(page));
  assert.ok(measured >= 1.3 && measured <= 1.5, `sidebar ${dark.sidebar} on page ${page} is ${measured.toFixed(2)}:1`);
});

for (const [theme, tokens] of Object.entries(themes)) {
  for (const [fg, bg, min, what] of PAIRS) {
    test(`${theme}: ${what} (${fg} on ${bg}) clears ${min}:1`, () => {
      const measured = ratio(rgb(tokens[fg]), rgb(tokens[bg]));
      assert.ok(measured >= min, `${fg} ${tokens[fg]} on ${bg} ${tokens[bg]} is ${measured.toFixed(2)}:1`);
    });
  }
}

/** Minimal decoder for the 8-bit RGBA, non-interlaced PNGs in src/assets. */
function decodePng(path) {
  const bytes = readFileSync(path);
  let offset = 8;
  let width = 0;
  let height = 0;
  const idat = [];
  while (offset < bytes.length) {
    const length = bytes.readUInt32BE(offset);
    const type = bytes.toString('ascii', offset + 4, offset + 8);
    const data = bytes.subarray(offset + 8, offset + 8 + length);
    if (type === 'IHDR') {
      width = data.readUInt32BE(0);
      height = data.readUInt32BE(4);
      assert.deepEqual([data[8], data[9], data[12]], [8, 6, 0], 'expected RGBA8, non-interlaced');
    }
    if (type === 'IDAT') idat.push(data);
    offset += 12 + length;
  }
  const raw = inflateSync(Buffer.concat(idat));
  const stride = width * 4;
  const out = Buffer.alloc(stride * height);
  for (let y = 0; y < height; y++) {
    const filter = raw[y * (stride + 1)];
    for (let x = 0; x < stride; x++) {
      const value = raw[y * (stride + 1) + 1 + x];
      const left = x >= 4 ? out[y * stride + x - 4] : 0;
      const up = y > 0 ? out[(y - 1) * stride + x] : 0;
      const corner = y > 0 && x >= 4 ? out[(y - 1) * stride + x - 4] : 0;
      let predictor = 0;
      if (filter === 1) predictor = left;
      else if (filter === 2) predictor = up;
      else if (filter === 3) predictor = (left + up) >> 1;
      else if (filter === 4) {
        const p = left + up - corner;
        const [pa, pb, pc] = [Math.abs(p - left), Math.abs(p - up), Math.abs(p - corner)];
        predictor = pa <= pb && pa <= pc ? left : pb <= pc ? up : corner;
      }
      out[y * stride + x] = (value + predictor) & 255;
    }
  }
  return out;
}

function weakestOpaqueRatio(asset, background) {
  const pixels = decodePng(fileURLToPath(new URL(`assets/${asset}`, src)));
  let weakest = Infinity;
  let below = 0;
  for (let i = 0; i < pixels.length; i += 4) {
    if (pixels[i + 3] < 250) continue; // anti-aliased edges blend into the rail
    const measured = ratio([pixels[i], pixels[i + 1], pixels[i + 2]], background);
    weakest = Math.min(weakest, measured);
    if (measured < 3) below++;
  }
  return { weakest, below };
}

for (const [theme, tokens] of Object.entries(themes)) {
  for (const asset of ['logo-mark-sidebar.png', 'logo-full-sidebar.png']) {
    test(`${theme}: ${asset}: every opaque colour clears 3:1 on the sidebar`, () => {
      const { weakest, below } = weakestOpaqueRatio(asset, rgb(tokens.sidebar));
      assert.equal(below, 0, `${below} opaque pixels under 3:1 (weakest ${weakest.toFixed(2)}:1)`);
    });
  }
}

test('the plain white cut is why the sidebar cut exists: its greens fail on the rail', () => {
  const { below } = weakestOpaqueRatio('logo-mark-inverse.png', rgb(themes.light.sidebar));
  assert.ok(below > 0);
});
