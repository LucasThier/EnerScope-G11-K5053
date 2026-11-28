import test from 'node:test';
import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createLoader } from './loadModule.mjs';

const load = createLoader();
const root = fileURLToPath(new URL('../src/', import.meta.url));
const classes = load(resolve(root, 'components/ui/tableClasses.ts'));

const tokens = (value) => value.split(/\s+/);

test('both header densities are the small uppercase label in ink-500, the floor for text', () => {
  for (const header of [classes.tableHeaderCell, classes.tableHeaderCellCompact]) {
    for (const required of ['text-left', 'text-xs', 'font-medium', 'uppercase', 'tracking-wide', 'text-ink-500']) {
      assert.ok(tokens(header).includes(required), `${required} missing in "${header}"`);
    }
  }
});

test('the two densities differ only in their padding', () => {
  assert.ok(tokens(classes.tableHeaderCell).includes('px-4'));
  assert.ok(tokens(classes.tableHeaderCell).includes('py-3'));
  assert.ok(tokens(classes.tableHeaderCellCompact).includes('px-3'));
  assert.ok(tokens(classes.tableHeaderCellCompact).includes('py-2'));
  assert.ok(tokens(classes.tableBodyCell).includes('px-4'));
  assert.ok(tokens(classes.tableBodyCellCompact).includes('px-3'));
  const strip = (value) => tokens(value).filter((token) => !/^p[xy]-/.test(token)).join(' ');
  assert.equal(strip(classes.tableHeaderCell), strip(classes.tableHeaderCellCompact));
  assert.equal(strip(classes.tableBodyCell), strip(classes.tableBodyCellCompact));
});

test('the body cell is 14px body text in ink-700 and leaves vertical alignment to the table', () => {
  for (const body of [classes.tableBodyCell, classes.tableBodyCellCompact]) {
    assert.ok(tokens(body).includes('text-sm'));
    assert.ok(tokens(body).includes('text-ink-700'));
    assert.ok(!tokens(body).some((token) => token.startsWith('align-')));
  }
});

function walk(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const full = path.join(directory, entry.name);
    if (entry.isDirectory()) return walk(full);
    return /\.tsx?$/.test(entry.name) ? [full] : [];
  });
}
const files = walk(root).map((file) => ({
  name: path.relative(root, file).split(path.sep).join('/'),
  source: readFileSync(file, 'utf8'),
}));

test('no table writes its own header or body cell classes (they come from tableClasses.ts)', () => {
  const own = files.filter((file) => file.name !== 'components/ui/tableClasses.ts');
  const offenders = own.flatMap((file) => [
    ...[...file.source.matchAll(/const (?:headerCell|bodyCell)\s*=\s*['"]/g)].map((m) => `${file.name}: ${m[0]}`),
    ...[...file.source.matchAll(/p[xy]-[34] py-[23] text-left text-xs font-medium uppercase/g)].map((m) => `${file.name}: ${m[0]}`),
  ]);
  assert.deepEqual(offenders, []);
  assert.ok(files.length > 100);
});
