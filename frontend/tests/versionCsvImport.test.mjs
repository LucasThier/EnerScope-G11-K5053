import test from 'node:test';
import assert from 'node:assert/strict';
import { resolve } from 'node:path';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { createLoader } from './loadModule.mjs';

const load = createLoader();
const root = fileURLToPath(new URL('../src/', import.meta.url));
const csv = load(resolve(root, 'utils/versionCsvImport.ts'));
const errors = load(resolve(root, 'api/errors.ts'));

const UUID = '6f1c2a9e-0b7d-4c1e-9a52-3d8f7e4b1a10';
const OTHER_UUID = '0a8e5c1d-3f42-4b6a-8d17-9c2e4f6a8b01';

// ---- error mapping -------------------------------------------------------------------------------------------------

test('an identity that already exists in the version is translated with its line and uuid', () => {
  const message = `Invalid CSV record 7 (NODE): identity ${UUID} already exists in this version`;
  const expected =
    `Línea 7: la identidad ${UUID} ya existe en esta versión. Este importador solo agrega nodos nuevos: ` +
    'quitá esa fila o dejá la columna identity vacía.';
  assert.equal(errors.translateCsvImportMessage(message), expected);
  assert.equal(errors.csvImportErrorLine(message), 7);
  assert.deepEqual(errors.describeCsvImportError(message), { line: 7, text: expected, detail: null });
  assert.equal(errors.translateBackendMessage(message), expected, 'the shared translator knows it too');
});

test('an identity repeated in the file is translated with its line and uuid', () => {
  const message = `Invalid CSV record 3 (NODE): identity ${UUID} is repeated in the file`;
  const expected = `Línea 3: la identidad ${UUID} aparece repetida en el archivo. Cada nodo debe tener una identidad única.`;
  assert.equal(errors.translateCsvImportMessage(message), expected);
  assert.equal(errors.describeCsvImportError(message).line, 3);
  assert.equal(errors.translateBackendMessage(message), expected);
});

test('the identity messages are recognised for any record number and for an upper-case uuid', () => {
  const upper = UUID.toUpperCase();
  assert.match(errors.translateCsvImportMessage(`Invalid CSV record 10002 (NODE): identity ${upper} is repeated in the file`), /^Línea 10002: la identidad 6F1C2A9E/);
  assert.match(errors.translateCsvImportMessage(`Invalid CSV record 2 (NODE): identity ${OTHER_UUID} already exists in this version`), /^Línea 2: la identidad 0a8e5c1d/);
});

test('the identity messages without a record number are not guessed: generic text and the original as detail', () => {
  const message = `Invalid CSV record (NODE): identity ${UUID} already exists in this version`;
  assert.equal(errors.translateCsvImportMessage(message), null);
  assert.equal(errors.csvImportErrorLine(message), null);
  assert.deepEqual(errors.describeCsvImportError(message), {
    line: null,
    text: 'El importador rechazó el archivo.',
    detail: message,
  });
  assert.equal(errors.translateBackendMessage(message), message, 'the shared translator leaves it alone');
});

test('a malformed uuid is not taken for the identity rule: it falls to the row-cause wrapper', () => {
  const message = 'Invalid CSV record 4 (NODE): identity not-a-uuid already exists in this version';
  assert.equal(errors.translateCsvImportMessage(message), 'Línea 4 (nodo): identity not-a-uuid already exists in this version');
});

test('a failed row of the saved node keeps its cause, translated when the cause is known', () => {
  assert.equal(
    errors.translateCsvImportMessage('Invalid CSV record 5 (NODE): La duración del mantenimiento no puede ser negativa'),
    'Línea 5 (nodo): La duración del mantenimiento no puede ser negativa',
  );
  assert.equal(
    errors.translateCsvImportMessage("Invalid CSV record 6 (CONNECTION): Node not found with id: abc"),
    'Línea 6 (conexión): El nodo no existe o ya fue eliminado.',
  );
});

test('the other importer messages are translated by pattern', () => {
  const t = errors.translateCsvImportMessage;
  assert.equal(t('CSV record 4 has 3 fields; expected 12'), 'Línea 4: tiene 3 campos y el encabezado define 12. Revisá las comas y las comillas.');
  assert.equal(t("CSV record 2 (NODE) is missing required field 'name'"), 'Línea 2: a la fila de nodo le falta el campo obligatorio «name».');
  assert.equal(t("CSV record 3 (WELL) has a value in unsupported column 'length'"), 'Línea 3: la columna «length» no corresponde a un WELL. Dejala vacía en esa fila.');
  assert.equal(t("Unsupported nodeKind 'POZO' in CSV record 2. Supported values: WELL, PIPELINE"), 'Línea 2: el tipo de nodo «POZO» no existe. Valores admitidos: WELL, PIPELINE.');
  assert.equal(t('CSV record 9 has invalid JSON in column \'type\''), 'Línea 9: la columna «type» no contiene un JSON válido.');
  assert.equal(t("CSV record 5 column 'identity' must contain a UUID"), 'Línea 5: la columna identity debe contener un UUID válido o quedar vacía.');
  assert.equal(t("CSV record 3 (CONNECTION) references unknown node id 'x' in 'toNodeId'"), 'Línea 3: la conexión usa «x» en toNodeId, pero ningún nodo del archivo tiene ese id.');
  assert.equal(t('CSV file must be 10 MB or smaller'), 'El archivo supera el máximo de 10 MB.');
  assert.equal(t('CSV file must be valid UTF-8'), 'El archivo tiene que estar guardado como CSV UTF-8.');
  assert.equal(t('Unknown CSV header(s): foo, bar'), 'El encabezado tiene columnas que el importador no conoce: foo, bar.');
  assert.equal(t('CSV exceeds the maximum of 10000 rows'), 'El archivo supera el máximo de 10000 filas de datos.');
  assert.equal(t('CSV can contain only one SCENARIO record'), 'El archivo puede tener una sola fila SCENARIO.');
});

test('a row message that is not known keeps its line and shows the original as detail; a file one does not invent a line', () => {
  const row = "CSV record 8 (NODE) has something new 'x'";
  assert.deepEqual(errors.describeCsvImportError(row), { line: 8, text: 'Línea 8: la fila no es válida.', detail: row });
  const file = 'Something else entirely';
  assert.deepEqual(errors.describeCsvImportError(file), { line: null, text: 'El importador rechazó el archivo.', detail: file });
});

test('the previous translations and passthroughs are unchanged', () => {
  assert.equal(errors.translateBackendMessage('Version name cannot be null'), 'El nombre de la versión es obligatorio.');
  assert.equal(errors.translateBackendMessage('You are not allowed to edit this version'), 'No tenés permiso para editar esta versión.');
  assert.equal(errors.translateBackendMessage('Una versión admite como máximo 20'), 'Esta versión ya tiene el máximo de 20 escenarios. Eliminá alguno para crear otro.');
  assert.equal(errors.translateBackendMessage('CSV header is missing required column(s): mail'), 'Al archivo le faltan columnas obligatorias. Descargá la plantilla para ver los nombres que acepta.');
  assert.equal(errors.translateBackendMessage('The CSV file is empty'), 'El archivo no tiene filas.');
  assert.equal(errors.translateBackendMessage('Some unknown backend text'), 'Some unknown backend text');
});

// ---- parsing and analysis ------------------------------------------------------------------------------------------

const HEAD = 'nodeKind,name,state,lifespanInMonths,upkeepCosts,maintenanceIntervalInDays,operatingCosts,wastePercentage,type,investmentCost,identity';
const typeCell = (vertical, role, nodeType) => `"{""vertical"":""${vertical}"",""role"":""${role}"",""nodeType"":""${nodeType}""}"`;
const well = (identity = '') => `WELL,Pozo,RUNNING,240,1,30,2,0.1,${typeCell('EXTRACTION', 'GENERATOR', 'WELL')},"{""components"":[]}",${identity}`;
const row = (kind, typeText) => `${kind},N,RUNNING,240,1,30,2,0.1,${typeText},"{""components"":[]}",`;
const rawCell = (text) => `"${text.replace(/"/g, '""')}"`;

test('the parser reads quotes, escaped quotes, embedded line breaks, CRLF and a BOM like the backend', () => {
  const { records, error } = csv.parseCsvRecords('﻿a,b\r\n"x, y","say ""hi"""\r\n\r\n"two\nlines", z \r\n');
  assert.equal(error, null);
  assert.deepEqual(records, [['a', 'b'], ['x, y', 'say "hi"'], ['two\nlines', 'z']]);
});

test('malformed quoting is reported', () => {
  assert.match(csv.parseCsvRecords('a,b\n"x"y,1').error, /después de unas comillas/);
  assert.match(csv.parseCsvRecords('a,b\nx"y,1').error, /dentro de un campo/);
  assert.match(csv.parseCsvRecords('a,b\n"x,1').error, /nunca se cierra/);
});

test('a clean file is summarised: nodes, verticals, no problems', () => {
  const analysis = csv.analyzeNodesCsv(`${HEAD}\n${well()}\n${well(UUID)}\n`);
  assert.deepEqual(analysis.problems, []);
  assert.equal(analysis.nodeCount, 2);
  assert.equal(analysis.identityCount, 1);
  assert.deepEqual(analysis.verticals, ['EXTRACTION']);
  assert.equal(csv.importSummary(analysis), '2 nodos');
});

test('records are numbered with the header as 1 and blank lines skipped, so the first data row is line 2', () => {
  const analysis = csv.analyzeNodesCsv(`${HEAD}\n\n${well(UUID)}\n\n${well(UUID)}\n`);
  assert.deepEqual(analysis.problems, [
    { line: 3, message: `la identidad ${UUID} aparece repetida en el archivo. Cada nodo debe tener una identidad única.` },
  ]);
  assert.equal(csv.formatProblem(analysis.problems[0]), `Línea 3: la identidad ${UUID} aparece repetida en el archivo. Cada nodo debe tener una identidad única.`);
});

test('identity must be a uuid when present and may be empty', () => {
  const analysis = csv.analyzeNodesCsv(`${HEAD}\n${well('abc')}\n${well()}\n`);
  assert.equal(analysis.problems.length, 1);
  assert.equal(analysis.problems[0].line, 2);
  assert.match(analysis.problems[0].message, /identity debe contener un UUID/);
  assert.deepEqual(csv.analyzeNodesCsv(`${HEAD.replace(',identity', '')}\n${well().replace(/,$/, '')}\n`).problems, []);
});

test('file level problems stop the analysis with one message', () => {
  const only = (raw) => csv.analyzeNodesCsv(raw).problems.map((p) => p.message);
  assert.deepEqual(only('   '), ['El archivo está vacío.']);
  assert.deepEqual(only(HEAD), ['El archivo necesita un encabezado y al menos una fila de datos.']);
  assert.deepEqual(only('name,state\nx,y'), ['Al encabezado le falta la columna nodeKind.']);
  assert.deepEqual(only('nodeKind,nodeKind\nWELL,WELL'), ['El encabezado repite el nombre de una columna.']);
  assert.deepEqual(only('nodeKind,\nWELL,'), ['El encabezado tiene una columna sin nombre.']);
  assert.match(only('nodeKind,name\nWELL,�')[0], /UTF-8/);
  assert.match(only(`nodeKind\n${'WELL\n'.repeat(10001)}`)[0], /10001 filas de datos y el máximo es 10000/);
});

test('rows with the wrong number of fields, an unknown kind or recordType are reported with their line', () => {
  const analysis = csv.analyzeNodesCsv('recordType,nodeKind,name\nNODE,WELL,a\nNODE,POZO,b\nFOO,WELL,c\nNODE,WELL\n');
  assert.deepEqual(analysis.problems.map((p) => p.line), [3, 4, 5]);
  assert.match(analysis.problems[0].message, /«POZO» no existe/);
  assert.match(analysis.problems[1].message, /«FOO» no existe/);
  assert.match(analysis.problems[2].message, /tiene 2 campos y el encabezado define 3/);
});

test('connections and the scenario row are counted, and a second scenario row is a problem', () => {
  const head = 'recordType,nodeKind,name,fromNodeId,toNodeId,economicScenario';
  const ok = csv.analyzeNodesCsv(`${head}\nNODE,WELL,a,,,\nCONNECTION,,,a,b,\nSCENARIO,,,,,{}\n`);
  assert.deepEqual(ok.problems, []);
  assert.equal(ok.connectionCount, 1);
  assert.equal(ok.scenarioCount, 1);
  assert.equal(csv.importSummary(ok), '1 nodo, 1 conexión, configuración económica');
  const twice = csv.analyzeNodesCsv(`${head}\nNODE,WELL,a,,,\nSCENARIO,,,,,{}\nSCENARIO,,,,,{}\n`);
  assert.deepEqual(twice.problems.map((p) => p.line), [4]);
});

test('a file with no node rows, or economic profiles without a scenario row, is flagged', () => {
  assert.deepEqual(csv.analyzeNodesCsv('recordType,nodeKind\nCONNECTION,\n').problems.map((p) => p.message), ['El archivo necesita al menos una fila de nodo (NODE).']);
  const profile = csv.analyzeNodesCsv('nodeKind,name,economicProfile\nWELL,a,"{}"\n');
  assert.equal(profile.hasEconomicProfiles, true);
  assert.deepEqual(profile.problems.map((p) => p.message), ['Hay nodos con economicProfile, pero falta la fila SCENARIO con economicScenario.']);
});

test('a refused connection identity is translated with its line and uuid, from the file and from the version', () => {
  const repeated = `Invalid CSV record 6 (CONNECTION): identity ${UUID} is repeated in the file`;
  const repeatedText = `Línea 6: la identidad ${UUID} aparece en más de una conexión del archivo. Cada conexión debe tener una identidad única.`;
  assert.deepEqual(errors.describeCsvImportError(repeated), { line: 6, text: repeatedText, detail: null });

  const existing = `Invalid CSV record 4 (CONNECTION): identity ${OTHER_UUID} already exists in this version`;
  const existingText =
    `Línea 4: la identidad ${OTHER_UUID} ya la tiene una conexión de esta versión. El importador no reemplaza ` +
    'conexiones: quitá esa fila o dejá la columna identity vacía.';
  assert.deepEqual(errors.describeCsvImportError(existing), { line: 4, text: existingText, detail: null });
  const failure = csv.classifyImportFailure({ status: 400, canceled: false, message: existing, fallback: 'fallback' });
  assert.deepEqual([failure.kind, failure.line, failure.text], ['rejected', 4, existingText]);
});

test('the editor call that repeats a connection identity is translated too', () => {
  assert.equal(
    errors.translateBackendMessage(`Connection identity ${UUID} already exists in this version`),
    `Ya hay una conexión con la identidad ${UUID} en esta versión. No se guardó nada.`,
  );
});

// ---- templates ------------------------------------------------------------------------------------------------------

test('the example files analyse clean and carry no identity column that could collide', () => {
  const operational = csv.buildTemplateCsv('operational');
  const full = csv.buildTemplateCsv('full');
  for (const text of [operational, full]) {
    assert.ok(!csv.parseCsvRecords(text).records[0].includes('identity'));
    assert.deepEqual(csv.analyzeNodesCsv(text).problems, []);
  }
  assert.equal(csv.analyzeNodesCsv(operational).nodeCount, 1);
  const analysis = csv.analyzeNodesCsv(full);
  assert.deepEqual([analysis.nodeCount, analysis.connectionCount, analysis.scenarioCount, analysis.hasEconomicProfiles], [2, 1, 1, true]);
  assert.deepEqual(analysis.verticals, ['EXTRACTION', 'TRANSPORTATION']);
});

test('the JSON cells of the example files survive the CSV round trip', () => {
  const { records } = csv.parseCsvRecords(csv.buildTemplateCsv('full'));
  const header = records[0];
  const scenario = records.find((row) => row[header.indexOf('recordType')] === 'SCENARIO');
  const parsed = JSON.parse(scenario[header.indexOf('economicScenario')]);
  assert.deepEqual(parsed.boundary, ['empresa-1']);
  assert.equal(parsed.taxEntities[0].id, 'empresa-1');
  const node = records[1];
  assert.equal(JSON.parse(node[header.indexOf('type')]).nodeType, 'TREATMENT_PLANT');
  assert.deepEqual(JSON.parse(node[header.indexOf('economicProfile')]).ownership, [{ taxEntityId: 'empresa-1', share: 1 }]);
  assert.equal(csv.templateFileName('operational'), 'plantilla-nodos.csv');
  assert.equal(csv.templateFileName('connections'), 'plantilla-nodos-y-conexiones.csv');
  assert.equal(csv.templateFileName('full'), 'plantilla-nodos-con-economia.csv');
});

test('the help text states that identity is optional and what it may not be', () => {
  assert.match(csv.IDENTITY_HELP, /opcional/);
  assert.match(csv.IDENTITY_HELP, /no exista ya en esta versión/);
  assert.match(csv.IDENTITY_HELP, /no se repita en el archivo/);
});

const TEMPLATE_KINDS = ['operational', 'connections', 'full'];
const { canConnect } = load(resolve(root, 'components/editor/nodeCatalog.ts'));

/** The CONNECTION rows of a template as [source nodeType, target nodeType], in the spelling of `NodeTypeEnum`. */
function templateConnections(kind) {
  const { records } = csv.parseCsvRecords(csv.buildTemplateCsv(kind));
  const [header, ...rows] = records;
  const cell = (record, name) => record[header.indexOf(name)];
  const typeById = new Map(
    rows
      .filter((r) => cell(r, 'recordType') === 'NODE')
      .map((r) => [cell(r, 'id'), csv.NODE_TYPE_RULES[cell(r, 'nodeKind')].nodeType]),
  );
  return rows
    .filter((r) => cell(r, 'recordType') === 'CONNECTION')
    .map((r) => [typeById.get(cell(r, 'fromNodeId')), typeById.get(cell(r, 'toNodeId'))]);
}

test('every connection of every example file is one the editor allows', () => {
  for (const kind of TEMPLATE_KINDS) {
    const connections = templateConnections(kind);
    assert.equal(connections.length, kind === 'operational' ? 0 : 1, kind);
    for (const [from, to] of connections) {
      assert.ok(from && to, `${kind}: both ends are nodes of the file`);
      assert.equal(canConnect(from, to), true, `${kind}: ${from} -> ${to}`);
    }
  }
});

test('the nodes-and-connections example has no economics and needs no economic permission', () => {
  const text = csv.buildTemplateCsv('connections');
  const { records } = csv.parseCsvRecords(text);
  assert.ok(!records[0].includes('economicProfile'));
  assert.ok(!records[0].includes('economicScenario'));
  assert.ok(!records[0].includes('identity'));
  assert.ok(!records.some((record) => record.includes('SCENARIO')));
  const analysis = csv.analyzeNodesCsv(text);
  assert.deepEqual(analysis.problems, []);
  assert.deepEqual([analysis.nodeCount, analysis.connectionCount, analysis.scenarioCount, analysis.hasEconomicProfiles], [2, 1, 0, false]);
  assert.deepEqual(csv.importPermissionBlockers(analysis, version({ canEditEconomicConfiguration: false })), []);
});

test('the copies the backend imports are byte for byte what the screen downloads', () => {
  const { withBom } = load(resolve(root, 'utils/csvImport.ts'));
  for (const kind of TEMPLATE_KINDS) {
    const copy = readFileSync(
      new URL(`../../backend/src/test/resources/csv-templates/${csv.templateFileName(kind)}`, import.meta.url),
      'utf8',
    );
    assert.equal(copy, withBom(csv.buildTemplateCsv(kind)), kind);
  }
});

// ---- permissions ---------------------------------------------------------------------------------------------------

const version = (over = {}) => ({
  versionId: 'v1',
  canEdit: true,
  canEditConnections: true,
  canEditEconomicConfiguration: true,
  editableVerticals: ['EXTRACTION', 'TRANSPORTATION', 'LIQUEFACTION', 'EXPORT', 'INTERNAL_CONSUMPTION'],
  ...over,
});
const access = (...versions) => ({ projectId: 'p', versions });

test('importing is denied by default: no access data, an unknown version or no edit right', () => {
  assert.equal(csv.canImportInto(null, 'v1'), false);
  assert.equal(csv.canImportInto(undefined, 'v1'), false);
  assert.equal(csv.canImportInto(access(version()), null), false);
  assert.equal(csv.canImportInto(access(version()), 'other'), false);
  assert.equal(csv.canImportInto(access(version({ canEdit: false })), 'v1'), false);
  assert.equal(csv.canImportInto(access(version()), 'v1'), true);
});

test('what the file needs is checked against the caller: verticals, connections and economics', () => {
  const full = csv.analyzeNodesCsv(csv.buildTemplateCsv('full'));
  assert.deepEqual(csv.importPermissionBlockers(full, version()), []);
  assert.deepEqual(csv.importPermissionBlockers(full, null), [csv.NO_IMPORT_PERMISSION_MESSAGE]);
  assert.deepEqual(csv.importPermissionBlockers(full, version({ canEdit: false })), [csv.NO_IMPORT_PERMISSION_MESSAGE]);
  const noTransport = csv.importPermissionBlockers(full, version({ editableVerticals: ['EXTRACTION'] }));
  assert.equal(noTransport.length, 1);
  assert.match(noTransport[0], /Transporte/);
  assert.match(csv.importPermissionBlockers(full, version({ canEditConnections: false }))[0], /conexiones/);
  assert.match(csv.importPermissionBlockers(full, version({ canEditEconomicConfiguration: false }))[0], /configuración económica/);
  const operational = csv.analyzeNodesCsv(csv.buildTemplateCsv('operational'));
  assert.deepEqual(csv.importPermissionBlockers(operational, version({ canEditConnections: false, canEditEconomicConfiguration: false })), []);
});

// ---- warnings, start and results -----------------------------------------------------------------------------------

test('the warnings always say the import only adds and cannot be undone; the scenario row adds the economics one', () => {
  const operational = csv.importWarnings(csv.analyzeNodesCsv(csv.buildTemplateCsv('operational')), 'V2');
  assert.deepEqual(operational.map((w) => w.id), ['adds-only', 'no-undo']);
  assert.match(operational[0].text, /«V2»/);
  const full = csv.importWarnings(csv.analyzeNodesCsv(csv.buildTemplateCsv('full')), 'V2');
  assert.deepEqual(full.map((w) => w.id), ['adds-only', 'replaces-base-economics', 'no-undo']);
  assert.match(full[1].text, /Escenario base/);
});

test('the import starts only with a clean file, no blockers and the warnings confirmed', () => {
  const clean = csv.analyzeNodesCsv(csv.buildTemplateCsv('operational'));
  const dirty = csv.analyzeNodesCsv('x');
  const ok = { analysis: clean, blockers: [], confirmed: true, sending: false };
  assert.equal(csv.canStartImport(ok), true);
  assert.equal(csv.canStartImport({ ...ok, analysis: null }), false);
  assert.equal(csv.canStartImport({ ...ok, analysis: dirty }), false);
  assert.equal(csv.canStartImport({ ...ok, blockers: ['x'] }), false);
  assert.equal(csv.canStartImport({ ...ok, confirmed: false }), false);
  assert.equal(csv.canStartImport({ ...ok, sending: true }), false);
});

test('failures are classified: a rejected file imported nothing, no answer means uncertain', () => {
  const classify = (over) => csv.classifyImportFailure({ status: 400, canceled: false, message: null, fallback: 'fallback', ...over });
  const identity = classify({ message: `Invalid CSV record 7 (NODE): identity ${UUID} already exists in this version` });
  assert.equal(identity.kind, 'rejected');
  assert.equal(identity.line, 7);
  assert.match(identity.text, /^Línea 7: la identidad/);
  assert.equal(identity.detail, null);
  const unknown = classify({ message: 'Something odd' });
  assert.deepEqual([unknown.kind, unknown.text, unknown.detail], ['rejected', 'El importador rechazó el archivo.', 'Something odd']);
  assert.equal(classify({ status: 413 }).text, 'El archivo supera el máximo de 10 MB.');
  assert.deepEqual([classify({ status: 403 }).kind, classify({ status: 403 }).text], ['rejected', 'fallback']);
  assert.equal(classify({ status: 400, message: null }).text, 'fallback');
  for (const over of [{ status: null }, { status: 502 }, { status: 500 }, { status: null, canceled: true }]) {
    const failure = classify(over);
    assert.equal(failure.kind, 'uncertain');
    assert.match(failure.text, /Revisá los nodos de la versión en el editor/);
  }
  assert.match(classify({ status: null, canceled: true }).text, /^Cancelaste la importación\./);
});

test('the result messages and where the buttons lead', () => {
  assert.equal(csv.importedNodesMessage(1), 'Se importó 1 nodo.');
  assert.equal(csv.importedNodesMessage(12), 'Se importaron 12 nodos.');
  assert.deepEqual(csv.importDestinations('p 1', 'v/2'), {
    editor: '/editor?versionId=v%2F2',
    scenarios: '/projects/p%201/versions/v%2F2/scenarios',
  });
});

// ---- type / role / vertical coherence --------------------------------------------------------------------------------

// Written by hand from backend NodeTypeEnum.java: [nodeKind, vertical, role, type.nodeType]. The nodeType column keeps
// the enum's own spelling (PIPELINE_CONECTION, GROUND_LIQUEFACTION_PLANT, LNG_CAMER), not nodeKind's.
const EXPECTED = [
  ['WELL', 'EXTRACTION', 'GENERATOR', 'WELL'],
  ['GATHERING_NETWORK', 'EXTRACTION', 'INTERMEDIATE', 'GATHERING_NETWORK'],
  ['TREATMENT_PLANT', 'EXTRACTION', 'INTERMEDIATE', 'TREATMENT_PLANT'],
  ['PIPELINE', 'TRANSPORTATION', 'INTERMEDIATE', 'PIPELINE'],
  ['PIPELINE_CONNECTION', 'TRANSPORTATION', 'INTERMEDIATE', 'PIPELINE_CONECTION'],
  ['COMPRESSING_PLANT', 'TRANSPORTATION', 'INTERMEDIATE', 'COMPRESSING_PLANT'],
  ['GROUND_BASED_LIQUEFACTION_PLANT', 'LIQUEFACTION', 'INTERMEDIATE', 'GROUND_LIQUEFACTION_PLANT'],
  ['FLNG_UNIT', 'LIQUEFACTION', 'INTERMEDIATE', 'FLNG_UNIT'],
  ['LNG_CARRIER', 'EXPORT', 'SINK', 'LNG_CAMER'],
  ['SEAPORT_TERMINAL', 'EXPORT', 'SINK', 'SEAPORT_TERMINAL'],
  ['INTERNAL_CONSUMPTION', 'INTERNAL_CONSUMPTION', 'SINK', 'INTERNAL_CONSUMPTION'],
  ['INDUSTRIAL_CONSUMPTION', 'INTERNAL_CONSUMPTION', 'SINK', 'INDUSTRIAL_CONSUMPTION'],
];
const ROLES = ['GENERATOR', 'INTERMEDIATE', 'SINK'];
const VERTICALS = ['EXTRACTION', 'TRANSPORTATION', 'LIQUEFACTION', 'EXPORT', 'INTERNAL_CONSUMPTION'];
const messages = (raw) => csv.analyzeNodesCsv(raw).problems.map((p) => `Línea ${p.line}: ${p.message}`);

test('the type rules match the hand-written NodeTypeEnum matrix, one rule per supported nodeKind', () => {
  assert.deepEqual(
    Object.entries(csv.NODE_TYPE_RULES).map(([kind, r]) => [kind, r.vertical, r.role, r.nodeType]),
    EXPECTED,
  );
  assert.deepEqual(Object.keys(csv.NODE_TYPE_RULES).sort(), Object.keys(csv.NODE_KIND_VERTICAL).sort());
});

test('the vertical used for the edit permission is the NodeTypeEnum one for every kind, INDUSTRIAL_CONSUMPTION included', () => {
  for (const [kind, vertical] of EXPECTED) {
    assert.equal(csv.NODE_KIND_VERTICAL[kind], vertical, kind);
  }
  assert.equal(csv.NODE_KIND_VERTICAL.INDUSTRIAL_CONSUMPTION, 'INTERNAL_CONSUMPTION');
});

test('a file with an INDUSTRIAL_CONSUMPTION node needs the Consumo interno vertical, not Exportación', () => {
  const analysis = csv.analyzeNodesCsv(`${HEAD}\n${row('INDUSTRIAL_CONSUMPTION', typeCell('INTERNAL_CONSUMPTION', 'SINK', 'INDUSTRIAL_CONSUMPTION'))}\n`);
  assert.deepEqual(analysis.problems, []);
  assert.deepEqual(analysis.verticals, ['INTERNAL_CONSUMPTION']);
  assert.deepEqual(csv.importPermissionBlockers(analysis, version({ editableVerticals: ['INTERNAL_CONSUMPTION'] })), []);
  const onlyExport = csv.importPermissionBlockers(analysis, version({ editableVerticals: ['EXPORT'] }));
  assert.equal(onlyExport.length, 1);
  assert.match(onlyExport[0], /Consumo interno/);
});

test('a row whose type says what its nodeKind implies is clean, for every kind', () => {
  for (const [kind, vertical, role, nodeType] of EXPECTED) {
    assert.deepEqual(messages(`${HEAD}\n${row(kind, typeCell(vertical, role, nodeType))}\n`), [], kind);
  }
});

test('every wrong role is reported with the expected one and the line, for every kind', () => {
  for (const [kind, vertical, role, nodeType] of EXPECTED) {
    for (const wrong of ROLES.filter((r) => r !== role)) {
      assert.deepEqual(
        messages(`${HEAD}\n${row(kind, typeCell(vertical, wrong, nodeType))}\n`),
        [`Línea 2: ${kind} debe tener el rol ${role} (el archivo dice ${wrong})`],
        `${kind} with ${wrong}`,
      );
    }
  }
});

test('every wrong vertical is reported with the expected one and the line, for every kind', () => {
  for (const [kind, vertical, role, nodeType] of EXPECTED) {
    for (const wrong of VERTICALS.filter((v) => v !== vertical)) {
      assert.deepEqual(
        messages(`${HEAD}\n${row(kind, typeCell(wrong, role, nodeType))}\n`),
        [`Línea 2: ${kind} debe tener la vertical ${vertical} (el archivo dice ${wrong})`],
        `${kind} with ${wrong}`,
      );
    }
  }
});

test('the message of the screenshot: a SEAPORT_TERMINAL as INTERMEDIATE on line 9', () => {
  const good = row('WELL', typeCell('EXTRACTION', 'GENERATOR', 'WELL'));
  const bad = row('SEAPORT_TERMINAL', typeCell('EXPORT', 'INTERMEDIATE', 'SEAPORT_TERMINAL'));
  const raw = [HEAD, ...Array(7).fill(good), bad].join('\n');
  const analysis = csv.analyzeNodesCsv(raw);
  assert.equal(analysis.problems.length, 1);
  assert.equal(csv.formatProblem(analysis.problems[0]), 'Línea 9: SEAPORT_TERMINAL debe tener el rol SINK (el archivo dice INTERMEDIATE)');
});

test('type.nodeType uses the enum spelling, and the message explains it when it differs from nodeKind', () => {
  assert.deepEqual(messages(`${HEAD}\n${row('LNG_CARRIER', typeCell('EXPORT', 'SINK', 'LNG_CARRIER'))}\n`), [
    'Línea 2: LNG_CARRIER debe tener type.nodeType LNG_CAMER (el archivo dice LNG_CARRIER). Se escribe así aunque nodeKind use la ortografía LNG_CARRIER',
  ]);
  assert.deepEqual(messages(`${HEAD}\n${row('PIPELINE', typeCell('TRANSPORTATION', 'INTERMEDIATE', 'WELL'))}\n`), [
    'Línea 2: PIPELINE debe tener type.nodeType PIPELINE (el archivo dice WELL)',
  ]);
});

test('several errors in one file are all reported, in order, with the header counted as line 1', () => {
  const raw = [
    HEAD,
    well(),
    row('SEAPORT_TERMINAL', typeCell('EXPORT', 'INTERMEDIATE', 'SEAPORT_TERMINAL')),
    row('PIPELINE', typeCell('EXTRACTION', 'SINK', 'PIPELINE')),
    '',
    row('LNG_CARRIER', typeCell('EXPORT', 'SINK', 'LNG_CAMER')),
    row('FLNG_UNIT', typeCell('LIQUEFACTION', 'GENERATOR', 'FLNG_UNIT')),
  ].join('\n');
  const analysis = csv.analyzeNodesCsv(raw);
  assert.deepEqual(messages(raw), [
    'Línea 3: SEAPORT_TERMINAL debe tener el rol SINK (el archivo dice INTERMEDIATE)',
    'Línea 4: PIPELINE debe tener la vertical TRANSPORTATION (el archivo dice EXTRACTION)',
    'Línea 4: PIPELINE debe tener el rol INTERMEDIATE (el archivo dice SINK)',
    'Línea 6: FLNG_UNIT debe tener el rol INTERMEDIATE (el archivo dice GENERATOR)',
  ]);
  assert.equal(analysis.hiddenProblemCount, 0);
  assert.equal(analysis.nodeCount, 5);
});

test('a type cell that is empty, not JSON, not an object or missing keys is an error with its line and the right value', () => {
  const example = '{"vertical":"EXPORT","role":"SINK","nodeType":"SEAPORT_TERMINAL"}';
  assert.deepEqual(messages(`${HEAD}\n${row('SEAPORT_TERMINAL', '')}\n`), [`Línea 2: falta el valor de type. Para SEAPORT_TERMINAL es ${example}`]);
  assert.deepEqual(messages(`${HEAD}\n${row('SEAPORT_TERMINAL', rawCell('{"vertical": EXPORT}'))}\n`), [
    `Línea 2: la columna type no contiene un JSON válido. Para SEAPORT_TERMINAL tiene que ser ${example}`,
  ]);
  for (const text of ['[1,2]', '"SINK"', 'null', '7']) {
    assert.deepEqual(messages(`${HEAD}\n${row('SEAPORT_TERMINAL', rawCell(text))}\n`), [
      `Línea 2: la columna type debe ser un objeto JSON. Para SEAPORT_TERMINAL es ${example}`,
    ], text);
  }
  assert.deepEqual(messages(`${HEAD}\n${row('SEAPORT_TERMINAL', rawCell('{"vertical":"EXPORT"}'))}\n`), [
    'Línea 2: SEAPORT_TERMINAL debe tener el rol SINK (el archivo no lo indica)',
    'Línea 2: SEAPORT_TERMINAL debe tener type.nodeType SEAPORT_TERMINAL (el archivo no lo indica)',
  ]);
  assert.deepEqual(messages(`${HEAD}\n${row('SEAPORT_TERMINAL', rawCell('{"vertical":"EXPORT","role":5,"nodeType":"SEAPORT_TERMINAL"}'))}\n`), [
    'Línea 2: SEAPORT_TERMINAL debe tener el rol SINK (el archivo dice 5)',
  ]);
});

test('the kind is read case-insensitively, a wrong type is not checked when the kind is unknown, nor without a type column', () => {
  assert.deepEqual(messages(`${HEAD}\n${row('seaport_terminal', typeCell('EXPORT', 'INTERMEDIATE', 'SEAPORT_TERMINAL'))}\n`), [
    'Línea 2: SEAPORT_TERMINAL debe tener el rol SINK (el archivo dice INTERMEDIATE)',
  ]);
  const unknown = messages(`${HEAD}\n${row('POZO', typeCell('EXPORT', 'SINK', 'X'))}\n`);
  assert.equal(unknown.length, 1);
  assert.match(unknown[0], /«POZO» no existe/);
  assert.deepEqual(messages('nodeKind,name\nSEAPORT_TERMINAL,a\n'), []);
});

test('rows that are not NODE rows are not checked against the type rules', () => {
  const head = 'recordType,nodeKind,name,type,fromNodeId,toNodeId';
  assert.deepEqual(messages(`${head}\nNODE,WELL,a,${typeCell('EXTRACTION', 'GENERATOR', 'WELL')},,\nCONNECTION,,,,a,a\n`), []);
});

test('only the first 50 problems are listed and the rest are counted', () => {
  const bad = row('SEAPORT_TERMINAL', typeCell('EXPORT', 'INTERMEDIATE', 'SEAPORT_TERMINAL'));
  const analysis = csv.analyzeNodesCsv([HEAD, ...Array(60).fill(bad)].join('\n'));
  assert.equal(csv.MAX_PROBLEMS, 50);
  assert.equal(analysis.problems.length, 50);
  assert.equal(analysis.hiddenProblemCount, 10);
  assert.deepEqual([analysis.problems[0].line, analysis.problems[49].line], [2, 51]);
  assert.equal(csv.hiddenProblemsMessage(10), 'Y 10 problemas más.');
  assert.equal(csv.hiddenProblemsMessage(1), 'Y 1 problema más.');
  assert.equal(csv.analyzeNodesCsv([HEAD, ...Array(50).fill(bad)].join('\n')).hiddenProblemCount, 0);
});

test('a file with problems cannot start the import, whichever the problem', () => {
  const analysis = csv.analyzeNodesCsv(`${HEAD}\n${row('SEAPORT_TERMINAL', typeCell('EXPORT', 'INTERMEDIATE', 'SEAPORT_TERMINAL'))}\n`);
  assert.equal(csv.canStartImport({ analysis, blockers: [], confirmed: true, sending: false }), false);
});

test('the help text says the role and the vertical come from the node type', () => {
  assert.match(csv.NODE_TYPE_HELP, /rol y la vertical se deducen del tipo de nodo/);
  assert.match(csv.NODE_TYPE_HELP, /plantillas/);
});

test('the example files agree with the type rules', () => {
  const { records } = csv.parseCsvRecords(csv.buildTemplateCsv('full'));
  const header = records[0];
  for (const record of records.filter((r) => r[header.indexOf('recordType')] === 'NODE')) {
    const rule = csv.NODE_TYPE_RULES[record[header.indexOf('nodeKind')]];
    assert.deepEqual(JSON.parse(record[header.indexOf('type')]), { vertical: rule.vertical, role: rule.role, nodeType: rule.nodeType });
  }
});

// ---- the confirm button --------------------------------------------------------------------------------------------

const counted = (nodeCount, connectionCount, problems = []) => ({ nodeCount, connectionCount, problems });

test('the button says plain "Importar" without a file, with problems or with no nodes', () => {
  assert.equal(csv.importButtonLabel(null), 'Importar');
  assert.equal(csv.importButtonLabel(counted(2, 1, [{ line: 2, message: 'x' }])), 'Importar');
  assert.equal(csv.importButtonLabel(counted(0, 0)), 'Importar');
  assert.equal(csv.importButtonLabel(counted(0, 3)), 'Importar');
});

test('the button counts nodes and mentions connections only when the file has them', () => {
  assert.equal(csv.importButtonLabel(counted(1, 0)), 'Importar 1 nodo');
  assert.equal(csv.importButtonLabel(counted(5, 0)), 'Importar 5 nodos');
  assert.equal(csv.importButtonLabel(counted(1, 1)), 'Importar 1 nodo y 1 conexión');
  assert.equal(csv.importButtonLabel(counted(1, 4)), 'Importar 1 nodo y 4 conexiones');
  assert.equal(csv.importButtonLabel(counted(7, 1)), 'Importar 7 nodos y 1 conexión');
  assert.equal(csv.importButtonLabel(counted(12, 30)), 'Importar 12 nodos y 30 conexiones');
});

test('the button writes large counts the Argentine way', () => {
  const big = (n) => n.toLocaleString('es-AR');
  assert.equal(csv.importButtonLabel(counted(10000, 9999)), `Importar ${big(10000)} nodos y ${big(9999)} conexiones`);
});

// ---- the backend's own messages for the same rule -----------------------------------------------------------------

test('the backend role and vertical messages are translated, with their line when the row is named', () => {
  const t = errors.translateCsvImportMessage;
  assert.equal(
    t('Invalid CSV record 9 (SEAPORT_TERMINAL): Node role does not match its type: SEAPORT_TERMINAL is SINK, not INTERMEDIATE'),
    'Línea 9: SEAPORT_TERMINAL debe tener el rol SINK (el archivo dice INTERMEDIATE)',
  );
  assert.equal(
    t('Invalid CSV record 4 (NODE): Node vertical does not match its type: PIPELINE belongs to TRANSPORTATION, not EXTRACTION'),
    'Línea 4: PIPELINE debe tener la vertical TRANSPORTATION (el archivo dice EXTRACTION)',
  );
  assert.equal(t('Node role does not match its type: WELL is GENERATOR, not SINK'), 'WELL debe tener el rol GENERATOR (el archivo dice SINK)');
  assert.equal(
    t('Node vertical does not match its type: LNG_CAMER belongs to EXPORT, not LIQUEFACTION'),
    'LNG_CAMER debe tener la vertical EXPORT (el archivo dice LIQUEFACTION)',
  );
});

test('the translated role message reaches the screen with its line and no original detail', () => {
  const message = 'Invalid CSV record 9 (SEAPORT_TERMINAL): Node role does not match its type: SEAPORT_TERMINAL is SINK, not INTERMEDIATE';
  assert.deepEqual(errors.describeCsvImportError(message), {
    line: 9,
    text: 'Línea 9: SEAPORT_TERMINAL debe tener el rol SINK (el archivo dice INTERMEDIATE)',
    detail: null,
  });
  const failure = csv.classifyImportFailure({ status: 400, canceled: false, message, fallback: 'fallback' });
  assert.deepEqual([failure.kind, failure.line, failure.detail], ['rejected', 9, null]);
  assert.equal(errors.translateBackendMessage(message), 'Línea 9: SEAPORT_TERMINAL debe tener el rol SINK (el archivo dice INTERMEDIATE)');
});

test('a mismatch message that does not fit the known shape is left alone', () => {
  const odd = 'Node role does not match its type: something unexpected';
  assert.equal(errors.translateCsvImportMessage(odd), null);
  assert.equal(errors.translateBackendMessage(odd), odd);
});
