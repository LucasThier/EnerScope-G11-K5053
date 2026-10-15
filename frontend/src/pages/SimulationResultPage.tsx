import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { simulationsApi } from '../api/simulations';
import { getErrorMessage } from '../api/errors';
import { projectsApi } from '../api/projects';
import type { VersionSummary } from '../types/diagram';
import type { SimulationDetail } from '../types/simulation';
import { formatCompact, formatDateTime, formatMoney, formatNumber } from '../utils/number';
import { Alert } from '../components/ui/Alert';
import { Card } from '../components/ui/Card';
import { Spinner } from '../components/ui/Spinner';
import { ArrowLeftIcon } from '../components/ui/icons';
import { BarList } from '../components/charts/BarList';
import { StatTile } from '../components/charts/StatTile';
import { UtilizationMeter } from '../components/charts/UtilizationMeter';
import { NODE_STATE_LABELS, VERTICAL_LABELS, nodeTypeLabel } from '../components/editor/nodeCatalog';
import { WARNING_MESSAGES } from '../components/scenarios/simulationCopy';
import type { NodeState } from '../types/diagram';

const headerCell = 'px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-ink-500';
const bodyCell = 'px-3 py-2 text-sm text-ink-700 whitespace-nowrap';

/**
 * One simulation run: the headline figures, the cost broken down by vertical,
 * and every node's production, utilization, losses and cost. All volumes are
 * totals over the run's horizon; the per-node table is the plain-data view of
 * everything the charts show.
 */
export function SimulationResultPage() {
  const { projectId = '', versionId = '', simulationId = '' } = useParams();
  const [detail, setDetail] = useState<SimulationDetail | null>(null);
  const [version, setVersion] = useState<VersionSummary | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setDetail(null);
    setError(null);
    simulationsApi
      .get(versionId, simulationId)
      .then((res) => {
        if (!cancelled) setDetail(res.data.data ?? null);
      })
      .catch((err) => {
        if (!cancelled) setError(getErrorMessage(err, 'No se pudo cargar la simulación'));
      });
    projectsApi
      .listVersions(projectId)
      .then((res) => {
        if (!cancelled) setVersion((res.data.data ?? []).find((v) => v.id === versionId) ?? null);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [projectId, versionId, simulationId]);

  const costBars = useMemo(
    () =>
      (detail?.verticals ?? []).map((v) => ({
        key: v.vertical,
        label: VERTICAL_LABELS[v.vertical] ?? v.vertical,
        value: v.totalCost,
        note: `${v.nodeCount} ${v.nodeCount === 1 ? 'nodo' : 'nodos'}`,
      })),
    [detail],
  );

  const lossBars = useMemo(
    () =>
      (detail?.verticals ?? [])
        .filter((v) => (v.lossesP50 ?? 0) > 0)
        .map((v) => ({
          key: v.vertical,
          label: VERTICAL_LABELS[v.vertical] ?? v.vertical,
          value: v.lossesP50,
        })),
    [detail],
  );

  const back = (
    <Link
      to={`/projects/${projectId}/scenarios`}
      className="inline-flex items-center gap-1 text-sm font-medium text-brand-800 hover:underline"
    >
      <ArrowLeftIcon className="h-4 w-4" />
      Escenarios
    </Link>
  );

  if (error) {
    return (
      <div className="flex flex-col gap-4">
        {back}
        <Alert tone="error">{error}</Alert>
      </div>
    );
  }
  if (!detail) {
    return (
      <div className="flex justify-center py-16 text-brand-800">
        <Spinner className="h-6 w-6" />
      </div>
    );
  }

  const { summary } = detail;
  const kpis = summary.kpis;

  return (
    <div className="flex flex-col gap-6">
      <div>{back}</div>
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-ink-800">
            Resultados{version ? ` de ${version.name}` : ''}
          </h1>
          <p className="mt-1 text-sm text-ink-500">
            Simulado el {formatDateTime(summary.createdAt)} · horizonte de {summary.years} años ·{' '}
            {summary.rounds} rondas Monte Carlo · {summary.nodeCount} nodos
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Link
            to={`/projects/${projectId}/editor?version=${versionId}`}
            className="rounded-lg px-3 py-2 text-sm font-medium text-brand-800 hover:bg-brand-50"
          >
            Abrir en el editor
          </Link>
          <Link
            to={`/projects/${projectId}/compare?versions=${versionId}`}
            className="rounded-lg bg-brand-800 px-3 py-2 text-sm font-semibold text-white hover:bg-brand-900"
          >
            Comparar con otros escenarios
          </Link>
        </div>
      </header>

      {summary.warnings.map((warning) => (
        <Alert key={warning} tone="error">
          {WARNING_MESSAGES[warning]}
        </Alert>
      ))}

      <section aria-label="Indicadores principales" className="grid grid-cols-1 gap-3 md:grid-cols-4">
        <div className="md:col-span-2">
          <StatTile
            hero
            label="Volumen entregado (P50)"
            value={formatCompact(kpis.deliveredP50)}
            detail={`Rango P10–P90: ${formatCompact(kpis.deliveredP10)} – ${formatCompact(kpis.deliveredP90)} · media ${formatCompact(kpis.deliveredMean)}`}
          />
        </div>
        <StatTile label="Exportado como GNL (P50)" value={formatCompact(kpis.exportedP50)} />
        <StatTile label="Mercado interno (P50)" value={formatCompact(kpis.domesticP50)} />
        <StatTile
          label="Costo total"
          value={formatMoney(kpis.totalCost, true)}
          detail={`En ${summary.years} años`}
        />
        <StatTile
          label="Costo unitario"
          value={formatMoney(kpis.unitCost)}
          detail="Costo total / volumen entregado P50"
        />
        <StatTile label="CAPEX" value={formatMoney(kpis.capex, true)} detail="Inversión" />
        <StatTile
          label="OPEX + mantenimiento"
          value={formatMoney(kpis.opex + kpis.upkeep, true)}
          detail={`Operación ${formatMoney(kpis.opex, true)} · mant. ${formatMoney(kpis.upkeep, true)}`}
        />
        <div className="md:col-span-4">
          <StatTile
            label="Pérdidas y consumos de proceso (P50)"
            value={formatCompact(kpis.lossesP50)}
            detail="Transporte, descarga de contaminantes en tratamiento, compresión y licuefacción."
          />
        </div>
      </section>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <Card>
          <BarList
            title="Costo total por vertical"
            subtitle={`CAPEX + OPEX + mantenimiento en ${summary.years} años`}
            items={costBars}
            format={(v) => formatMoney(v, true)}
          />
        </Card>
        <Card>
          {lossBars.length > 0 ? (
            <BarList
              title="Pérdidas por vertical (P50)"
              subtitle="Volumen perdido o consumido en el proceso"
              items={lossBars}
              format={formatCompact}
            />
          ) : (
            <p className="text-sm text-ink-500">La corrida no registró pérdidas.</p>
          )}
        </Card>
      </div>

      <Card padded={false}>
        <div className="border-b border-ink-100 px-4 py-3">
          <h2 className="text-base font-semibold text-ink-800">Nodos</h2>
          <p className="text-xs text-ink-500">
            Producción: lo que pasó por el nodo en todo el horizonte. Utilización: producción sobre lo
            que el nodo podía procesar en las horas que estuvo operativo.
          </p>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[64rem] border-collapse">
            <thead>
              <tr className="border-b border-ink-100">
                <th scope="col" className={headerCell}>Nodo</th>
                <th scope="col" className={headerCell}>Vertical</th>
                <th scope="col" className={`${headerCell} text-right`}>P10</th>
                <th scope="col" className={`${headerCell} text-right`}>P50</th>
                <th scope="col" className={`${headerCell} text-right`}>P90</th>
                <th scope="col" className={headerCell}>Utilización</th>
                <th scope="col" className={`${headerCell} text-right`}>Pérdidas</th>
                <th scope="col" className={`${headerCell} text-right`}>CAPEX</th>
                <th scope="col" className={`${headerCell} text-right`}>OPEX + mant.</th>
                <th scope="col" className={`${headerCell} text-right`}>Total</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-ink-100">
              {detail.nodes.map((node) => (
                <tr key={node.nodeId}>
                  <td className={bodyCell}>
                    <span className="block font-semibold text-ink-800">{node.name}</span>
                    <span className="block text-xs text-ink-500">
                      {nodeTypeLabel(node.nodeType)}
                      {node.state ? ` · ${NODE_STATE_LABELS[node.state as NodeState] ?? node.state}` : ''}
                    </span>
                  </td>
                  <td className={bodyCell}>{VERTICAL_LABELS[node.vertical ?? 'UNKNOWN'] ?? node.vertical}</td>
                  <td className={`${bodyCell} text-right tabular-nums`}>{formatCompact(node.producedP10)}</td>
                  <td className={`${bodyCell} text-right font-semibold tabular-nums`}>
                    {node.producedP50 == null ? (
                      <span title="El nodo forma parte de un ciclo y no se simuló">No simulado</span>
                    ) : (
                      formatCompact(node.producedP50)
                    )}
                  </td>
                  <td className={`${bodyCell} text-right tabular-nums`}>{formatCompact(node.producedP90)}</td>
                  <td className={bodyCell}>
                    <UtilizationMeter ratio={node.utilizationP50} />
                  </td>
                  <td className={`${bodyCell} text-right tabular-nums`}>
                    {node.lossesP50 ? formatCompact(node.lossesP50) : formatNumber(node.lossesP50)}
                  </td>
                  <td className={`${bodyCell} text-right tabular-nums`}>{formatMoney(node.capex, true)}</td>
                  <td className={`${bodyCell} text-right tabular-nums`}>
                    {formatMoney(node.opex + node.upkeep, true)}
                  </td>
                  <td className={`${bodyCell} text-right tabular-nums`}>{formatMoney(node.totalCost, true)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      <p className="text-xs text-ink-500">
        Los percentiles son estadísticos: P10 es el caso bajo y P90 el alto entre las rondas simuladas. Los
        volúmenes están en las unidades del modelo (capacidad por hora × horas simuladas). Los costos no
        incluyen el costo DTM, de tratamiento ni de contratación de buques.
      </p>
    </div>
  );
}
