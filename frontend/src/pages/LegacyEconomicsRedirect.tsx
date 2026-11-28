import { useEffect, useState } from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import { getErrorMessage } from '../api/errors';
import { scenariosApi } from '../api/scenarios';
import { Alert } from '../components/ui/Alert';
import { defaultScenarioOf } from '../utils/scenarioRules';
import { legacyEconomicsTarget, parseLegacyEconomicsPath, projectVersionsPath, versionScenariosPath } from '../utils/scenarioRoutes';

/**
 * The version-level economics routes of earlier releases (`/versions/:v/economics/...`) now lead to the version's
 * default scenario, so bookmarks and links keep working.
 */
export function LegacyEconomicsRedirect() {
  const { pathname } = useLocation();
  const [resolved, setResolved] = useState<{ path: string; target: string | null; error: string } | null>(null);
  const route = parseLegacyEconomicsPath(pathname);

  useEffect(() => {
    const parsed = parseLegacyEconomicsPath(pathname);
    if (!parsed) return;
    let cancelled = false;
    scenariosApi.list(parsed.projectId, parsed.versionId)
      .then(scenarios => {
        if (cancelled) return;
        const fallback = defaultScenarioOf(scenarios);
        setResolved({ path: pathname, error: '', target: fallback ? legacyEconomicsTarget(parsed, fallback.id) : versionScenariosPath(parsed.projectId, parsed.versionId) });
      })
      .catch(failure => { if (!cancelled) setResolved({ path: pathname, target: null, error: getErrorMessage(failure, 'No se pudo abrir el escenario.') }); });
    return () => { cancelled = true; };
  }, [pathname]);

  if (!route) return <Navigate replace to="/" />;
  const current = resolved?.path === pathname ? resolved : null;
  if (current?.target) return <Navigate replace to={current.target} />;
  if (current?.error) {
    return <Alert tone="error">{current.error} <Link className="underline" to={projectVersionsPath(route.projectId)}>Volver a las versiones</Link></Alert>;
  }
  return <p role="status">Abriendo el escenario…</p>;
}
