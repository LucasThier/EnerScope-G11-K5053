import { useCallback, useEffect, useMemo, useReducer, useRef, type ReactNode } from 'react';
import { versionAccessApi } from '../api/versionAccess';
import { onAccessInvalidated } from '../api/accessEvents';
import { getErrorMessage, getErrorStatus } from '../api/errors';
import {
  INITIAL_PROJECT_ACCESS_STATE,
  needsProjectAccessLoad,
  projectAccessReducer,
} from '../utils/projectAccessState';
import { ProjectAccessCacheContext, type ProjectAccessCacheValue } from './useProjectAccessCache';

/**
 * Keeps the `/projects/{id}/access` answer of the project being viewed, so the
 * screens that need it stop asking again on every navigation. It only fetches
 * when a screen asks (see `useGuardedProjectAccess`), never on its own.
 */
export function ProjectAccessProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(projectAccessReducer, INITIAL_PROJECT_ACCESS_STATE);
  const stateRef = useRef(state);
  stateRef.current = state;
  const requestSeq = useRef(0);
  const inFlight = useRef<string | null>(null);

  const load = useCallback(async (projectId: string) => {
    const request = ++requestSeq.current;
    inFlight.current = projectId;
    dispatch({ type: 'project', projectId });
    dispatch({ type: 'requested', projectId, request });
    try {
      const res = await versionAccessApi.projectAccess(projectId);
      dispatch({ type: 'succeeded', projectId, request, access: res.data.data ?? null });
    } catch (err) {
      dispatch({
        type: 'failed',
        projectId,
        request,
        status: getErrorStatus(err),
        message: getErrorMessage(err, 'No se pudieron cargar los accesos'),
      });
    } finally {
      if (requestSeq.current === request) {
        inFlight.current = null;
      }
    }
  }, []);

  const ensure = useCallback(
    (projectId: string) => {
      if (needsProjectAccessLoad(stateRef.current, projectId, inFlight.current)) {
        void load(projectId);
      }
    },
    [load],
  );

  useEffect(
    () =>
      onAccessInvalidated((projectId) => {
        if (projectId === undefined || projectId === stateRef.current.projectId) {
          inFlight.current = null;
          dispatch({ type: 'invalidate', projectId });
        }
      }),
    [],
  );

  const value = useMemo<ProjectAccessCacheValue>(
    () => ({ state, ensure, reload: load }),
    [state, ensure, load],
  );

  return (
    <ProjectAccessCacheContext.Provider value={value}>{children}</ProjectAccessCacheContext.Provider>
  );
}
