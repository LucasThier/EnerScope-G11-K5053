import test from 'node:test';
import assert from 'node:assert/strict';
import { resolve } from 'node:path';
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
const well = (identity = '') => `WELL,Pozo,RUNNING,240,1,30,2,0.1,"{""vertical"":""EXTRACTION""}","{""components"":[]}",${identity}`;

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
  assert.equal(JSON.parse(node[header.indexOf('type')]).nodeType, 'WELL');
  assert.deepEqual(JSON.parse(node[header.indexOf('economicProfile')]).ownership, [{ taxEntityId: 'empresa-1', share: 1 }]);
  assert.equal(csv.templateFileName('operational'), 'plantilla-nodos.csv');
  assert.equal(csv.templateFileName('full'), 'plantilla-nodos-con-economia.csv');
});

test('the help text states that identity is optional and what it may not be', () => {
  assert.match(csv.IDENTITY_HELP, /opcional/);
  assert.match(csv.IDENTITY_HELP, /no exista ya en esta versión/);
  assert.match(csv.IDENTITY_HELP, /no se repita en el archivo/);
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
