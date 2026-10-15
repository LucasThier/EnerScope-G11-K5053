import { useEffect, useState } from 'react';
import { simulationsApi } from '../../api/simulations';
import { getErrorMessage } from '../../api/errors';
import type { VersionSummary } from '../../types/diagram';
import type { CostEstimate } from '../../types/simulation';
import { formatMoney } from '../../utils/number';
import { Alert } from '../ui/Alert';
import { Card } from '../ui/Card';
import { Spinner } from '../ui/Spinner';
import { controlClasses } from '../ui/controlClasses';
import { VERTICAL_LABELS } from '../editor/nodeCatalog';

const HORIZONS = [5, 10, 20, 30];

/**
 * The cost of the scenario's current model over a horizon, computed on the spot
 * without simulating — what the model costs while it is still being drawn.
 */
export function CostEstimateCard({ version, refreshKey }: { version: VersionSummary; refreshKey: number }) {
  const [years, setYears] = useState(20);
  const [estimate, setEstimate] = useState<CostEstimate | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setError(null);
    simulationsApi
      .estimateCosts(version.id, years)
      .then((res) => {
        if (!cancelled) setEstimate(res.data.data ?? null);
      })
      .catch((err) => {
        if (!cancelled) setError(getErrorMessage(err, 'No se pudo estimar el costo'));
      });
    return () => {
      cancelled = true;
    };
  }, [version.id, years, refreshKey]);

  return (
    <Card>
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <h2 className="text-base font-semibold text-ink-800">Costo estimado</h2>
          <p className="text-xs text-ink-500">Del modelo actual, sin simular.</p>
        </div>
        <label className="flex items-center gap-2 text-sm text-ink-600">
          <span className="sr-only">Horizonte</span>
          <select
            value={years}
            onChange={(e) => setYears(Number(e.target.value))}
            className={`${controlClasses} py-1`}
          >
            {HORIZONS.map((h) => (
              <option key={h} value={h}>
                {h} años
              </option>
            ))}
          </select>
        </label>
      </div>
      {error && <Alert tone="error" className="mt-3">{error}</Alert>}
      {!estimate && !error ? (
        <div className="flex justify-center py-6 text-brand-800">
          <Spinner className="h-5 w-5" />
        </div>
      ) : (
        estimate && (
          <>
            <p className="mt-4 text-3xl font-semibold text-ink-800">{formatMoney(estimate.totalCost, true)}</p>
            <dl className="mt-3 grid grid-cols-3 gap-2 text-sm">
              <div>
                <dt className="text-xs text-ink-500">CAPEX</dt>
                <dd className="tabular-nums text-ink-700">{formatMoney(estimate.capex, true)}</dd>
              </div>
              <div>
                <dt className="text-xs text-ink-500">OPEX</dt>
                <dd className="tabular-nums text-ink-700">{formatMoney(estimate.opex, true)}</dd>
              </div>
              <div>
                <dt className="text-xs text-ink-500">Mantenimiento</dt>
                <dd className="tabular-nums text-ink-700">{formatMoney(estimate.upkeep, true)}</dd>
              </div>
            </dl>
            {estimate.verticals.length > 0 && (
              <ul className="mt-4 flex flex-col gap-1 border-t border-ink-100 pt-3 text-sm">
                {estimate.verticals.map((v) => (
                  <li key={v.vertical} className="flex justify-between gap-2">
                    <span className="text-ink-600">
                      {VERTICAL_LABELS[v.vertical] ?? v.vertical}{' '}
                      <span className="text-ink-500">({v.nodeCount})</span>
                    </span>
                    <span className="tabular-nums text-ink-700">{formatMoney(v.totalCost, true)}</span>
                  </li>
                ))}
              </ul>
            )}
          </>
        )
      )}
    </Card>
  );
}
