import { AxiosError } from 'axios';
import type { ApiResponse } from '../types/auth';

/**
 * Pulls a human-readable message out of a failed request, preferring the
 * backend's ApiResponse.message envelope and falling back to a default.
 */
export function getErrorMessage(error: unknown, fallback = 'Something went wrong'): string {
  if (error instanceof AxiosError) {
    const data = error.response?.data as ApiResponse<unknown> | undefined;
    if (data?.message) {
      return data.message;
    }
    return error.message || fallback;
  }
  if (error instanceof Error) {
    return error.message || fallback;
  }
  return fallback;
}
