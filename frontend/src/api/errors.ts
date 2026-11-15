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
};

const KNOWN_BACKEND_PREFIXES: [string, string][] = [
  ['Node vertical does not match its type:', 'La vertical del nodo no corresponde a su tipo.'],
  ['Version not found with id:', 'La versión no existe o ya fue eliminada.'],
  ['Version conflict not found with id:', 'El conflicto no existe o ya fue resuelto.'],
  ['Node with id', 'El nodo ya no existe en esta versión.'],
  ['ADMIN roles apply to the whole version', 'Ese rol aplica a toda la versión, no a una vertical.'],
  [
    'SIMULATOR roles apply to the whole version',
    'Ese rol aplica a toda la versión, no a una vertical.',
  ],
  [
    'CSV header is missing required column(s):',
    'Al archivo le faltan columnas obligatorias. Descargá la plantilla para ver los nombres que acepta.',
  ],
];

export function translateBackendMessage(message: string): string {
  const exact = KNOWN_BACKEND_MESSAGES[message];
  if (exact) {
    return exact;
  }
  const prefixed = KNOWN_BACKEND_PREFIXES.find(([prefix]) => message.startsWith(prefix));
  return prefixed ? prefixed[1] : message;
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
