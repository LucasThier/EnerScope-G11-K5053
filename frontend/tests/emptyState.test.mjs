import test from 'node:test';
import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createLoader } from './loadModule.mjs';

const stubs = {
  'react/jsx-runtime': {
    jsx: (type, props) => (typeof type === 'function' ? type(props) : { type, props }),
    jsxs: (type, props) => (typeof type === 'function' ? type(props) : { type, props }),
  },
};
const load = createLoader((name) => stubs[name]);
const root = fileURLToPath(new URL('../src/', import.meta.url));
const { EmptyState } = load(resolve(root, 'components/ui/EmptyState.tsx'));
const { LoadingState } = load(resolve(root, 'components/ui/LoadingState.tsx'));

const flat = (node) => (Array.isArray(node) ? node.flatMap(flat) : [node]);
const tokens = (node) => node.props.className.split(/\s+/);

test('an empty state is the centred, quiet 14px block of the system', () => {
  const tree = EmptyState({ children: 'Todavía no hay proyectos.' });
  assert.equal(tree.type, 'div');
  for (const required of ['px-4', 'py-8', 'text-center', 'text-sm', 'text-ink-500']) {
    assert.ok(tokens(tree).includes(required), `${required} missing`);
  }
  assert.deepEqual(flat(tree.props.children).filter(Boolean), ['Todavía no hay proyectos.']);
});

test('an empty state puts its action underneath, 16px below the message, and only when given', () => {
  const button = { type: 'button', props: { children: 'Nuevo' } };
  const withAction = EmptyState({ children: 'Vacío', action: button });
  const wrapper = flat(withAction.props.children).find((node) => node && node.type === 'div');
  assert.ok(wrapper);
  assert.ok(tokens(wrapper).includes('mt-4'));
  assert.equal(wrapper.props.children, button);
  const without = EmptyState({ children: 'Vacío', action: false });
  assert.equal(flat(without.props.children).filter((node) => node && node.type === 'div').length, 0);
});

test('a loading state is announced as a status and looks like the empty state', () => {
  const tree = LoadingState({ children: 'Cargando proyectos…' });
  assert.equal(tree.type, 'p');
  assert.equal(tree.props.role, 'status');
  for (const required of ['px-4', 'py-8', 'text-center', 'text-sm', 'text-ink-500']) {
    assert.ok(tokens(tree).includes(required), `${required} missing`);
  }
  assert.equal(tree.props.children, 'Cargando proyectos…');
});

test('a custom class is appended to both', () => {
  assert.ok(tokens(EmptyState({ children: 'x', className: 'max-w-sm' })).includes('max-w-sm'));
  assert.ok(tokens(LoadingState({ children: 'x', className: 'max-w-sm' })).includes('max-w-sm'));
});

function walk(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const full = path.join(directory, entry.name);
    if (entry.isDirectory()) return walk(full);
    return entry.name.endsWith('.tsx') ? [full] : [];
  });
}

test('no screen hand-rolls the empty or loading block (px-4 py-8 text-center)', () => {
  const allowed = new Set(['components/ui/EmptyState.tsx', 'components/ui/LoadingState.tsx']);
  const offenders = walk(root)
    .map((file) => ({ name: path.relative(root, file).split(path.sep).join('/'), source: readFileSync(file, 'utf8') }))
    .filter((file) => !allowed.has(file.name) && /px-4 py-8 text-center/.test(file.source))
    .map((file) => file.name);
  assert.deepEqual(offenders, []);
});
