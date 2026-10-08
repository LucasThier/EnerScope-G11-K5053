import { AxiosError } from 'axios';
import type { ApiResponse } from '../types/auth';

const KNOWN_BACKEND_MESSAGES: Record<string, string> = {
  'The project would be left without an administrator':
    'El proyecto no puede quedarse sin administrador. Asigná primero el rol de Administrador a otra persona.',
  'User is already a member of this project': 'Esa persona ya integra el proyecto.',
  "User is not a member of the project's organization":
    'Esa persona no pertenece a la organización del proyecto.',
  'User is not active': 'La cuenta de esa persona está desactivada.',
  'User not found': 'No se encontró la cuenta.',
  'Member not found': 'Esa persona ya no integra el proyecto.',
  'Project not found': 'El proyecto no existe o fue eliminado.',
  'You are not allowed to manage this project':
    'No tenés permiso para administrar los integrantes de este proyecto.',
  'You are not allowed to view this project': 'No tenés acceso a este proyecto.',
};

function translate(message: string): string {
  return KNOWN_BACKEND_MESSAGES[message] ?? message;
}

/**
 * Pulls a human-readable message out of a failed request, preferring the
 * backend's ApiResponse.message envelope and falling back to a default.
 */
export function getErrorMessage(error: unknown, fallback = 'Algo salió mal'): string {
  if (error instanceof AxiosError) {
    const data = error.response?.data as ApiResponse<unknown> | undefined;
    if (data?.message) {
      return translate(data.message);
    }
    return error.message || fallback;
  }
  if (error instanceof Error) {
    return error.message || fallback;
  }
  return fallback;
}

export function getErrorStatus(error: unknown): number | null {
  return error instanceof AxiosError ? (error.response?.status ?? null) : null;
}
