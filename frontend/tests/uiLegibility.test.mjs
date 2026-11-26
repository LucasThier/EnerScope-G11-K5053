import test from 'node:test';
import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/*
 * Static guards for what a reader can and cannot read or hit:
 *   - ink-300 and ink-400 are never a text colour (3.44:1 or less on white),
 *   - the tabs have a 44px touch target and the row buttons a 44px-tall one,
 *   - sections are 24px apart (space-y-6), never space-y-5 or space-y-10,
 *   - a section heading is text-lg, never text-xl,
 *   - nothing sets a colour on a <Button> that its variant already sets,
 *   - arbitrary pixel font sizes live only where the type scale documents them.
 * The ratios and the scale are in index.css and in frontend/README.md.
 */
const root = fileURLToPath(new URL('../src/', import.meta.url));

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
const source = (name) => files.find((file) => file.name === name).source;
const countByFile = (pattern, only = () => true) =>
  Object.fromEntries(
    files
      .filter((file) => only(file))
      .map((file) => [file.name, (file.source.match(pattern) ?? []).length])
      .filter(([, count]) => count > 0),
  );

test('the scan reaches the app', () => {
  assert.ok(files.length > 150, `${files.length} files`);
});

/**
 * `text-ink-300` / `text-ink-400` where the colour is not a reading colour. A
 * prefixed one (`placeholder:`, `disabled:`) is allowed by the contract in
 * index.css and is not matched. The count per file is exact.
 */
const INK_TEXT_EXCEPTIONS = {
  'components/ui/SearchField.tsx': 1, // the magnifier icon, drawn with currentColor
  'components/dashboard/RecentProjectsList.tsx': 1, // a chevron icon
  'components/layout/TopBar.tsx': 1, // an aria-hidden separator glyph
};

test('ink-300 and ink-400 are not a text colour, apart from the documented decorative uses', () => {
  const found = countByFile(/(?<![\w:-])text-ink-(?:300|400)\b/g);
  assert.deepEqual(found, INK_TEXT_EXCEPTIONS);
});

test('the tabs are a 44px touch target without being wider or looking different', () => {
  const tabs = source('components/ui/Tabs.tsx');
  const classes = tabs.match(/'-mb-px border-b-2[\s\S]*?'relative after:[^']*'/);
  assert.ok(classes, 'the tab button classes were not found');
  const tokens = classes[0].replace(/\/\/[^\n]*/g, '').match(/[\w:\-\[\]]+/g);
  const px = (token) => Number(token.match(/(\d+)$/)[1]) * 4;
  const padding = px(tokens.find((token) => /^py-\d+$/.test(token)));
  const extension = px(tokens.find((token) => /^after:-inset-y-\d+$/.test(token)));
  const textSmLineHeight = 20; // text-sm: 1.25rem
  assert.ok(tokens.includes('text-sm') && tokens.includes('relative') && tokens.includes('after:absolute'));
  assert.ok(tokens.includes('after:inset-x-0'), 'the area must not widen past the tab');
  assert.equal(padding * 2 + textSmLineHeight + extension * 2, 44);
});

test('no <Button> carries a colour, border or radius its variant already sets (only `!` overrides)', () => {
  const offenders = files
    .filter((file) => file.name.endsWith('.tsx'))
    .flatMap((file) =>
      [...file.source.matchAll(/<Button\b((?:=>|[^>])*?)>/g)].flatMap((tag) => {
        const className = tag[1].match(/className="([^"]*)"/);
        if (!className) return [];
        return className[1]
          .split(/\s+/)
          .filter((token) => token && !token.endsWith('!'))
          .filter((token) => /^(?:hover:|focus|bg-|text-(?:ink|brand|danger|warn|on|white)|border|ring|rounded)/.test(token))
          .map((token) => `${file.name}: ${token}`);
      }),
    );
  assert.deepEqual(offenders, []);
});

/**
 * Arbitrary pixel sizes, with the step of the type scale each one is. The
 * count per file is exact. 11px is the "micro" step (badges, small captions,
 * the sidebar's section titles); the two 10px ones are in the value-chain
 * editor and sit below it.
 */
const PIXEL_SIZES = {
  'components/layout/Sidebar.tsx': 1, // micro: section title of the rail
  'components/projects/ProjectsTable.tsx': 1, // micro: "Activo" badge
  'pages/VersionsIndexPage.tsx': 1, // micro: badge
  'components/editor/NodePalette.tsx': 1, // micro: palette caption
  'components/editor/EnerNode.tsx': 1, // 10px, editor (below micro)
  'pages/EditorPage.tsx': 1, // 10px, editor (below micro)
};

test('arbitrary pixel font sizes appear only where the scale documents them', () => {
  assert.deepEqual(countByFile(/\btext-\[\d+(?:\.\d+)?px\]/g, (file) => file.name.endsWith('.tsx')), PIXEL_SIZES);
});

test('a row button is a 24px button with a 24x44 touch area that cannot meet its neighbours', () => {
  const classes = source('components/ui/RowButton.tsx').match(/'inline-flex rounded-control p-1[\s\S]*?'relative after:[^']*'/);
  assert.ok(classes, 'the RowButton classes were not found');
  const tokens = classes[0].replace(/\/\/[^\n]*/g, '').match(/[\w:\-.\[\]]+/g);
  const px = (token) => Number(token.match(/(-?[\d.]+)$/)[1].replace('-', '')) * 4;
  const icon = 16; // every RowButton child is an h-4 w-4 icon
  const button = px(tokens.find((token) => token === 'p-1')) * 2 + icon;
  const extension = px(tokens.find((token) => /^after:-inset-y-[\d.]+$/.test(token)));
  assert.ok(tokens.includes('relative') && tokens.includes('after:absolute'));
  assert.ok(tokens.includes('after:inset-x-0'), 'the area must not widen past the button');
  assert.equal(button, 24);
  assert.equal(button + extension * 2, 44);
  // Vertically it stays inside its own row: a standard table cell pads 12px (py-3) around a
  // 24px button, a project row is h-14 (56px) and a version-tree row is py-3 as well; the
  // compact members table only has a RowButton next to a 40px role select, so its rows are 56px.
  const standardCellPadding = px('py-3');
  assert.ok(extension <= standardCellPadding, `${extension}px of extension vs ${standardCellPadding}px of cell padding`);
  assert.match(source('components/ui/tableClasses.ts'), /tableBodyCell = 'px-4 py-3/);
  assert.match(source('components/projects/ProjectsTable.tsx'), /h-14/);
  assert.match(source('components/versions/VersionTree.tsx'), /flex items-center gap-3 px-4 py-3/);
});

test('sections are 24px apart: no space-y-5 and no space-y-10 anywhere', () => {
  assert.deepEqual(countByFile(/\bspace-y-(?:5|10)\b/g, (file) => file.name.endsWith('.tsx')), {});
});

test('a section heading (h2) is text-lg, never text-xl', () => {
  assert.deepEqual(countByFile(/<h2\b[^>]*\btext-xl\b/g, (file) => file.name.endsWith('.tsx')), {});
});
