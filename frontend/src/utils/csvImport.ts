import type { OrganizationMemberType } from '../types/auth';

export const MAX_ROWS = 100;
export const MAX_FILE_BYTES = 1024 * 1024;
const NAME_MIN = 2;
const NAME_MAX = 60;
const QUOTE = '"';
const BOM = '\uFEFF';
const MAIL_PATTERN = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
const FORMULA_STARTS = ['=', '+', '-', '@', '\t', '\r'];

const MAIL_HEADERS = ['mail', 'email', 'e-mail', 'correo'];
const FIRST_NAME_HEADERS = ['firstname', 'first_name', 'first name', 'nombre'];
const LAST_NAME_HEADERS = ['lastname', 'last_name', 'last name', 'apellido'];
const ROLE_HEADERS = ['role', 'rol', 'membertype', 'member_type', 'member type', 'tipo'];

const COLUMN_LABELS: Record<ColumnKey, string> = {
  mail: 'email',
  firstName: 'nombre',
  lastName: 'apellido',
};

type ColumnKey = 'mail' | 'firstName' | 'lastName';

export interface ImportRow {
  line: number;
  mail: string;
  firstName: string;
  lastName: string;
  role: OrganizationMemberType | null;
  error: string | null;
}

export interface ParsedImport {
  rows: ImportRow[];
  validCount: number;
  invalidCount: number;
  fileError: string | null;
  delimiter: ',' | ';';
  hasRoleColumn: boolean;
}

interface RawRecord {
  line: number;
  fields: string[];
}

function detectDelimiter(content: string): ',' | ';' {
  const firstLine = content.split('\n').find((line) => line.trim() !== '') ?? '';
  let commas = 0;
  let semicolons = 0;
  let inQuotes = false;
  for (const char of firstLine) {
    if (char === QUOTE) {
      inQuotes = !inQuotes;
    } else if (!inQuotes && char === ',') {
      commas += 1;
    } else if (!inQuotes && char === ';') {
      semicolons += 1;
    }
  }
  return semicolons > commas ? ';' : ',';
}

function parseRecords(content: string, delimiter: string): RawRecord[] {
  const records: RawRecord[] = [];
  let fields: string[] = [];
  let field = '';
  let inQuotes = false;
  let line = 1;
  let recordStart = 1;

  function closeRecord() {
    fields.push(field.trim());
    field = '';
    if (!fields.every((value) => value === '')) {
      records.push({ line: recordStart, fields });
    }
    fields = [];
  }

  for (let i = 0; i < content.length; i += 1) {
    const char = content[i];

    if (inQuotes) {
      if (char === QUOTE) {
        if (content[i + 1] === QUOTE) {
          field += QUOTE;
          i += 1;
        } else {
          inQuotes = false;
        }
      } else {
        if (char === '\n') {
          line += 1;
        }
        field += char;
      }
      continue;
    }

    if (char === QUOTE) {
      inQuotes = true;
    } else if (char === delimiter) {
      fields.push(field.trim());
      field = '';
    } else if (char === '\n') {
      closeRecord();
      line += 1;
      recordStart = line;
    } else {
      field += char;
    }
  }

  closeRecord();
  return records;
}

export function normalizeCsvText(raw: string): string {
  const withoutBom = raw.startsWith(BOM) ? raw.slice(BOM.length) : raw;
  return withoutBom.replace(/\r\n?/g, '\n');
}

function resolveColumns(header: string[]) {
  const keys = header.map((cell) => cell.trim().toLowerCase());
  let mail: number | null = null;
  let firstName: number | null = null;
  let lastName: number | null = null;
  let role: number | null = null;

  keys.forEach((key, index) => {
    if (mail === null && MAIL_HEADERS.includes(key)) {
      mail = index;
    } else if (firstName === null && FIRST_NAME_HEADERS.includes(key)) {
      firstName = index;
    } else if (lastName === null && LAST_NAME_HEADERS.includes(key)) {
      lastName = index;
    } else if (role === null && ROLE_HEADERS.includes(key)) {
      role = index;
    }
  });

  return { mail, firstName, lastName, role };
}

function cell(fields: string[], index: number | null): string {
  if (index === null || index >= fields.length) {
    return '';
  }
  return fields[index].trim();
}

function parseRole(value: string): OrganizationMemberType | null | 'invalid' {
  if (value === '') {
    return null;
  }
  const upper = value.toUpperCase();
  if (upper === 'OWNER' || upper === 'MEMBER') {
    return upper;
  }
  return 'invalid';
}

function invalidName(value: string): boolean {
  return value.length < NAME_MIN || value.length > NAME_MAX;
}

export function parseImportCsv(raw: string): ParsedImport {
  const content = normalizeCsvText(raw);
  const delimiter = detectDelimiter(content);
  const records = parseRecords(content, delimiter);

  const empty: ParsedImport = {
    rows: [],
    validCount: 0,
    invalidCount: 0,
    fileError: null,
    delimiter,
    hasRoleColumn: false,
  };

  if (records.length === 0) {
    return { ...empty, fileError: 'El archivo no tiene filas.' };
  }

  const columns = resolveColumns(records[0].fields);
  const missing = (['mail', 'firstName', 'lastName'] as ColumnKey[]).filter(
    (key) => columns[key] === null,
  );
  if (missing.length > 0) {
    const labels = missing.map((key) => COLUMN_LABELS[key]).join(', ');
    return {
      ...empty,
      fileError:
        `Al archivo le faltan columnas obligatorias: ${labels}. ` +
        'Descargá la plantilla para ver los nombres que acepta.',
    };
  }

  if (records.length === 1) {
    return {
      ...empty,
      hasRoleColumn: columns.role !== null,
      fileError: 'El archivo sólo tiene el encabezado: no hay filas para importar.',
    };
  }

  const seenMails = new Set<string>();
  const rows: ImportRow[] = records.slice(1).map((record) => {
    const mail = cell(record.fields, columns.mail);
    const firstName = cell(record.fields, columns.firstName);
    const lastName = cell(record.fields, columns.lastName);
    const rawRole = cell(record.fields, columns.role);
    const role = parseRole(rawRole);

    let error: string | null = null;
    if (mail === '') {
      error = 'Falta el email.';
    } else if (!MAIL_PATTERN.test(mail)) {
      error = 'El email no tiene un formato válido.';
    } else if (seenMails.has(mail.toLowerCase())) {
      error = 'Ese email está repetido en el archivo.';
    } else if (invalidName(firstName)) {
      error =
        firstName === ''
          ? 'Falta el nombre.'
          : `El nombre debe tener entre ${NAME_MIN} y ${NAME_MAX} caracteres.`;
    } else if (invalidName(lastName)) {
      error =
        lastName === ''
          ? 'Falta el apellido.'
          : `El apellido debe tener entre ${NAME_MIN} y ${NAME_MAX} caracteres.`;
    } else if (role === 'invalid') {
      error = `«${rawRole}» no es un rol válido: usá Propietario (OWNER) o Miembro (MEMBER).`;
    }

    if (error === null) {
      seenMails.add(mail.toLowerCase());
    }

    return {
      line: record.line,
      mail,
      firstName,
      lastName,
      role: role === 'invalid' ? null : role,
      error,
    };
  });

  const invalidCount = rows.filter((row) => row.error !== null).length;

  return {
    rows,
    validCount: rows.length - invalidCount,
    invalidCount,
    fileError: null,
    delimiter,
    hasRoleColumn: columns.role !== null,
  };
}

function escapeField(value: string, guardFormulas: boolean): string {
  let field = value;
  if (guardFormulas && field !== '' && FORMULA_STARTS.includes(field[0])) {
    field = `'${field}`;
  }
  if (/["\n\r,;]/.test(field)) {
    return `"${field.replace(/"/g, '""')}"`;
  }
  return field;
}

function toCsv(rows: string[][], guardFormulas: boolean): string {
  return rows
    .map((row) => row.map((value) => escapeField(value, guardFormulas)).join(','))
    .join('\r\n')
    .concat('\r\n');
}

export function buildNormalizedCsv(rows: ImportRow[]): string {
  const valid = rows.filter((row) => row.error === null);
  const body = valid.map((row) => [row.mail, row.firstName, row.lastName, row.role ?? '']);
  return toCsv([['mail', 'firstName', 'lastName', 'role'], ...body], false);
}

export function buildTemplateCsv(): string {
  return (
    BOM +
    toCsv(
      [
        ['mail', 'firstName', 'lastName', 'role'],
        ['ana.perez@ejemplo.com', 'Ana', 'Pérez', 'MEMBER'],
        ['luis.gomez@ejemplo.com', 'Luis', 'Gómez', 'OWNER'],
      ],
      true,
    )
  );
}

export function withBom(content: string): string {
  return content.startsWith(BOM) ? content : BOM + content;
}

export function downloadTextFile(name: string, content: string): void {
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = name;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

export function timestampedName(prefix: string): string {
  const now = new Date();
  const pad = (value: number) => String(value).padStart(2, '0');
  const stamp =
    `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}` +
    `-${pad(now.getHours())}${pad(now.getMinutes())}`;
  return `${prefix}-${stamp}.csv`;
}
