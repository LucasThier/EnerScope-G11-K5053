import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { simulationsApi } from '../../api/simulations';
import { getErrorMessage } from '../../api/errors';
import type { VersionSummary } from '../../types/diagram';
import type { SimulationSummary } from '../../types/simulation';
import { formatCompact, formatDateTime, formatMoney } from '../../utils/number';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { Card } from '../ui/Card';
import { AlertIcon, EyeIcon, FlaskIcon, TrashIcon } from '../ui/icons';
import { RowButton } from '../ui/RowButton';
import { Spinner } from '../ui/Spinner';
import { WARNING_MESSAGES } from './simulationCopy';

interface SimulationRunsPanelProps {
  projectId: string;
  version: VersionSummary;
  /** Changes whenever the caller knows the runs changed (e.g. after simulating). */
  refreshKey: number;
  onSimulate: () => void;
  /** After a run is deleted the version's counts change too. */
  onChanged: () => void;
}

const headerCell = 'px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-ink-500';
const bodyCell = 'px-3 py-2 text-sm text-ink-700';

/** The simulation runs of one scenario, newest first, each linking to its results. */
export function SimulationRunsPanel({ projectId, version, refreshKey, onSimulate, onChanged }: SimulationRunsPanelProps) {
  const [runs, setRuns] = useState<SimulationSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      const res = await simulationsApi.list(version.id);
      setRuns(res.data.data ?? []);
    } catch (err) {
      setError(getErrorMessage(err, 'No se pudieron cargar las simulaciones'));
      setRuns([]);
    }
  }, [version.id]);

  useEffect(() => {
    setRuns(null);
    void load();
  }, [load, refreshKey]);

  async function remove(run: SimulationSummary) {
    try {
      await simulationsApi.remove(version.id, run.id);
      await load();
      onChanged();
    } catch (err) {
      setError(getErrorMessage(err, 'No se pudo eliminar la simulación'));
    }
  }

  return (
    <Card padded={false}>
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-ink-100 px-4 py-3">
        <div>
          <h2 className="text-base font-semibold text-ink-800">Simulaciones de {version.name}</h2>
          <p className="text-xs text-ink-500">Cada corrida guarda la producción y los costos del modelo de ese momento.</p>
        </div>
        <Button onClick={onSimulate} disabled={version.nodeCount === 0} className="text-sm">
          <FlaskIcon className="h-4 w-4" />
          Simular
        </Button>
      </div>
      {error && <Alert tone="error" className="m-4">{error}</Alert>}
      {runs === null ? (
        <div className="flex justify-center py-6 text-brand-800">
          <Spinner className="h-5 w-5" />
        </div>
      ) : runs.length === 0 ? (
        <p className="px-4 py-6 text-sm text-ink-500">
          {version.nodeCount === 0
            ? 'Este escenario no tiene nodos todavía. Modelalo en el editor y después simulalo.'
            : 'Todavía no se simuló este escenario.'}
        </p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full border-collapse">
            <thead>
              <tr className="border-b border-ink-100">
                <th scope="col" className={headerCell}>Corrida</th>
                <th scope="col" className={`${headerCell} text-right`}>Entregado (P50)</th>
                <th scope="col" className={`${headerCell} text-right`}>Costo total</th>
                <th scope="col" className={`${headerCell} text-right`}>Costo unitario</th>
                <th scope="col" className={`${headerCell} text-right`}>Acciones</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-ink-100">
              {runs.map((run) => (
                <tr key={run.id}>
                  <td className={`${bodyCell} whitespace-nowrap`}>
                    <span className="flex items-center gap-1">
                      {formatDateTime(run.createdAt)}
                      {run.warnings.length > 0 && (
                        <span
                          className="text-danger-600"
                          title={run.warnings.map((w) => WARNING_MESSAGES[w]).join('\n')}
                        >
                          <AlertIcon className="h-4 w-4" />
                          <span className="sr-only">Con advertencias</span>
                        </span>
                      )}
                    </span>
                    <span className="block text-xs text-ink-500">
                      {run.years} años · {run.rounds} rondas
                    </span>
                  </td>
                  <td className={`${bodyCell} whitespace-nowrap text-right tabular-nums`}>{formatCompact(run.kpis.deliveredP50)}</td>
                  <td className={`${bodyCell} whitespace-nowrap text-right tabular-nums`}>{formatMoney(run.kpis.totalCost, true)}</td>
                  <td className={`${bodyCell} whitespace-nowrap text-right tabular-nums`}>{formatMoney(run.kpis.unitCost)}</td>
                  <td className={`${bodyCell} text-right`}>
                    <div className="flex justify-end gap-1">
                      <Link
                        to={`/projects/${projectId}/scenarios/${version.id}/runs/${run.id}`}
                        aria-label={`Ver resultados de la simulación del ${formatDateTime(run.createdAt)}`}
                        title="Ver resultados"
                        className="inline-flex rounded-lg p-1 text-ink-500 hover:bg-ink-50 hover:text-ink-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-400"
                      >
                        <EyeIcon className="h-4 w-4" />
                      </Link>
                      <RowButton
                        label={`Eliminar la simulación del ${formatDateTime(run.createdAt)}`}
                        title="Eliminar"
                        onClick={() => void remove(run)}
                      >
                        <TrashIcon className="h-4 w-4" />
                      </RowButton>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  );
}
