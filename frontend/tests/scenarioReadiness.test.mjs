import test from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createLoader } from './loadModule.mjs';

const nodeRequire = createRequire(import.meta.url);
const { createElement } = nodeRequire('react');
const { renderToStaticMarkup } = nodeRequire('react-dom/server');

const stubs = {
  'react-router-dom': { Link: ({ to, children, ...rest }) => createElement('a', { href: to, ...rest }, children) },
};
const load = createLoader((name) => stubs[name]);
const root = fileURLToPath(new URL('../src/', import.meta.url));
const { readinessView } = load(resolve(root, 'utils/readiness.ts'));
const errors = load(resolve(root, 'api/errors.ts'));
const { ScenarioTile } = load(resolve(root, 'components/comparison/ScenarioTile.tsx'));
const { ScenarioStatusChip } = load(resolve(root, 'components/scenarios/ScenarioStatusChip.tsx'));
const { scenarioStatusView } = load(resolve(root, 'utils/scenarioRules.ts'));

const INVALID_CONNECTION = 'The value chain graph has a connection the simulator cannot process';
const CARRIER = 'An LNG carrier has no seaport terminal or FLNG unit connected before it';
const UNPROCESSABLE = 'The simulator could not process the value chain graph; review its nodes and connections';
const NOT_CHECKED = 'The scenario could not be checked; review the server log';

const scenario = (over = {}) => ({
  projectId: 'p1', projectName: 'P', versionId: 'v1', versionName: 'Base', scenarioId: 's1', scenarioName: 'Escenario base',
  defaultScenario: true, status: 'NEVER_SIMULATED', latestEvaluationId: null, latestEvaluatedAt: null, mode: null,
  rounds: null, ready: false, readinessErrors: [INVALID_CONNECTION], canSimulate: true, job: null, ...over,
});
const tile = (over) => renderToStaticMarkup(createElement(ScenarioTile, {
  scenario: scenario(over), position: -1, disabled: false, starting: false, error: null, onToggle() {}, onSimulate() {},
}));

test('every new reason is shown in Spanish, both in the list and when the simulate call answers 400', () => {
  const expected = {
    [INVALID_CONNECTION]: 'El grafo tiene una conexión no válida: revisá las conexiones del diagrama.',
    [CARRIER]: 'Hay un buque metanero sin una terminal portuaria o una unidad FLNG conectada antes.',
    [UNPROCESSABLE]: 'El simulador no pudo procesar el grafo: revisá los nodos y las conexiones del diagrama.',
    [NOT_CHECKED]: 'No se pudo verificar este escenario. Avisá a quien administra el sistema.',
  };
  for (const [backend, spanish] of Object.entries(expected)) {
    assert.equal(errors.translateBackendMessage(backend), spanish);
    assert.equal(readinessView([backend]).reason, spanish);
  }
});

test('a diagram problem sends to the editor, an incomplete configuration to the scenario, and an unchecked one nowhere', () => {
  for (const reason of [INVALID_CONNECTION, CARRIER, UNPROCESSABLE, 'Simulation graph contains a cycle', 'Invalid or duplicate connection']) {
    assert.equal(readinessView([reason]).fix, 'diagram', reason);
  }
  assert.equal(readinessView(['Hay un borrador pendiente en Configuración del Escenario.']).fix, 'configuration');
  assert.deepEqual(readinessView([]), { reason: 'la configuración está incompleta.', fix: 'configuration' });
  assert.equal(readinessView([NOT_CHECKED]).fix, null);
});

test('the comparison tile of a broken graph shows the reason and links to the diagram, not to the configuration', () => {
  const html = tile();
  assert.match(html, /No se puede simular todavía: El grafo tiene una conexión no válida/);
  assert.match(html, /<a href="[^"]*v1[^"]*"[^>]*>Revisar el diagrama<\/a>/);
  assert.doesNotMatch(html, /Completar configuración|Simular ahora|The value chain graph/);
});

test('an outdated simulation on a broken graph shows the reason instead of the simulate button', () => {
  const html = tile({ status: 'OUTDATED', latestEvaluationId: 'e1', latestEvaluatedAt: '2026-11-20T10:00:00Z', readinessErrors: [CARRIER] });
  assert.match(html, /buque metanero/);
  assert.doesNotMatch(html, /Volver a simular/);
});

test('other tiles keep their behaviour: configuration link, no link when unchecked, the button when ready', () => {
  assert.match(tile({ readinessErrors: ['Hay un borrador pendiente en Configuración del Escenario.'] }), />Completar configuración<\/a>/);
  assert.doesNotMatch(tile({ readinessErrors: [NOT_CHECKED] }), /<a /);
  assert.match(tile({ ready: true, readinessErrors: [] }), /Simular ahora/);
});

test('the shared status chip is unaffected: no new status, and a broken scenario renders with the existing ones', () => {
  for (const status of ['OUTDATED', 'NEVER_SIMULATED']) {
    const view = scenarioStatusView(status);
    const html = renderToStaticMarkup(createElement(ScenarioStatusChip, { label: view.label, tone: view.tone }));
    assert.ok(html.includes(view.label));
  }
});
