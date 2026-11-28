import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { scenariosApi } from '../../api/scenarios';
import { useActiveProject } from '../../hooks/useActiveProject';
import { useHomeScenarios } from '../../hooks/useHomeScenarios';
import { useShortScreen } from '../../hooks/useShortScreen';
import type { ProjectSummary } from '../../types/project';
import type { ScenarioSummary } from '../../types/scenario';
import { formatRelative } from '../../utils/date';
import {
  compareHint, homeScenarioRows, pendingLabel, pendingScenarios, scenarioChips, scenarioFooter, scenarioRowAction, scenarioRowLimit,
} from '../../utils/homeScenarios';
import { createBlockedReason, scenarioPermissions } from '../../utils/scenarioRules';
import { scenarioConfigurationPath, versionScenariosPath } from '../../utils/scenarioRoutes';
import { SimulationDialog } from '../economics/SimulationDialog';
import { ScenarioNameDialog } from '../scenarios/ScenarioDialogs';
import { ScenarioStatusChip } from '../scenarios/ScenarioStatusChip';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { ButtonLink } from '../ui/ButtonLink';
import { Card } from '../ui/Card';
import { EmptyState } from '../ui/EmptyState';
import { ArrowRightIcon } from '../ui/icons';
import { LoadingState } from '../ui/LoadingState';

interface ScenariosPanelProps {
  project: ProjectSummary;
  /** Whether the person can reach Comparaciones (the sidebar's rule); the "Comparar" button follows it. */
  canCompare: boolean;
  className?: string;
}

const LINK_CLASSES =
  'inline-flex shrink-0 items-center gap-1 rounded text-sm font-medium text-brand-800 underline ' +
  'underline-offset-2 hover:text-brand-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-focus';

const FOOTER_CLASSES = 'flex items-center justify-between gap-4 border-t border-ink-100 px-6 py-3 text-sm text-ink-500';

/**
 * The scenarios of the recent project's root version: name, status chips, relative date and one action per row, then
 * a footer with the next step. Rows sit at the top at their natural height; the card has its own height (a minimum of
 * about three rows, so nothing jumps while it loads). It loads on its own: the rest of Inicio never waits for it.
 */
export function ScenariosPanel({ project, canCompare, className = '' }: ScenariosPanelProps) {
  const { selectProject } = useActiveProject();
  const navigate = useNavigate();
  const short = useShortScreen();
  const { state, access, lastComparison, retry } = useHomeScenarios(project.id, canCompare);
  const [simulating, setSimulating] = useState<ScenarioSummary | null>(null);
  const [creating, setCreating] = useState(false);

  const version = state.status === 'ready' ? state.version : null;
  const all = useMemo(() => (state.status === 'ready' ? state.scenarios : []), [state]);
  const permissions = useMemo(() => scenarioPermissions(access, version?.id), [access, version?.id]);
  const { rows } = useMemo(() => homeScenarioRows(all, scenarioRowLimit(short)), [all, short]);
  const canCreate = version !== null && createBlockedReason(permissions, all.length) === null;
  const footer = scenarioFooter({
    total: all.length, rows: rows.length, pending: pendingScenarios(all), canCreate, hasLastComparison: lastComparison !== null,
  });
  const allPath = version ? versionScenariosPath(project.id, version.id) : null;
  const compare = compareHint(all.length);

  return (
    <Card padded={false} className={`@container flex min-h-52 min-w-0 flex-col ${className}`}>
      <div className="flex items-center justify-between gap-3 px-6 pb-2 pt-5 [@media(max-height:760px)]:pt-4">
        <div className="min-w-0">
          <h2 className="text-lg font-semibold text-ink-800">Escenarios</h2>
          <p className="truncate text-sm text-ink-500" title={project.name}>
            {project.name}
          </p>
        </div>
        <div className="flex shrink-0 items-center gap-3">
          {canCompare && (
            <ButtonLink
              variant="outline"
              size="compact"
              to={`/projects/${encodeURIComponent(project.id)}/comparisons`}
              onClick={() => selectProject(project.id)}
              title={state.status === 'ready' ? compare : undefined}
              aria-label={state.status === 'ready' && compare ? `Comparar. ${compare}` : undefined}
            >
              Comparar
            </ButtonLink>
          )}
          {allPath && (
            <Link to={allPath} onClick={() => selectProject(project.id)} className={LINK_CLASSES}>
              Ver todos
              <ArrowRightIcon className="h-3.5 w-3.5" />
            </Link>
          )}
        </div>
      </div>

      {state.status === 'loading' ? (
        <LoadingState className="my-auto">Cargando escenarios…</LoadingState>
      ) : state.status === 'error' ? (
        <div className="px-6 pb-6">
          <Alert tone="error">{state.message}</Alert>
          <Button variant="secondary" size="compact" className="mt-4" onClick={retry}>
            Reintentar
          </Button>
        </div>
      ) : !version ? (
        <EmptyState className="my-auto">No hay escenarios visibles en este proyecto.</EmptyState>
      ) : rows.length === 0 ? (
        <EmptyState
          className="my-auto"
          action={canCreate ? <Button size="compact" onClick={() => setCreating(true)}>Crear escenario</Button> : undefined}
        >
          Todavía no hay escenarios
        </EmptyState>
      ) : (
        <>
          <ul className="px-3 pb-3">
            {rows.map((scenario) => (
              <ScenarioRow
                key={scenario.id}
                scenario={scenario}
                action={scenarioRowAction(scenario, permissions, { projectId: project.id, versionId: version.id })}
                onSimulate={() => setSimulating(scenario)}
                onNavigate={() => selectProject(project.id)}
              />
            ))}
          </ul>
          {footer?.kind === 'add' && (
            <div className={FOOTER_CLASSES}>
              <span>Agregá otro escenario para poder compararlos</span>
              {footer.canCreate && (
                <Button variant="outline" size="compact" className="shrink-0" onClick={() => setCreating(true)}>
                  Nuevo escenario
                </Button>
              )}
            </div>
          )}
          {footer?.kind === 'pending' && allPath && (
            <div className={FOOTER_CLASSES}>
              <span>{pendingLabel(footer.count)}</span>
              <Link to={allPath} onClick={() => selectProject(project.id)} className={LINK_CLASSES}>
                Ver todos
                <ArrowRightIcon className="h-3.5 w-3.5" />
              </Link>
            </div>
          )}
          {footer?.kind === 'comparison' && lastComparison && (
            <div className={FOOTER_CLASSES}>
              <Link
                to={`/projects/${encodeURIComponent(project.id)}/comparisons/${encodeURIComponent(lastComparison.id)}`}
                onClick={() => selectProject(project.id)}
                title={lastComparison.name}
                className="min-w-0 truncate rounded focus:outline-none focus-visible:ring-2 focus-visible:ring-focus"
              >
                Última comparación: <span className="font-medium text-brand-800 underline underline-offset-2">{lastComparison.name}</span>
              </Link>
            </div>
          )}
        </>
      )}

      {simulating && version && (
        <SimulationDialog
          projectId={project.id}
          versionId={version.id}
          scenarioId={simulating.id}
          name={simulating.name}
          onClose={() => setSimulating(null)}
        />
      )}
      {version && (
        <ScenarioNameDialog
          open={creating}
          title="Nuevo escenario"
          submitLabel="Crear escenario"
          description="Se crea vacío, sobre esta versión. Después lo configurás."
          scenarios={all}
          onClose={() => setCreating(false)}
          onSubmit={async (name) => {
            const created = await scenariosApi.create(project.id, version.id, name);
            selectProject(project.id);
            navigate(scenarioConfigurationPath(project.id, version.id, created.id));
          }}
        />
      )}
    </Card>
  );
}

function ScenarioRow({
  scenario,
  action,
  onSimulate,
  onNavigate,
}: {
  scenario: ScenarioSummary;
  action: ReturnType<typeof scenarioRowAction>;
  onSimulate: () => void;
  onNavigate: () => void;
}) {
  const chips = scenarioChips(scenario);
  const when = formatRelative(scenario.lastModified);
  return (
    <li className="flex min-h-12 items-center gap-3 border-t border-ink-100 px-3 py-1 first:border-t-0">
      <span className="min-w-0 flex-1 truncate text-sm font-semibold text-ink-800" title={scenario.name}>
        {scenario.name}
      </span>
      <span className="flex shrink-0 items-center gap-1.5">
        {chips.map((chip) => (
          <ScenarioStatusChip
            key={chip.id}
            dot
            label={chip.label}
            tone={chip.tone}
            hint={chip.hint}
            className={chip.essential ? '' : 'hidden @xl:inline-flex'}
          />
        ))}
      </span>
      {when && <span className="hidden w-20 shrink-0 text-right text-xs text-ink-500 @md:block">{when}</span>}
      <span className="flex w-20 shrink-0 justify-end">
        {action.kind === 'simulate' && (
          <Button size="compact" onClick={onSimulate} aria-label={`Simular ${scenario.name}`}>
            Simular
          </Button>
        )}
        {action.kind === 'view' && (
          <ButtonLink variant="link" to={action.to} onClick={onNavigate} className="px-2 py-1" aria-label={`Ver ${scenario.name}`}>
            Ver
          </ButtonLink>
        )}
      </span>
    </li>
  );
}
