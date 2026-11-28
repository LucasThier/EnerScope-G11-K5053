import test from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createLoader } from './loadModule.mjs';

const nodeRequire = createRequire(import.meta.url);
const { createElement } = nodeRequire('react');
const { renderToStaticMarkup } = nodeRequire('react-dom/server');

// The flow (file reading, request) is replaced by a fixed state: these tests look only at what the window renders.
let flow;
const idle = {
  stage: 'pick', fileName: null, readError: null, analysis: null, blockers: [], warnings: [], confirmed: false,
  failure: null, importedCount: null, canSubmit: false,
  setConfirmed() {}, selectFile() {}, submit() {}, cancel() {}, downloadTemplate() {},
};
const stubs = {
  'react-router-dom': { useNavigate: () => () => {} },
  '../../hooks/useVersionCsvImport': { useVersionCsvImport: () => flow },
};
const load = createLoader((name) => stubs[name] ?? (name === '../api/client' || name === './client' ? { client: {}, default: {} } : undefined));
const root = fileURLToPath(new URL('../src/', import.meta.url));
const { ImportVersionCsvModal } = load(resolve(root, 'components/versions/ImportVersionCsvModal.tsx'));

const access = (over = {}) => ({ canEdit: true, canEditConnections: true, canEditEconomicConfiguration: true, ...over });

function render({ state = {}, as = access() } = {}) {
  flow = { ...idle, ...state };
  return renderToStaticMarkup(
    createElement(ImportVersionCsvModal, {
      projectId: 'p', version: { id: 'v', name: 'V1' }, access: as, onImported() {}, onClose() {},
    }),
  );
}

/** The opening tag of the button whose content includes `text`. */
function buttonTag(html, text) {
  const found = [...html.matchAll(/(<button[^>]*>)(.*?)<\/button>/gs)].find((m) => m[2].includes(text));
  assert.ok(found, `there is a button with "${text}"`);
  return found[1];
}
const isDisabled = (tag) => /\sdisabled=""/.test(tag);

test('the file picker speaks Spanish and leaves no browser-written text on screen', () => {
  const html = render();
  assert.match(html, /Elegir archivo/);
  assert.match(html, /Ningún archivo seleccionado/);
  assert.doesNotMatch(html, /Choose File|No file chosen/i);
  const input = html.match(/<input[^>]*type="file"[^>]*>/)[0];
  assert.match(input, /class="peer sr-only"/, 'the native control is hidden from sight only');
  assert.doesNotMatch(input, /tabindex="-1"|display:\s*none|\shidden/, 'and stays reachable by keyboard and screen reader');
});

test('the hidden file input keeps its label, its status text and a visible focus ring', () => {
  const html = render();
  const input = html.match(/<input[^>]*type="file"[^>]*>/)[0];
  const id = input.match(/\sid="([^"]+)"/)[1];
  assert.ok(html.includes(`<label for="${id}" class="text-sm font-medium text-ink-600">Archivo CSV</label>`));
  const describedBy = input.match(/aria-describedby="([^"]+)"/)[1].split(' ')[0];
  assert.match(html, new RegExp(`<span id="${describedBy}"[^>]*>Ningún archivo seleccionado</span>`));
  assert.match(html, /peer-focus-visible:ring-2/);
});

test('a chosen file shows its name in the picker', () => {
  const html = render({ state: { fileName: 'mi-planta.csv' } });
  assert.match(html, />mi-planta\.csv<\/span>/);
  assert.doesNotMatch(html, /Ningún archivo seleccionado/);
});

test('the window asks for a single file and offers one main template plus two other examples', () => {
  const html = render();
  assert.match(html, /Subí un solo archivo/);
  assert.equal(html.match(/type="file"/g).length, 1);
  assert.doesNotMatch(html, /multiple/);
  const order = ['Descargar plantilla (nodos y conexiones)', 'Otros ejemplos:', 'solo nodos', 'con economía</button>'];
  const positions = order.map((text) => html.indexOf(text));
  assert.ok(positions.every((at, i) => at >= 0 && (i === 0 || at > positions[i - 1])), String(positions));
});

test('the column help is a closed disclosure that keeps both texts unchanged', () => {
  const { NODE_TYPE_HELP, IDENTITY_HELP } = load(resolve(root, 'utils/versionCsvImport.ts'));
  const html = render().replaceAll('&quot;', '"');
  const details = html.match(/<details([^>]*)>(.*?)<\/details>/s);
  assert.doesNotMatch(details[1], /open/);
  assert.match(details[2], /<summary[^>]*>Ayuda sobre las columnas type e identity<\/summary>/);
  assert.ok(details[2].includes(NODE_TYPE_HELP));
  assert.ok(details[2].includes(IDENTITY_HELP));
});

test('with economic permission the economics example can be downloaded', () => {
  const html = render();
  assert.equal(isDisabled(buttonTag(html, 'con economía')), false);
  assert.doesNotMatch(html, /necesita permiso para editar la economía/);
});

test('without economic permission only the economics example is disabled, and the window says why', () => {
  for (const as of [access({ canEditEconomicConfiguration: false }), null]) {
    const html = render({ as });
    const tag = buttonTag(html, 'con economía');
    assert.equal(isDisabled(tag), true);
    const hintId = tag.match(/aria-describedby="([^"]+)"/)[1];
    assert.ok(html.includes(`id="${hintId}"`));
    assert.match(html, /necesita permiso para editar la economía/);
    assert.equal(isDisabled(buttonTag(html, 'Descargar plantilla (nodos y conexiones)')), false);
    assert.equal(isDisabled(buttonTag(html, 'solo nodos')), false);
  }
});

test('the confirm button carries what the chosen file brings', () => {
  assert.match(render(), />Importar<\/button>/);
  const analysis = {
    problems: [], hiddenProblemCount: 0, nodeCount: 2, connectionCount: 1, scenarioCount: 0,
    hasEconomicProfiles: false, identityCount: 0, verticals: [],
  };
  assert.match(render({ state: { stage: 'review', fileName: 'a.csv', analysis } }), />Importar 2 nodos y 1 conexión<\/button>/);
});

test('a file ready to confirm hides the examples and the help; a blocked one keeps them', () => {
  const analysis = {
    problems: [], hiddenProblemCount: 0, nodeCount: 2, connectionCount: 1, scenarioCount: 1,
    hasEconomicProfiles: true, identityCount: 0, verticals: [],
  };
  const state = { stage: 'review', fileName: 'a.csv', analysis };
  const ready = render({ state });
  assert.doesNotMatch(ready, /Descargar plantilla|Otros ejemplos|<details/);
  assert.match(ready, /Elegir archivo/, 'another file can still be chosen');
  const blocked = render({ state: { ...state, blockers: ['Sin permiso'] } });
  assert.match(blocked, /Descargar plantilla/);
  assert.match(blocked, /<details/);
});
