import { AxiosError } from 'axios';
import type { ApiResponse } from '../types/auth';

const KNOWN_BACKEND_MESSAGES: Record<string, string> = {
  'Invalid email or password': 'Email o contraseña incorrectos',
  'The project would be left without an administrator':
    'El proyecto no puede quedarse sin administrador. Asigná primero el rol de Administrador a otra persona.',
  'The organization would be left without an owner':
    'La organización no puede quedarse sin propietario. Asigná primero el rol de Propietario a otra persona.',
  'A project in this organization would be left without an administrator':
    'No se puede quitar a esa persona: algún proyecto de la organización quedaría sin administrador. ' +
    'Asigná primero el rol de Administrador a otra persona en esos proyectos.',
  'User is already a member of this project': 'Esa persona ya integra el proyecto.',
  "User is not a member of the project's organization":
    'Esa persona no pertenece a la organización del proyecto.',
  'User is not active': 'La cuenta de esa persona está desactivada.',
  'User not found': 'No se encontró la cuenta.',
  'Member not found': 'Esa persona ya no integra el proyecto.',
  'Project not found': 'El proyecto no existe o fue eliminado.',
  'You are not allowed to manage this project': 'No tenés permiso para administrar este proyecto.',
  'You are not allowed to view this project': 'No tenés acceso a este proyecto.',
  'You are not allowed to edit this project':
    'No tenés permiso para editar las versiones de este proyecto.',
  'You are not allowed to access this version': 'No tenés acceso a esta versión.',
  'You are not allowed to edit this version': 'No tenés permiso para editar esta versión.',
  "You are not allowed to edit this vertical's nodes in this version":
    'No tenés permiso para editar los nodos de esa vertical en esta versión.',
  'You are not allowed to simulate this version': 'No tenés permiso para simular esta versión.',
  'Project permission required: VIEW_PROJECT': 'No tenés acceso a este proyecto.',
  'Resource not found': 'No se encontró el recurso pedido.',
  'The versions belong to different projects':
    'No se pueden relacionar versiones de proyectos distintos.',
  'Parent version must belong to the same project':
    'La versión padre debe pertenecer al mismo proyecto.',
  'A version cannot be its own ancestor':
    'Esa versión no puede ser su propia ancestra: el padre elegido deriva de ella.',
  'Simulation graph contains a cycle':
    'El diagrama tiene un ciclo: revisá las conexiones antes de simular.',
  'You are not allowed to manage this version': 'No tenés permiso para administrar esta versión.',
  'You are not allowed to delete one or more versions below this one':
    'No podés eliminar esta versión porque tiene versiones derivadas que no tenés permiso para eliminar.',
  'You are not allowed to manage the roles of this version':
    'Solo los Administradores del proyecto pueden asignar, cambiar o quitar roles de versión.',
  'User already has this role': 'Esa persona ya tiene ese rol en la versión.',
  'EDITOR roles require a vertical': 'Elegí la vertical del Editor.',
  "User is not a member of the version's project":
    'Esa persona no integra el proyecto de esta versión.',
  'Version is not attached to any project': 'La versión no pertenece a ningún proyecto.',
  'Role not found': 'Ese rol ya no existe en esta versión.',
  'memberType cannot be null': 'Elegí un rol.',
  'Version name cannot be null': 'El nombre de la versión es obligatorio.',
  'Version name cannot be blank or whitespace only': 'El nombre de la versión es obligatorio.',
  'Organization not found': 'La organización no existe o fue desactivada.',
  'You are not allowed to manage users in this organization':
    'No tenés permiso para dar de alta personas en esta organización.',
  'An account with that email already exists': 'Ya existe una cuenta con ese email.',
  'The CSV file is empty': 'El archivo no tiene filas.',
  'The CSV file has more than 500 rows':
    'El archivo supera el máximo de 500 filas por importación.',
  'The uploaded file is empty': 'No se adjuntó ningún archivo.',
  'Could not read the uploaded file': 'No se pudo leer el archivo que subiste.',
  'Missing email': 'Falta el email.',
  'Invalid email': 'El email no tiene un formato válido.',
  'Duplicate email in file': 'Ese email está repetido en el archivo.',
  'Invalid first name (2-60 characters required)':
    'El nombre debe tener entre 2 y 60 caracteres.',
  'Invalid last name (2-60 characters required)':
    'El apellido debe tener entre 2 y 60 caracteres.',
  'Invalid role (OWNER or MEMBER)': 'El rol debe ser Propietario (OWNER) o Miembro (MEMBER).',
  'User is already a member of this organization': 'Esa persona ya integra la organización.',
  'You are not allowed to look users up': 'No tenés permiso para buscar cuentas en la plataforma.',
  'Only platform admins can list platform users':
    'Sólo un administrador de plataforma puede ver el listado de cuentas.',
  'Internal error': 'El servidor tuvo un problema y no pudo completar la operación.',
  'Malformed request body': 'No se pudo interpretar el pedido.',
  'A supported type.nodeType is required': 'Elegí un tipo de nodo válido.',
  'Version has no parent version to merge into':
    'Esa versión no tiene una versión padre con la que combinarse.',
  'Subversion does not have the specified parent version':
    'La subversión no deriva de la versión padre indicada.',
  'Invalid job title (120 characters max)': 'El cargo puede tener hasta 120 caracteres.',
  'Pick at least 2 evaluations to compare': 'Elegí al menos dos escenarios para comparar.',
  'At most 8 evaluations can be compared at once': 'Se pueden comparar hasta 8 escenarios a la vez.',
  'The same scenario cannot be compared with itself': 'Elegiste el mismo escenario dos veces.',
  'Every compared item needs a version': 'Elegí un escenario en cada fila.',
  'Every compared item needs a scenario': 'Elegí un escenario en cada fila.',
  'A saved comparison needs a name': 'Poné un nombre para guardar la comparación.',
  'Comparison name must be at most 120 characters': 'El nombre puede tener hasta 120 caracteres.',
  'Comparison not found': 'La comparación no existe o fue eliminada.',
  'Version not found': 'El escenario no existe o fue eliminado.',
  'Economic evaluation not found': 'La evaluación no existe o ya no está disponible.',
  'Economic job not found': 'La simulación no existe o ya no está disponible.',
  'Economic configuration not found': 'Este escenario todavía no tiene configuración económica.',
  // Scenarios of a version (the 404 is the same for an unknown, deleted, foreign or inactive one).
  // DELETE /version/{id} answers 409 when the version, or one below it, has simulations or saved comparisons.
  'La versión tiene simulaciones o comparaciones guardadas y no se puede eliminar.':
    'No se puede eliminar: esta versión, o alguna que deriva de ella, tiene simulaciones o comparaciones guardadas. Se conservan para no perder esos resultados.',
  'Scenario not found': 'El escenario no existe, fue eliminado o no pertenece a este proyecto.',
  'El nombre del escenario es obligatorio.': 'Escribí un nombre para el escenario.',
  'No se puede eliminar el único escenario de la versión.':
    'No se puede eliminar el único escenario de la versión: una versión siempre conserva al menos uno.',
};

const KNOWN_BACKEND_PREFIXES: [string, string][] = [
  // Scenarios: the backend already answers in Spanish with the name inside; these fix the wording the screens show.
  ['Ya existe un escenario llamado', 'Ya hay un escenario con ese nombre en esta versión. Elegí otro (no se distinguen mayúsculas).'],
  ['Una versión admite como máximo', 'Esta versión ya tiene el máximo de 20 escenarios. Eliminá alguno para crear otro.'],
  ['El escenario tiene una simulación en cola', 'El escenario tiene una simulación en cola o en curso. Esperá a que termine para eliminarlo.'],
  ['El escenario cambió desde otra pantalla', 'El escenario cambió desde otra pantalla. Tus cambios se conservan; recargá para no sobrescribir datos.'],
  ['Los valores de operación de los nodos', 'Los valores de operación se editan en cada nodo de la versión, no en el escenario. Usá «Editar operación en la versión».'],
  ['El nombre del escenario admite hasta', 'El nombre del escenario admite hasta 120 caracteres.'],
  ['Version not found with id:', 'La versión no existe o ya fue eliminada.'],
  ['Version conflict not found with id:', 'El conflicto no existe o ya fue resuelto.'],
  ['Node with id', 'El nodo ya no existe en esta versión.'],
  ['Node not found with id:', 'El nodo no existe o ya fue eliminado.'],
  ['Connection not found with id:', 'La conexión no existe o ya fue eliminada.'],
  ['Connection with id', 'La conexión ya no existe en esta versión.'],
  ['Node type mismatch: expected', 'El tipo de nodo no coincide con el esperado.'],
  ['ADMIN roles apply to the whole version', 'Ese rol aplica a toda la versión, no a una vertical.'],
  [
    'SIMULATOR roles apply to the whole version',
    'Ese rol aplica a toda la versión, no a una vertical.',
  ],
  ['Project permission required', 'No tenés permiso para realizar esta acción en el proyecto.'],
  ['Scenario changed since its last simulation:', 'Un escenario cambió desde su última simulación. Volvé a simularlo antes de comparar.'],
  ['Scenario has never been simulated:', 'Un escenario nunca se simuló. Simulalo antes de comparar.'],
  ['Relevance threshold must be', 'El umbral de relevancia tiene que ser mayor que 0.'],
  ['Rate threshold must be', 'El umbral para tasas tiene que ser mayor que 0.'],
  [
    'CSV header is missing required column(s):',
    'Al archivo le faltan columnas obligatorias. Descargá la plantilla para ver los nombres que acepta.',
  ],
];

// --- CSV import of nodes (POST /version/{id}/nodes/import) -------------------------------------------------------
// The importer answers one English message per failure. Row messages carry the record number (the header is record 1,
// so the first data row is 2) and are shown as "Línea N: ...". Matching is by regular expression; anything not
// recognised is handled by `describeCsvImportError`, which keeps the original text as the detail.

const CSV_KIND_LABELS: Record<string, string> = {
  NODE: 'nodo',
  CONNECTION: 'conexión',
  SCENARIO: 'escenario',
};

function csvKind(kind: string): string {
  return CSV_KIND_LABELS[kind] ?? kind;
}

const UUID = '[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}';

const CSV_ROW_RULES: [RegExp, (m: RegExpMatchArray) => string][] = [
  [
    new RegExp(`^Invalid CSV record (\\d+) \\(NODE\\): identity (${UUID}) already exists in this version$`),
    (m) =>
      `Línea ${m[1]}: la identidad ${m[2]} ya existe en esta versión. Este importador solo agrega nodos nuevos: ` +
      'quitá esa fila o dejá la columna identity vacía.',
  ],
  [
    new RegExp(`^Invalid CSV record (\\d+) \\(NODE\\): identity (${UUID}) is repeated in the file$`),
    (m) =>
      `Línea ${m[1]}: la identidad ${m[2]} aparece repetida en el archivo. Cada nodo debe tener una identidad única.`,
  ],
  [
    /^CSV record (\d+) has (\d+) fields; expected (\d+)$/,
    (m) => `Línea ${m[1]}: tiene ${m[2]} campos y el encabezado define ${m[3]}. Revisá las comas y las comillas.`,
  ],
  [/^CSV record (\d+) is missing nodeKind$/, (m) => `Línea ${m[1]}: falta el valor de nodeKind (el tipo de nodo).`],
  [/^CSV record (\d+) is missing recordType$/, (m) => `Línea ${m[1]}: falta el valor de recordType (NODE, CONNECTION o SCENARIO).`],
  [
    /^Unsupported nodeKind '(.*)' in CSV record (\d+)\. Supported values: (.*)$/,
    (m) => `Línea ${m[2]}: el tipo de nodo «${m[1]}» no existe. Valores admitidos: ${m[3]}.`,
  ],
  [
    /^Unsupported recordType '(.*)' in CSV record (\d+)\. Supported values: (.*)$/,
    (m) => `Línea ${m[2]}: el tipo de fila «${m[1]}» no existe. Valores admitidos: ${m[3]}.`,
  ],
  [
    /^CSV record (\d+) \((\w+)\) is missing required field '(.*)'$/,
    (m) => `Línea ${m[1]}: a la fila de ${csvKind(m[2])} le falta el campo obligatorio «${m[3]}».`,
  ],
  [
    /^CSV record (\d+) \((\w+)\) has a value in unsupported column '(.*)'$/,
    (m) => `Línea ${m[1]}: la columna «${m[3]}» no corresponde a un ${m[2]}. Dejala vacía en esa fila.`,
  ],
  [
    /^CSV record (\d+) \((\w+)\) must leave '(.*)' blank$/,
    (m) => `Línea ${m[1]}: en una fila de ${csvKind(m[2])} la columna «${m[3]}» debe quedar vacía.`,
  ],
  [/^CSV record (\d+) has duplicate node id '(.*)'$/, (m) => `Línea ${m[1]}: el id «${m[2]}» está repetido. Cada nodo necesita un id único dentro del archivo.`],
  [
    /^CSV record (\d+) \(CONNECTION\) references unknown node id '(.*)' in '(.*)'$/,
    (m) => `Línea ${m[1]}: la conexión usa «${m[2]}» en ${m[3]}, pero ningún nodo del archivo tiene ese id.`,
  ],
  [
    /^CSV record (\d+) column '(.*)' references unknown imported node id '(.*)' at (.*)$/,
    (m) => `Línea ${m[1]}: el escenario económico usa el nodo «${m[3]}» (${m[4]}), pero ningún nodo del archivo tiene ese id.`,
  ],
  [/^CSV record (\d+) has invalid JSON in column '(.*)'$/, (m) => `Línea ${m[1]}: la columna «${m[2]}» no contiene un JSON válido.`],
  [/^CSV record (\d+) column '(.*)' must contain a JSON object$/, (m) => `Línea ${m[1]}: la columna «${m[2]}» debe contener un objeto JSON.`],
  [/^CSV record (\d+) column 'identity' must contain a UUID$/, (m) => `Línea ${m[1]}: la columna identity debe contener un UUID válido o quedar vacía.`],
  [
    /^CSV record (\d+) column '(.*)' must contain a (?:valid|finite) number$/,
    (m) => `Línea ${m[1]}: la columna «${m[2]}» debe contener un número válido.`,
  ],
  [
    /^CSV record (\d+) \((\w+)\) has type\.nodeType '(.*)'; expected '(.*)'$/,
    (m) => `Línea ${m[1]}: type.nodeType es «${m[3]}» y para ${m[2]} debe ser «${m[4]}».`,
  ],
  [
    /^CSV record (\d+) \((\w+)\) has type\.vertical '(.*)'; expected '(.*)'$/,
    (m) => `Línea ${m[1]}: type.vertical es «${m[3]}» y para ${m[2]} debe ser «${m[4]}».`,
  ],
  [
    /^CSV record (\d+) \((\w+)\) has inconsistent node type metadata$/,
    (m) => `Línea ${m[1]}: el JSON de type no coincide con el tipo de nodo ${m[2]}.`,
  ],
  [/^CSV record (\d+) \((\w+)\) contains an invalid value: (.*)$/, (m) => `Línea ${m[1]}: la fila de ${m[2]} tiene un valor inválido (${m[3]}).`],
];

const CSV_ROW_CAUSE = /^Invalid CSV record (\d+) \((\w+)\): (.*)$/s;

// `NodeTypeEnum` derives the vertical and the structural role from the node type and rejects a different declared
// value: "<TYPE> is <ROLE>, not <declared>" / "<TYPE> belongs to <VERTICAL>, not <declared>". Same wording as the
// client-side check of the file (`checkNodeTypeCell`).
const NODE_TYPE_MISMATCH_RULES: [RegExp, (m: RegExpMatchArray) => string][] = [
  [
    /^Node role does not match its type: (\w+) is (\w+), not (\w+)$/,
    (m) => `${m[1]} debe tener el rol ${m[2]} (el archivo dice ${m[3]})`,
  ],
  [
    /^Node vertical does not match its type: (\w+) belongs to (\w+), not (\w+)$/,
    (m) => `${m[1]} debe tener la vertical ${m[2]} (el archivo dice ${m[3]})`,
  ],
];

function translateNodeTypeMismatch(message: string): string | null {
  for (const [pattern, build] of NODE_TYPE_MISMATCH_RULES) {
    const match = pattern.exec(message);
    if (match) {
      return build(match);
    }
  }
  return null;
}

const CSV_FILE_RULES: [RegExp, (m: RegExpMatchArray) => string][] = [
  [/^CSV file cannot be empty$/, () => 'El archivo está vacío.'],
  [/^CSV file must be 10 MB or smaller$/, () => 'El archivo supera el máximo de 10 MB.'],
  [/^CSV file must be valid UTF-8$/, () => 'El archivo tiene que estar guardado como CSV UTF-8.'],
  [/^CSV header row cannot be empty$/, () => 'La primera línea (encabezado) está vacía.'],
  [/^CSV headers cannot be blank$/, () => 'El encabezado tiene una columna sin nombre.'],
  [/^CSV headers must be unique$/, () => 'El encabezado repite el nombre de una columna.'],
  [/^CSV must contain a 'nodeKind' header$/, () => 'Al encabezado le falta la columna nodeKind.'],
  [/^CSV must contain a header and at least one data row$/, () => 'El archivo necesita un encabezado y al menos una fila de datos.'],
  [/^CSV must contain at least one NODE record$/, () => 'El archivo necesita al menos una fila de nodo (NODE).'],
  [/^CSV can contain only one SCENARIO record$/, () => 'El archivo puede tener una sola fila SCENARIO.'],
  [
    /^CSV records with economicProfile require one SCENARIO record with economicScenario$/,
    () => 'Hay nodos con economicProfile, pero falta la fila SCENARIO con economicScenario.',
  ],
  [/^Unknown CSV header\(s\): (.*)$/, (m) => `El encabezado tiene columnas que el importador no conoce: ${m[1]}.`],
  [/^Unexpected character after a quoted CSV field$/, () => 'Hay texto después de unas comillas de cierre. Revisá las comillas del archivo.'],
  [/^Quote inside an unquoted CSV field$/, () => 'Hay unas comillas dentro de un campo que no está entre comillas.'],
  [/^Unterminated quoted CSV field$/, () => 'Hay un campo entre comillas que nunca se cierra.'],
  [/^CSV exceeds the maximum of (\d+) rows$/, (m) => `El archivo supera el máximo de ${m[1]} filas de datos.`],
];

/** The Spanish text of a node-CSV import error, or null when the message is not one the importer is known to send. */
export function translateCsvImportMessage(message: string): string | null {
  for (const [pattern, build] of [...CSV_ROW_RULES, ...CSV_FILE_RULES]) {
    const match = pattern.exec(message);
    if (match) {
      return build(match);
    }
  }
  const mismatch = translateNodeTypeMismatch(message);
  if (mismatch) {
    return mismatch;
  }
  const cause = CSV_ROW_CAUSE.exec(message);
  if (cause) {
    // The sentence already names the node type, so it replaces the "(kind)" the generic wrapper would add.
    const typeMismatch = translateNodeTypeMismatch(cause[3]);
    if (typeMismatch) {
      return `Línea ${cause[1]}: ${typeMismatch}`;
    }
    // A rule of the saved node (e.g. a business rule) that failed on this row: keep its text, translated if known.
    const translated = translateBackendMessage(cause[3]);
    return `Línea ${cause[1]} (${csvKind(cause[2])}): ${translated}`;
  }
  return null;
}

/** The record number the message points at (header = 1), or null when it is about the file as a whole. */
export function csvImportErrorLine(message: string): number | null {
  const match = /(?:^|[^\w])CSV record (\d+)\b/.exec(message);
  return match ? Number(match[1]) : null;
}

export interface CsvImportErrorDescription {
  line: number | null;
  text: string;
  /** The backend's original text, kept when it could not be translated. */
  detail: string | null;
}

export function describeCsvImportError(message: string): CsvImportErrorDescription {
  const line = csvImportErrorLine(message);
  const text = translateCsvImportMessage(message);
  if (text !== null) {
    return { line, text, detail: null };
  }
  if (line !== null) {
    return { line, text: `Línea ${line}: la fila no es válida.`, detail: message };
  }
  return { line: null, text: 'El importador rechazó el archivo.', detail: message };
}

export function translateBackendMessage(message: string): string {
  const exact = KNOWN_BACKEND_MESSAGES[message];
  if (exact) {
    return exact;
  }
  const prefixed = KNOWN_BACKEND_PREFIXES.find(([prefix]) => message.startsWith(prefix));
  if (prefixed) {
    return prefixed[1];
  }
  return translateCsvImportMessage(message) ?? message;
}

const TRANSPORT_MESSAGES: RegExp[] = [
  /^Network Error$/,
  /^timeout of .* exceeded$/,
  /^Request failed with status code \d+$/,
];

function isTransportMessage(message: string): boolean {
  return TRANSPORT_MESSAGES.some((pattern) => pattern.test(message));
}

/**
 * Pulls a human-readable message out of a failed request, preferring the
 * backend's ApiResponse.message envelope and falling back to a default.
 */
export function getErrorMessage(error: unknown, fallback = 'Algo salió mal'): string {
  if (error instanceof AxiosError) {
    const data = error.response?.data as ApiResponse<unknown> | undefined;
    if (data?.message) {
      return translateBackendMessage(data.message);
    }
    if (!error.response || isTransportMessage(error.message)) {
      return fallback;
    }
    return error.message || fallback;
  }
  if (error instanceof Error) {
    return error.message || fallback;
  }
  return fallback;
}

export function getVersionRoleErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof AxiosError) {
    const data = error.response?.data as ApiResponse<unknown> | undefined;
    if (data?.message === 'Member not found') {
      return 'Esa persona ya no tiene roles en esta versión.';
    }
  }
  return getErrorMessage(error, fallback);
}

export function getErrorStatus(error: unknown): number | null {
  return error instanceof AxiosError ? (error.response?.status ?? null) : null;
}

/** The backend's own `ApiResponse.message` of a failed request, untranslated, or null when there is none. */
export function getBackendMessage(error: unknown): string | null {
  if (error instanceof AxiosError) {
    const data = error.response?.data as ApiResponse<unknown> | undefined;
    return data?.message ?? null;
  }
  return null;
}

export function isRequestCanceled(error: unknown): boolean {
  return error instanceof AxiosError && error.code === AxiosError.ERR_CANCELED;
}
