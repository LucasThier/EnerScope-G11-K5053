import type { Vertical } from '../types/diagram';
import type { ProjectAccess, VersionAccess } from '../types/versionAccess';
import { VERTICAL_LABELS } from '../components/editor/nodeCatalog';
import { describeCsvImportError } from '../api/errors';
import { findVersionAccess } from './versionAccessRules';
import { versionOperationPath, versionScenariosPath } from './scenarioRoutes';

/**
 * Client side of the node CSV importer (`POST /version/{id}/nodes/import`). The importer ADDS nodes to a version that
 * already exists: it never updates or replaces nodes, and it replaces the economic configuration of the version's
 * «Escenario base» when the file has a SCENARIO row. Everything here is pure; the real validation is the backend's.
 * See docs/importador-csv-nodos.md for the file format.
 */

export const MAX_FILE_BYTES = 10 * 1024 * 1024;
export const MAX_DATA_ROWS = 10_000;
export const BASE_SCENARIO_NAME = 'Escenario base';

/** `nodeKind` → the vertical whose editors may create that kind (docs/importador-csv-nodos.md). */
export const NODE_KIND_VERTICAL: Record<string, Vertical> = {
  WELL: 'EXTRACTION',
  GATHERING_NETWORK: 'EXTRACTION',
  TREATMENT_PLANT: 'EXTRACTION',
  PIPELINE: 'TRANSPORTATION',
  PIPELINE_CONNECTION: 'TRANSPORTATION',
  COMPRESSING_PLANT: 'TRANSPORTATION',
  GROUND_BASED_LIQUEFACTION_PLANT: 'LIQUEFACTION',
  FLNG_UNIT: 'LIQUEFACTION',
  LNG_CARRIER: 'EXPORT',
  SEAPORT_TERMINAL: 'EXPORT',
  INDUSTRIAL_CONSUMPTION: 'INTERNAL_CONSUMPTION',
  INTERNAL_CONSUMPTION: 'INTERNAL_CONSUMPTION',
};

export type StructuralRole = 'GENERATOR' | 'INTERMEDIATE' | 'SINK';

export interface NodeTypeRule {
  vertical: Vertical;
  role: StructuralRole;
  /** The exact `type.nodeType` the backend expects: the spelling of `NodeTypeEnum`, which is not always `nodeKind`'s. */
  nodeType: string;
}

/**
 * What `type` must say for each `nodeKind`, copied from `NodeTypeEnum` (backend): the vertical and the structural role
 * are derived from the node type, never chosen. `nodeType` keeps the enum's own spelling, which differs from
 * `nodeKind` for three kinds (`PIPELINE_CONECTION`, `GROUND_LIQUEFACTION_PLANT`, `LNG_CAMER`).
 * `NODE_KIND_VERTICAL` above must give the same vertical for every kind (a test checks it).
 */
export const NODE_TYPE_RULES: Record<string, NodeTypeRule> = {
  WELL: { vertical: 'EXTRACTION', role: 'GENERATOR', nodeType: 'WELL' },
  GATHERING_NETWORK: { vertical: 'EXTRACTION', role: 'INTERMEDIATE', nodeType: 'GATHERING_NETWORK' },
  TREATMENT_PLANT: { vertical: 'EXTRACTION', role: 'INTERMEDIATE', nodeType: 'TREATMENT_PLANT' },
  PIPELINE: { vertical: 'TRANSPORTATION', role: 'INTERMEDIATE', nodeType: 'PIPELINE' },
  PIPELINE_CONNECTION: { vertical: 'TRANSPORTATION', role: 'INTERMEDIATE', nodeType: 'PIPELINE_CONECTION' },
  COMPRESSING_PLANT: { vertical: 'TRANSPORTATION', role: 'INTERMEDIATE', nodeType: 'COMPRESSING_PLANT' },
  GROUND_BASED_LIQUEFACTION_PLANT: { vertical: 'LIQUEFACTION', role: 'INTERMEDIATE', nodeType: 'GROUND_LIQUEFACTION_PLANT' },
  FLNG_UNIT: { vertical: 'LIQUEFACTION', role: 'INTERMEDIATE', nodeType: 'FLNG_UNIT' },
  LNG_CARRIER: { vertical: 'EXPORT', role: 'SINK', nodeType: 'LNG_CAMER' },
  SEAPORT_TERMINAL: { vertical: 'EXPORT', role: 'SINK', nodeType: 'SEAPORT_TERMINAL' },
  INTERNAL_CONSUMPTION: { vertical: 'INTERNAL_CONSUMPTION', role: 'SINK', nodeType: 'INTERNAL_CONSUMPTION' },
  INDUSTRIAL_CONSUMPTION: { vertical: 'INTERNAL_CONSUMPTION', role: 'SINK', nodeType: 'INDUSTRIAL_CONSUMPTION' },
};

const RECORD_TYPES = ['NODE', 'CONNECTION', 'SCENARIO'];
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

// --- parsing ----------------------------------------------------------------------------------------------------

export interface ParsedRecords {
  /** Non-empty records, the header first; a record is numbered by its position (header = 1). */
  records: string[][];
  error: string | null;
}

/**
 * Reads CSV like the backend does (`CsvUtil.parseStrict`): comma separated, quotes with `""` as the escape, fields
 * trimmed, fully empty lines skipped, malformed quoting rejected. A leading BOM is dropped.
 */
export function parseCsvRecords(raw: string): ParsedRecords {
  const text = raw.startsWith('﻿') ? raw.slice(1) : raw;
  const records: string[][] = [];
  let current: string[] = [];
  let field = '';
  let inQuotes = false;
  let closedQuote = false;

  const endField = () => {
    current.push(field.trim());
    field = '';
  };
  const endRecord = () => {
    endField();
    if (!current.every((value) => value === '')) {
      records.push(current);
    }
    current = [];
    closedQuote = false;
  };
  const fail = (error: string): ParsedRecords => ({ records, error });

  for (let i = 0; i < text.length; i += 1) {
    const char = text[i];
    if (inQuotes) {
      if (char === '"') {
        if (text[i + 1] === '"') {
          field += '"';
          i += 1;
        } else {
          inQuotes = false;
          closedQuote = true;
        }
      } else {
        field += char;
      }
      continue;
    }
    const lineBreak = char === '\n' || char === '\r';
    if (lineBreak && char === '\r' && text[i + 1] === '\n') {
      i += 1;
    }
    if (closedQuote) {
      if (char === ',') {
        endField();
        closedQuote = false;
      } else if (lineBreak) {
        endRecord();
      } else if (char.trim() !== '') {
        return fail('Hay texto después de unas comillas de cierre. Revisá las comillas del archivo.');
      }
      continue;
    }
    if (char === '"') {
      if (field.trim() !== '') {
        return fail('Hay unas comillas dentro de un campo que no está entre comillas.');
      }
      field = '';
      inQuotes = true;
    } else if (char === ',') {
      endField();
    } else if (lineBreak) {
      endRecord();
    } else {
      field += char;
    }
  }
  if (inQuotes) {
    return fail('Hay un campo entre comillas que nunca se cierra.');
  }
  endRecord();
  return { records, error: null };
}

// --- analysis ----------------------------------------------------------------------------------------------------

export interface CsvProblem {
  /** Record number (header = 1) or null when it is about the file. */
  line: number | null;
  message: string;
}

export interface CsvAnalysis {
  /** The first MAX_PROBLEMS problems, in file order. */
  problems: CsvProblem[];
  /** How many more problems there were beyond those shown. */
  hiddenProblemCount: number;
  nodeCount: number;
  connectionCount: number;
  scenarioCount: number;
  hasEconomicProfiles: boolean;
  identityCount: number;
  /** Verticals of the NODE rows whose `nodeKind` is known, in catalog order. */
  verticals: Vertical[];
}

const EMPTY: CsvAnalysis = {
  problems: [],
  hiddenProblemCount: 0,
  nodeCount: 0,
  connectionCount: 0,
  scenarioCount: 0,
  hasEconomicProfiles: false,
  identityCount: 0,
  verticals: [],
};

const VERTICAL_ORDER = Object.keys(VERTICAL_LABELS) as Vertical[];
export const MAX_PROBLEMS = 50;

function blocked(message: string): CsvAnalysis {
  return { ...EMPTY, problems: [{ line: null, message }] };
}

function typeJsonExample(rule: NodeTypeRule): string {
  return JSON.stringify({ vertical: rule.vertical, role: rule.role, nodeType: rule.nodeType });
}

/**
 * Checks the `type` cell of a NODE row against what its `nodeKind` implies (the backend's `NodeTypeEnum`), one
 * sentence per problem, to be shown after "Línea N: ". Empty when the cell is right or the kind is unknown.
 */
export function checkNodeTypeCell(kind: string, cell: string): string[] {
  const rule = NODE_TYPE_RULES[kind];
  if (!rule) {
    return [];
  }
  const example = typeJsonExample(rule);
  if (cell === '') {
    return [`falta el valor de type. Para ${kind} es ${example}`];
  }
  let parsed: unknown;
  try {
    parsed = JSON.parse(cell);
  } catch {
    return [`la columna type no contiene un JSON válido. Para ${kind} tiene que ser ${example}`];
  }
  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    return [`la columna type debe ser un objeto JSON. Para ${kind} es ${example}`];
  }
  const fields = parsed as Record<string, unknown>;
  const checks: [keyof NodeTypeRule, string][] = [
    ['vertical', 'la vertical'],
    ['role', 'el rol'],
    ['nodeType', 'type.nodeType'],
  ];
  const problems: string[] = [];
  for (const [key, label] of checks) {
    const actual = fields[key];
    if (actual === rule[key]) {
      continue;
    }
    const said =
      actual === undefined ? 'el archivo no lo indica' : `el archivo dice ${typeof actual === 'string' ? actual : JSON.stringify(actual)}`;
    const spelling =
      key === 'nodeType' && rule.nodeType !== kind ? `. Se escribe así aunque nodeKind use la ortografía ${kind}` : '';
    problems.push(`${kind} debe tener ${label} ${rule[key]} (${said})${spelling}`);
  }
  return problems;
}

/** "Y N problemas más." for the problems beyond the ones listed. */
export function hiddenProblemsMessage(count: number): string {
  return count === 1 ? 'Y 1 problema más.' : `Y ${count} problemas más.`;
}

/** A first look at the file: what it carries and the problems the backend would certainly reject. Not a validator. */
export function analyzeNodesCsv(raw: string): CsvAnalysis {
  if (raw.trim() === '') {
    return blocked('El archivo está vacío.');
  }
  if (raw.includes('\uFFFD')) {
    return blocked('El archivo tiene que estar guardado como CSV UTF-8: hay caracteres que no se pueden leer.');
  }
  const { records, error } = parseCsvRecords(raw);
  if (error) {
    return blocked(error);
  }
  if (records.length < 2) {
    return blocked('El archivo necesita un encabezado y al menos una fila de datos.');
  }
  if (records.length - 1 > MAX_DATA_ROWS) {
    return blocked(`El archivo tiene ${records.length - 1} filas de datos y el máximo es ${MAX_DATA_ROWS}.`);
  }
  const headers = records[0];
  if (headers.some((header) => header === '')) {
    return blocked('El encabezado tiene una columna sin nombre.');
  }
  if (new Set(headers).size !== headers.length) {
    return blocked('El encabezado repite el nombre de una columna.');
  }
  const column = (name: string) => headers.indexOf(name);
  const kindColumn = column('nodeKind');
  if (kindColumn < 0) {
    return blocked('Al encabezado le falta la columna nodeKind.');
  }
  const typeColumn = column('recordType');
  const nodeTypeColumn = column('type');
  const profileColumn = column('economicProfile');
  const identityColumn = column('identity');

  const problems: CsvProblem[] = [];
  let hiddenProblemCount = 0;
  const report = (line: number | null, message: string) => {
    if (problems.length < MAX_PROBLEMS) {
      problems.push({ line, message });
    } else {
      hiddenProblemCount += 1;
    }
  };
  const verticals = new Set<Vertical>();
  const identities = new Set<string>();
  let nodeCount = 0;
  let connectionCount = 0;
  let scenarioCount = 0;
  let identityCount = 0;
  let hasEconomicProfiles = false;

  for (let index = 1; index < records.length; index += 1) {
    const row = records[index];
    const line = index + 1;
    if (row.length !== headers.length) {
      report(line, `tiene ${row.length} campos y el encabezado define ${headers.length}. Revisá las comas y las comillas.`);
      continue;
    }
    const recordType = typeColumn >= 0 ? row[typeColumn].toUpperCase() : 'NODE';
    if (typeColumn >= 0 && !RECORD_TYPES.includes(recordType)) {
      report(line, row[typeColumn] === '' ? 'falta el valor de recordType (NODE, CONNECTION o SCENARIO).' : `el tipo de fila «${row[typeColumn]}» no existe. Valores admitidos: ${RECORD_TYPES.join(', ')}.`);
      continue;
    }
    if (recordType === 'CONNECTION') {
      connectionCount += 1;
      continue;
    }
    if (recordType === 'SCENARIO') {
      scenarioCount += 1;
      if (scenarioCount > 1) {
        report(line, 'el archivo puede tener una sola fila SCENARIO.');
      }
      continue;
    }
    nodeCount += 1;
    const kind = row[kindColumn].toUpperCase();
    if (kind === '') {
      report(line, 'falta el valor de nodeKind (el tipo de nodo).');
    } else if (NODE_KIND_VERTICAL[kind]) {
      verticals.add(NODE_KIND_VERTICAL[kind]);
      if (nodeTypeColumn >= 0) {
        for (const message of checkNodeTypeCell(kind, row[nodeTypeColumn])) {
          report(line, message);
        }
      }
    } else {
      report(line, `el tipo de nodo «${row[kindColumn]}» no existe. Valores admitidos: ${Object.keys(NODE_KIND_VERTICAL).join(', ')}.`);
    }
    if (profileColumn >= 0 && row[profileColumn] !== '') {
      hasEconomicProfiles = true;
    }
    const identity = identityColumn >= 0 ? row[identityColumn] : '';
    if (identity !== '') {
      identityCount += 1;
      if (!UUID_PATTERN.test(identity)) {
        report(line, 'la columna identity debe contener un UUID válido o quedar vacía.');
      } else if (identities.has(identity.toLowerCase())) {
        report(line, `la identidad ${identity} aparece repetida en el archivo. Cada nodo debe tener una identidad única.`);
      } else {
        identities.add(identity.toLowerCase());
      }
    }
  }
  if (nodeCount === 0 && problems.length === 0) {
    report(null, 'El archivo necesita al menos una fila de nodo (NODE).');
  }
  if (hasEconomicProfiles && scenarioCount === 0) {
    report(null, 'Hay nodos con economicProfile, pero falta la fila SCENARIO con economicScenario.');
  }
  return {
    problems,
    hiddenProblemCount,
    nodeCount,
    connectionCount,
    scenarioCount,
    hasEconomicProfiles,
    identityCount,
    verticals: VERTICAL_ORDER.filter((vertical) => verticals.has(vertical)),
  };
}

export function formatProblem(problem: CsvProblem): string {
  return problem.line === null ? problem.message : `Línea ${problem.line}: ${problem.message}`;
}

// --- permissions -------------------------------------------------------------------------------------------------

export const NO_IMPORT_PERMISSION_MESSAGE = 'No tenés permiso para importar nodos en esta versión.';

/** Deny by default: only a loaded `/access` with edit rights on this very version lets the import start. */
export function canImportInto(access: ProjectAccess | null | undefined, versionId: string | null | undefined): boolean {
  return findVersionAccess(access, versionId)?.canEdit === true;
}

/** What this file needs that the caller does not have, one sentence each. Empty when nothing is missing. */
export function importPermissionBlockers(analysis: CsvAnalysis, version: VersionAccess | null): string[] {
  if (!version || !version.canEdit) {
    return [NO_IMPORT_PERMISSION_MESSAGE];
  }
  const blockers: string[] = [];
  const missing = analysis.verticals.filter((vertical) => !version.editableVerticals.includes(vertical));
  if (missing.length > 0) {
    blockers.push(
      `El archivo trae nodos de ${missing.map((vertical) => VERTICAL_LABELS[vertical]).join(', ')} y no podés editar ` +
        `${missing.length === 1 ? 'esa vertical' : 'esas verticales'} en esta versión. Quitá esas filas o pedí el rol de Editor.`,
    );
  }
  if (analysis.connectionCount > 0 && !version.canEditConnections) {
    blockers.push('El archivo trae conexiones y no tenés permiso para editar las conexiones de esta versión.');
  }
  if ((analysis.scenarioCount > 0 || analysis.hasEconomicProfiles) && !version.canEditEconomicConfiguration) {
    blockers.push('El archivo trae configuración económica y no tenés permiso para editarla en esta versión.');
  }
  return blockers;
}

// --- warnings that need a confirmation --------------------------------------------------------------------------

export interface ImportWarning {
  id: 'adds-only' | 'replaces-base-economics' | 'no-undo';
  text: string;
}

export function importWarnings(analysis: CsvAnalysis, versionName: string): ImportWarning[] {
  const warnings: ImportWarning[] = [];
  if (analysis.nodeCount > 0) {
    warnings.push({
      id: 'adds-only',
      text:
        `Solo agrega nodos nuevos a «${versionName}»: no actualiza ni reemplaza los que ya tiene. ` +
        'Si el archivo repite nodos que ya están, quedan duplicados.',
    });
  }
  if (analysis.scenarioCount > 0) {
    warnings.push({
      id: 'replaces-base-economics',
      text:
        `La fila SCENARIO reemplaza la configuración económica del «${BASE_SCENARIO_NAME}» de esta versión. ` +
        'Los demás escenarios no se modifican, pero quedan desactualizados si usan los nodos nuevos.',
    });
  }
  warnings.push({
    id: 'no-undo',
    text:
      'Si una fila falla, no se importa nada. Si todo sale bien no hay deshacer: para quitar nodos importados ' +
      'hay que borrarlos uno por uno desde el editor.',
  });
  return warnings;
}

/** The import may start only with a clean file, nothing blocking it and every warning acknowledged. */
export function canStartImport(input: {
  analysis: CsvAnalysis | null;
  blockers: string[];
  confirmed: boolean;
  sending: boolean;
}): boolean {
  return (
    input.analysis !== null &&
    input.analysis.problems.length === 0 &&
    input.analysis.nodeCount > 0 &&
    input.blockers.length === 0 &&
    input.confirmed &&
    !input.sending
  );
}

// --- results -----------------------------------------------------------------------------------------------------

export function importedNodesMessage(count: number): string {
  return count === 1 ? 'Se importó 1 nodo.' : `Se importaron ${count} nodos.`;
}

export function importSummary(analysis: CsvAnalysis): string {
  const parts = [`${analysis.nodeCount} ${analysis.nodeCount === 1 ? 'nodo' : 'nodos'}`];
  if (analysis.connectionCount > 0) {
    parts.push(`${analysis.connectionCount} ${analysis.connectionCount === 1 ? 'conexión' : 'conexiones'}`);
  }
  if (analysis.scenarioCount > 0) {
    parts.push('configuración económica');
  }
  return parts.join(', ');
}

/** Where "Ver en el editor" and "Ver escenarios" lead once the import is done. */
export function importDestinations(projectId: string, versionId: string): { editor: string; scenarios: string } {
  return { editor: versionOperationPath(versionId), scenarios: versionScenariosPath(projectId, versionId) };
}

export interface ImportFailure {
  /** `rejected`: the server answered and nothing was imported. `uncertain`: no answer, it may have finished. */
  kind: 'rejected' | 'uncertain';
  text: string;
  line: number | null;
  /** The backend's original text when it could not be translated. */
  detail: string | null;
}

/**
 * Turns how the request failed into what the screen shows. `message` is the backend's `ApiResponse.message`;
 * `fallback` is the already-translated text for statuses without a specific rule (403, 404, ...).
 */
export function classifyImportFailure(input: {
  status: number | null;
  canceled: boolean;
  message: string | null;
  fallback: string;
}): ImportFailure {
  if (input.canceled) {
    return { kind: 'uncertain', text: `Cancelaste la importación. ${UNCERTAIN_RESULT_MESSAGE}`, line: null, detail: null };
  }
  if (input.status === null) {
    return {
      kind: 'uncertain',
      text: `Se perdió la conexión con el servidor durante la importación. ${UNCERTAIN_RESULT_MESSAGE}`,
      line: null,
      detail: null,
    };
  }
  if (input.status >= 500) {
    return {
      kind: 'uncertain',
      text: `El servidor no pudo completar la importación. ${UNCERTAIN_RESULT_MESSAGE}`,
      line: null,
      detail: null,
    };
  }
  if (input.status === 413) {
    return { kind: 'rejected', text: 'El archivo supera el máximo de 10 MB.', line: null, detail: null };
  }
  if (input.status === 400 && input.message) {
    const described = describeCsvImportError(input.message);
    return { kind: 'rejected', text: described.text, line: described.line, detail: described.detail };
  }
  return { kind: 'rejected', text: input.fallback, line: null, detail: null };
}

/** What to tell the caller when the request ended without a response: the server may have finished anyway. */
export const UNCERTAIN_RESULT_MESSAGE =
  'No sabemos si el servidor llegó a importar el archivo. Revisá los nodos de la versión en el editor antes de volver a subirlo: ' +
  'si ya están, subirlo otra vez los duplica.';

// --- templates ---------------------------------------------------------------------------------------------------

export type TemplateKind = 'operational' | 'full';

const COMMON = [
  'nodeKind', 'name', 'state', 'lifespanInMonths', 'upkeepCosts', 'maintenanceIntervalInDays', 'operatingCosts',
  'wastePercentage', 'type', 'investmentCost',
];
const WELL = ['maxCollectionCapacity', 'declineCurve', 'gasRichness', 'dtmTime', 'dtmCost', 'surface'];
const PIPELINE = ['maxFlowCapacity', 'length', 'lossPerKm'];

function quote(value: string): string {
  return /[",\r\n]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value;
}

function line(values: string[]): string {
  return values.map(quote).join(',');
}

const WELL_TYPE = '{"vertical":"EXTRACTION","role":"GENERATOR","nodeType":"WELL"}';
const PIPELINE_TYPE = '{"vertical":"TRANSPORTATION","role":"INTERMEDIATE","nodeType":"PIPELINE"}';
const NO_COSTS = '{"components":[]}';

function nodeValues(kind: string, name: string, type: string, specific: Record<string, string>, columns: string[]): string[] {
  const base: Record<string, string> = {
    nodeKind: kind, name, state: 'RUNNING', lifespanInMonths: '240', upkeepCosts: '1000', maintenanceIntervalInDays: '30',
    operatingCosts: '5000', wastePercentage: '0.02', type, investmentCost: NO_COSTS, ...specific,
  };
  return columns.map((column) => base[column] ?? '');
}

const WELL_SPECIFIC = {
  maxCollectionCapacity: '1000000', declineCurve: '{"type":"CONSTANT","val_a":0.25}', gasRichness: '0.85', dtmTime: '0',
  dtmCost: '500', surface: '1200',
};
const PIPELINE_SPECIFIC = { maxFlowCapacity: '800000', length: '120', lossPerKm: '0.001' };

const SCENARIO_JSON =
  '{"startYear":2030,"years":5,"currency":"USD","wacc":0.1,' +
  '"taxEntities":[{"id":"empresa-1","name":"Empresa 1","jurisdiction":"HYPOTHETICAL","taxRate":0.3,"carryLosses":true,' +
  '"openingLoss":0,"lossExpiryYears":null,"lossOffsetLimit":1,"taxPaymentLagYears":0}],' +
  '"boundary":["empresa-1"],"adjustments":[],"contracts":[],"assets":[],"taxTreatments":[],"conversions":[]}';
const PROFILE_JSON = '{"ownership":[{"taxEntityId":"empresa-1","share":1}],"rules":[]}';

/**
 * Example files. Neither has an `identity` column on purpose: it is optional, and an identity copied from another
 * file would be rejected when it already exists in the version.
 */
export function buildTemplateCsv(kind: TemplateKind): string {
  if (kind === 'operational') {
    const columns = [...COMMON, ...WELL];
    return [line(columns), line(nodeValues('WELL', 'Pozo Norte', WELL_TYPE, WELL_SPECIFIC, columns))].join('\r\n') + '\r\n';
  }
  const columns = ['recordType', 'id', 'fromNodeId', 'toNodeId', ...COMMON, ...WELL, ...PIPELINE, 'economicProfile', 'economicScenario'];
  const row = (values: Record<string, string>) => line(columns.map((column) => values[column] ?? ''));
  const node = (id: string, kind: string, name: string, type: string, specific: Record<string, string>) => {
    const cells = nodeValues(kind, name, type, specific, columns);
    return row({
      ...Object.fromEntries(columns.map((column, index) => [column, cells[index]])),
      recordType: 'NODE',
      id,
      economicProfile: PROFILE_JSON,
    });
  };
  return (
    [
      line(columns),
      node('pozo-norte', 'WELL', 'Pozo Norte', WELL_TYPE, WELL_SPECIFIC),
      node('gasoducto-1', 'PIPELINE', 'Gasoducto 1', PIPELINE_TYPE, PIPELINE_SPECIFIC),
      row({ recordType: 'CONNECTION', fromNodeId: 'pozo-norte', toNodeId: 'gasoducto-1' }),
      row({ recordType: 'SCENARIO', economicScenario: SCENARIO_JSON }),
    ].join('\r\n') + '\r\n'
  );
}

export function templateFileName(kind: TemplateKind): string {
  return kind === 'operational' ? 'plantilla-nodos.csv' : 'plantilla-nodos-con-economia.csv';
}

/** Help text under the file picker about the optional `identity` column. */
export const IDENTITY_HELP =
  'La columna identity es opcional. Si la incluís, cada valor tiene que ser un UUID que no exista ya en esta versión ' +
  'y que no se repita en el archivo; si la dejás vacía, el sistema genera una identidad nueva.';

/** Help text under the file picker about `type`: the vertical and the role follow from the node type. */
export const NODE_TYPE_HELP =
  'En la columna type, el rol y la vertical se deducen del tipo de nodo: tienen que coincidir con nodeKind ' +
  '(por ejemplo, un SEAPORT_TERMINAL es siempre SINK de la vertical EXPORT). Las plantillas traen el valor correcto.';
