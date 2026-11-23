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
  'react-router-dom': {
    Link: ({ to, className, children }) => ({ type: 'a', props: { href: to, className, children } }),
  },
};
const load = createLoader((name) => stubs[name]);
const root = fileURLToPath(new URL('../src/', import.meta.url));
const { PageHeader } = load(resolve(root, 'components/ui/PageHeader.tsx'));

function all(node, found = []) {
  if (Array.isArray(node)) {
    node.forEach((child) => all(child, found));
  } else if (node && typeof node === 'object') {
    found.push(node);
    all(node.props?.children, found);
  }
  return found;
}
const byType = (tree, type) => all(tree).filter((node) => node.type === type);
const text = (node) =>
  Array.isArray(node) ? node.map(text).join('') : node && typeof node === 'object' ? text(node.props?.children) : (node ?? '');

test('the title is the only h1 and carries the page-title classes', () => {
  const tree = PageHeader({ title: 'Proyectos' });
  const headings = byType(tree, 'h1');
  assert.equal(headings.length, 1);
  assert.equal(text(headings[0]), 'Proyectos');
  assert.match(headings[0].props.className, /\btext-2xl\b/);
  assert.match(headings[0].props.className, /\bfont-semibold\b/);
  assert.match(headings[0].props.className, /\btext-ink-800\b/);
  assert.equal(byType(tree, 'header').length, 1);
});

test('optional parts are absent when not given', () => {
  const tree = PageHeader({ title: 'Solo título' });
  assert.equal(byType(tree, 'a').length, 0);
  assert.equal(byType(tree, 'p').length, 0);
  assert.equal(all(tree).filter((node) => node.props?.className?.includes('gap-3')).length, 0);
});

test('the description sits under the title in the standard subtitle style', () => {
  const tree = PageHeader({ title: 'Usuarios', description: 'Todas las cuentas.' });
  const [description] = byType(tree, 'p');
  assert.equal(text(description), 'Todas las cuentas.');
  assert.equal(description.props.className, 'mt-1 text-sm text-ink-500');
});

test('the back link goes to its target in the standard link style, above the title', () => {
  const tree = PageHeader({ title: 'Versionado', back: { to: '/projects', label: '← Proyectos' } });
  const [link] = byType(tree, 'a');
  assert.equal(link.props.href, '/projects');
  assert.equal(text(link), '← Proyectos');
  assert.equal(link.props.className, 'text-sm font-semibold text-brand-800 hover:underline');
  const order = all(tree).filter((node) => node === link || node.type === 'h1');
  assert.equal(order[0], link);
  assert.match(order[1].props.className, /\bmt-2\b/);
});

test('the eyebrow sits between the back link and the title', () => {
  const tree = PageHeader({
    title: 'Comparar',
    eyebrow: 'Paso 1 de 2',
    back: { to: '/x', label: '← x' },
  });
  const [eyebrow] = byType(tree, 'p');
  assert.equal(text(eyebrow), 'Paso 1 de 2');
  assert.match(eyebrow.props.className, /\bmt-3\b/);
  assert.match(byType(tree, 'h1')[0].props.className, /\bmt-1\b/);
});

test('actions go in their own wrapper that stacks below the title under sm', () => {
  const tree = PageHeader({ title: 'Proyectos', actions: { type: 'button', props: { children: 'Nuevo' } } });
  const [header] = byType(tree, 'header');
  assert.match(header.props.className, /\bflex-col\b/);
  assert.match(header.props.className, /\bsm:flex-row\b/);
  assert.match(header.props.className, /\bsm:justify-between\b/);
  assert.equal(byType(tree, 'button').length, 1);
  const wrapper = all(tree).find((node) => node.props?.className?.includes('flex-wrap'));
  assert.ok(wrapper, 'actions wrapper missing');
  assert.equal(text(wrapper), 'Nuevo');
});

test('children render under the description, inside the title column', () => {
  const tree = PageHeader({
    title: 'Equipo',
    description: 'D',
    children: { type: 'p', props: { className: 'note', children: 'Tu rol' } },
  });
  const paragraphs = byType(tree, 'p').map(text);
  assert.deepEqual(paragraphs, ['D', 'Tu rol']);
});

test('it adds the bottom margin by default and drops it when the parent spaces its children', () => {
  assert.match(PageHeader({ title: 'A' }).props.className, /\bmb-6\b/);
  assert.doesNotMatch(PageHeader({ title: 'A', spaced: false }).props.className, /\bmb-6\b/);
});

test('a long title and a custom class are kept', () => {
  const long = 'Simulación de escenarios: Versión base con un nombre bastante largo para probar el corte';
  const tree = PageHeader({ title: long, className: 'max-w-3xl' });
  assert.equal(text(byType(tree, 'h1')[0]), long);
  assert.match(tree.props.className, /\bmax-w-3xl\b/);
  assert.match(byType(tree, 'div')[0].props.className, /\bmin-w-0\b/);
});

import { readdirSync, readFileSync } from 'node:fs';
import path from 'node:path';

test('every page title is a PageHeader: no screen writes its own h1 (sign-in is the one exception)', () => {
  const walk = (directory) =>
    readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
      const full = path.join(directory, entry.name);
      if (entry.isDirectory()) return walk(full);
      return entry.name.endsWith('.tsx') ? [full] : [];
    });
  const allowed = new Set(['components/ui/PageHeader.tsx', 'pages/LoginPage.tsx']);
  const offenders = walk(root)
    .map((file) => ({ name: path.relative(root, file).split(path.sep).join('/'), source: readFileSync(file, 'utf8') }))
    .filter((file) => !allowed.has(file.name) && /<h1\b/.test(file.source))
    .map((file) => file.name);
  assert.deepEqual(offenders, []);
});
