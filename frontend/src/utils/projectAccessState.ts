import type { ProjectAccess } from '../types/versionAccess';

/**
 * Cached `/projects/{id}/access` for the one project the app is looking at.
 * Pure state machine; the provider only feeds it events.
 */
export interface ProjectAccessState {
  projectId: string | null;
  access: ProjectAccess | null;
  /** Message of a non-403 failure; null otherwise. */
  error: string | null;
  forbidden: boolean;
  /** An answer (data or failure) exists for `projectId`. Stays true across invalidations. */
  loaded: boolean;
  /** The answer reflects the latest known server state. */
  fresh: boolean;
  /** Only a response to this request may be applied. */
  latestRequest: number | null;
  /** Bumped on every invalidation so mounted screens know to refetch. */
  generation: number;
}

export type ProjectAccessAction =
  | { type: 'project'; projectId: string }
  | { type: 'requested'; projectId: string; request: number }
  | { type: 'succeeded'; projectId: string; request: number; access: ProjectAccess | null }
  | { type: 'failed'; projectId: string; request: number; status: number | null; message: string }
  /** `projectId` omitted: the mutation does not say which project, so invalidate whatever is cached. */
  | { type: 'invalidate'; projectId?: string };

export const INITIAL_PROJECT_ACCESS_STATE: ProjectAccessState = {
  projectId: null,
  access: null,
  error: null,
  forbidden: false,
  loaded: false,
  fresh: false,
  latestRequest: null,
  generation: 0,
};

export function projectAccessReducer(
  state: ProjectAccessState,
  action: ProjectAccessAction,
): ProjectAccessState {
  switch (action.type) {
    case 'project':
      if (state.projectId === action.projectId) {
        return state;
      }
      return { ...INITIAL_PROJECT_ACCESS_STATE, projectId: action.projectId, generation: state.generation };
    case 'requested':
      if (state.projectId !== action.projectId) {
        return state;
      }
      return { ...state, latestRequest: action.request };
    case 'succeeded':
      if (state.projectId !== action.projectId || state.latestRequest !== action.request) {
        return state;
      }
      return {
        ...state,
        access: action.access,
        error: null,
        forbidden: false,
        loaded: true,
        fresh: true,
        latestRequest: null,
      };
    case 'failed': {
      if (state.projectId !== action.projectId || state.latestRequest !== action.request) {
        return state;
      }
      const forbidden = action.status === 403;
      return {
        ...state,
        access: null,
        error: forbidden ? null : action.message,
        forbidden,
        loaded: true,
        // A 403 is an answer and is kept until retry, invalidation or a project change;
        // any other failure is retried the next time a screen asks.
        fresh: forbidden,
        latestRequest: null,
      };
    }
    case 'invalidate':
      if (action.projectId !== undefined && state.projectId !== action.projectId) {
        return state;
      }
      if (state.projectId === null) {
        return state;
      }
      // Dropping latestRequest discards a response that was already in flight: it may predate the change.
      return { ...state, fresh: false, latestRequest: null, generation: state.generation + 1 };
  }
}

/** Whether a screen asking for `projectId` has to start a request. */
export function needsProjectAccessLoad(
  state: ProjectAccessState,
  projectId: string,
  requestInFlightFor: string | null,
): boolean {
  if (requestInFlightFor === projectId) {
    return false;
  }
  return state.projectId !== projectId || !state.fresh;
}

export interface ProjectAccessView {
  access: ProjectAccess | null;
  loading: boolean;
  error: string | null;
  forbidden: boolean;
}

/** What a screen sees for `projectId`: nothing from another project, loading until there is an answer. */
export function projectAccessView(
  state: ProjectAccessState,
  projectId: string | undefined,
): ProjectAccessView {
  if (projectId === undefined) {
    return { access: null, loading: false, error: null, forbidden: false };
  }
  if (state.projectId !== projectId || !state.loaded) {
    return { access: null, loading: true, error: null, forbidden: false };
  }
  return { access: state.access, loading: false, error: state.error, forbidden: state.forbidden };
}
