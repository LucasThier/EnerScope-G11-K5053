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
    Link: ({ to, className, children, ...rest }) => ({ type: 'a', props: { href: to, className, children, ...rest } }),
  },
};
const load = createLoader((name) => stubs[name]);
const root = fileURLToPath(new URL('../src/', import.meta.url));
const { ButtonLink } = load(resolve(root, 'components/ui/ButtonLink.tsx'));
const { Button } = load(resolve(root, 'components/ui/Button.tsx'));
const { buttonClasses } = load(resolve(root, 'components/ui/buttonClasses.ts'));

const tokens = (value) => value.split(/\s+/).filter(Boolean);

test('a button link is an anchor to its target with the label inside', () => {
  const tree = ButtonLink({ to: '/projects/1/comparisons', children: 'Modificar la selección' });
  assert.equal(tree.type, 'a');
  assert.equal(tree.props.href, '/projects/1/comparisons');
  assert.equal(tree.props.children, 'Modificar la selección');
});

test('it wears exactly the classes a Button of the same variant and size wears', () => {
  for (const variant of ['primary', 'secondary', 'danger', 'ghost', 'link', 'on-dark']) {
    for (const size of ['md', 'compact']) {
      const link = ButtonLink({ to: '/x', variant, size, children: 'x' });
      const button = Button({ variant, size, children: 'x' });
      assert.equal(link.props.className.trim(), button.props.className.trim(), `${variant}/${size}`);
    }
  }
});

test('the default is the primary button at control height', () => {
  const { className } = ButtonLink({ to: '/x', children: 'x' }).props;
  assert.equal(className.trim(), buttonClasses('primary', 'md'));
  assert.ok(tokens(className).includes('h-control'));
  assert.ok(tokens(className).includes('bg-brand-800'));
});

test('compact is the 40px control and a link variant has no box', () => {
  assert.ok(tokens(ButtonLink({ to: '/x', size: 'compact', children: 'x' }).props.className).includes('h-control-compact'));
  const bare = tokens(ButtonLink({ to: '/x', variant: 'link', size: 'compact', children: 'x' }).props.className);
  assert.ok(!bare.includes('h-control') && !bare.includes('h-control-compact'));
});

test('every variant keeps the standard focus ring and the secondary one is the neutral fill', () => {
  for (const variant of ['primary', 'secondary', 'danger', 'ghost', 'link', 'on-dark']) {
    assert.match(buttonClasses(variant), /focus-visible:ring-/, variant);
  }
  assert.ok(tokens(buttonClasses('secondary')).includes('bg-fill-neutral'));
});

test('a custom class and the router props go through', () => {
  const tree = ButtonLink({ to: '/x', className: 'w-full', replace: true, children: 'x' });
  assert.ok(tokens(tree.props.className).includes('w-full'));
  assert.equal(tree.props.replace, true);
});
