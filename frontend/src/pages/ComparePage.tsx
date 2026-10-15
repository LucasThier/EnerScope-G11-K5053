import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { projectsApi } from '../api/projects';
import { simulationsApi } from '../api/simulations';
import { getErrorMessage } from '../api/errors';
import { useActiveProject } from '../hooks/useActiveProject';
import type { VersionSummary } from '../types/diagram';
import type { ComparedVersion, NodeChangeKind, SimulationKpis, VersionComparison } from '../types/simulation';
import { formatCompact, formatDateTime, formatMoney, formatPercent, relativeChange } from '../utils/number';
import { Alert } from '../components/ui/Alert';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Spinner } from '../components/ui/Spinner';
import { ArrowLeftIcon } from '../components/ui/icons';
import { BarList, type BarItem } from '../components/charts/BarList';
import { SimulateModal } from '../components/scenarios/SimulateModal';
import { WARNING_MESSAGES, fieldLabel } from '../components/scenarios/simulationCopy';
import { orderAsTree } from '../components/scenarios/scenarioTree';
import { VERTICAL_LABELS, nodeTypeLabel } from '../components/editor/nodeCatalog';

const MAX_COMPARED = 6;

/** Whether a higher value of an indicator is better, worse, or neither. */
type Direction = 'higher' | 'lower' | 'neutral';

interface Indicator {
  key: string;
  label: string;
  direction: Direction;
  read: (version: ComparedVersion) => number | null;
  format: (value: number | null) => string;
}

const kpi = (pick: (k: SimulationKpis) => number | null) => (version: ComparedVersion) =>
  version.latestSimulation ? pick(version.latestSimulation.kpis) : null;

const INDICATORS: Indicator[] = [
  { key: 'deliveredP50', label: 'Volumen entregado (P50)', direction: 'higher', read: kpi((k) => k.deliveredP50), format: formatCompact },
  { key: 'deliveredP10', label: 'Entregado, caso bajo (P10)', direction: 'higher', read: kpi((k) => k.deliveredP10), format: formatCompact },
  { key: 'deliveredP90', label: 'Entregado, caso alto (P90)', direction: 'higher', read: kpi((k) => k.deliveredP90), format: formatCompact },
  { key: 'exportedP50', label: 'Exportado como GNL (P50)', direction: 'higher', read: kpi((k) => k.exportedP50), format: formatCompact },
  { key: 'domesticP50', label: 'Mercado interno (P50)', direction: 'higher', read: kpi((k) => k.domesticP50), format: formatCompact },
  { key: 'lossesP50', label: 'Pérdidas y consumos (P50)', direction: 'lower', read: kpi((k) => k.lossesP50), format: formatCompact },
  { key: 'capex', label: 'CAPEX', direction: 'lower', read: kpi((k) => k.capex), format: (v) => formatMoney(v, true) },
  { key: 'opex', label: 'OPEX', direction: 'lower', read: kpi((k) => k.opex), format: (v) => formatMoney(v, true) },
  { key: 'upkeep', label: 'Mantenimiento', direction: 'lower', read: kpi((k) => k.upkeep), format: (v) => formatMoney(v, true) },
  { key: 'totalCost', label: 'Costo total', direction: 'lower', read: kpi((k) => k.totalCost), format: (v) => formatMoney(v, true) },
  { key: 'unitCost', label: 'Costo unitario', direction: 'lower', read: kpi((k) => k.unitCost), format: (v) => formatMoney(v) },
  { key: 'nodeCount', label: 'Nodos activos', direction: 'neutral', read: (v) => v.nodeCount, format: (v) => (v == null ? '—' : String(v)) },
  { key: 'connectionCount', label: 'Conexiones', direction: 'neutral', read: (v) => v.connectionCount, format: (v) => (v == null ? '—' : String(v)) },
];

/** Indicators charted as small multiples, one bar per scenario. */
const CHARTED = ['deliveredP50', 'totalCost', 'unitCost', 'exportedP50', 'domesticP50', 'capex'];

const CHANGE_LABELS: Record<NodeChangeKind, string> = {
  ADDED: 'Agregado',
  REMOVED: 'Quitado',
  MODIFIED: 'Modificado',
};

const headerCell = 'px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-ink-500';
const bodyCell = 'px-3 py-2 text-sm text-ink-700 whitespace-nowrap';

/**
 * The change of a value against the baseline. Better/worse is spelled out next
 * to the arrow, so the colour is never the only cue; neutral indicators (counts)
 * get no verdict at all.
 */
function Delta({ value, base, direction }: { value: number | null; base: number | null; direction: Direction }) {
  const change = relativeChange(value, base);
  if (change == null) return <span className="text-ink-500">—</span>;
  if (Math.abs(change) < 0.0005) return <span className="text-ink-500">= igual</span>;
  const up = change > 0;
  const better = direction === 'neutral' ? null : (direction === 'higher') === up;
  const tone =
    better == null
      ? 'bg-ink-100 text-ink-700'
      : better
        ? 'bg-brand-50 text-brand-800'
        : 'bg-danger-50 text-danger-700';
  return (
    <span className={`inline-flex items-center gap-1 rounded-full px-1.5 py-0.5 text-xs font-medium ${tone}`}>
      <span aria-hidden="true">{up ? '▲' : '▼'}</span>
      {up ? '+' : '−'}
      {formatPercent(Math.abs(change), 1)}
      {better != null && <span>{better ? 'mejor' : 'peor'}</span>}
    </span>
  );
}

function Section({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-3">
      <div>
        <h2 className="text-lg font-semibold text-ink-800">{title}</h2>
        {subtitle && <p className="text-sm text-ink-500">{subtitle}</p>}
      </div>
      {children}
    </section>
  );
}

/**
 * Compares two to six scenarios of a project. The first one selected is the
 * baseline: every other is shown with its change against it, both in figures
 * (the latest simulation of each) and in structure (nodes added, removed or
 * modified). The selection lives in `?versions=a,b,c` so a comparison can be
 * shared or bookmarked.
 */
export function ComparePage() {
  const { projectId = '' } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const { projects } = useActiveProject();
  const project = projects.find((p) => p.id === projectId);

  const [versions, setVersions] = useState<VersionSummary[]>([]);
  const [comparison, setComparison] = useState<VersionComparison | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [simulating, setSimulating] = useState<ComparedVersion | null>(null);

  const selection = useMemo(
    () => (searchParams.get('versions') ?? '').split(',').filter(Boolean).slice(0, MAX_COMPARED),
    [searchParams],
  );
  const selectionKey = selection.join(',');

  const setSelection = useCallback(
    (ids: string[]) => setSearchParams(ids.length ? { versions: ids.join(',') } : {}, { replace: true }),
    [setSearchParams],
  );

  useEffect(() => {
    projectsApi
      .listVersions(projectId)
      .then((res) => setVersions(res.data.data ?? []))
      .catch((err) => setError(getErrorMessage(err, 'No se pudieron cargar los escenarios')));
  }, [projectId]);

  const loadComparison = useCallback(async () => {
    const ids = selectionKey.split(',').filter(Boolean);
    if (ids.length < 2) {
      setComparison(null);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const res = await simulationsApi.compare(projectId, ids);
      setComparison(res.data.data ?? null);
    } catch (err) {
      setError(getErrorMessage(err, 'No se pudo comparar los escenarios'));
    } finally {
      setLoading(false);
    }
  }, [projectId, selectionKey]);

  useEffect(() => {
    void loadComparison();
  }, [loadComparison]);

  function toggle(id: string) {
    if (selection.includes(id)) {
      setSelection(selection.filter((x) => x !== id));
    } else if (selection.length < MAX_COMPARED) {
      setSelection([...selection, id]);
    }
  }

  function makeBaseline(id: string) {
    setSelection([id, ...selection.filter((x) => x !== id)]);
  }

  const compared = useMemo(() => comparison?.versions ?? [], [comparison]);
  const baseline = compared[0] ?? null;
  const rows = orderAsTree(versions);
  const nameOf = (id: string) => versions.find((v) => v.id === id)?.name ?? 'Escenario';

  const verticalKeys = useMemo(() => {
    const keys = new Set<string>();
    compared.forEach((v) => v.verticals?.forEach((row) => keys.add(row.vertical)));
    return Object.keys(VERTICAL_LABELS).filter((key) => keys.has(key));
  }, [compared]);

  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-col gap-2">
        <Link
          to={`/projects/${projectId}/scenarios`}
          className="inline-flex items-center gap-1 self-start text-sm font-medium text-brand-800 hover:underline"
        >
          <ArrowLeftIcon className="h-4 w-4" />
          Escenarios
        </Link>
        <h1 className="text-2xl font-semibold text-ink-800">Comparar escenarios</h1>
        <p className="text-sm text-ink-500">
          {project ? `${project.name} · ` : ''}Elegí de 2 a {MAX_COMPARED} escenarios. El primero es la base
          contra la que se miden los demás; se usa la última simulación de cada uno.
        </p>
      </div>

      <Card>
        <fieldset>
          <legend className="text-sm font-semibold text-ink-800">Escenarios a comparar</legend>
          {rows.length === 0 ? (
            <p className="mt-2 text-sm text-ink-500">Este proyecto no tiene escenarios todavía.</p>
          ) : (
            <ul className="mt-3 flex flex-wrap gap-2">
              {rows.map(({ version, depth }) => {
                const index = selection.indexOf(version.id);
                const checked = index >= 0;
                return (
                  <li key={version.id}>
                    <label
                      className={
                        'flex cursor-pointer items-center gap-2 rounded-full border px-3 py-1.5 text-sm ' +
                        (checked ? 'border-brand-300 bg-brand-50 text-brand-900' : 'border-ink-200 bg-white text-ink-700 hover:bg-ink-50')
                      }
                    >
                      <input
                        type="checkbox"
                        className="h-4 w-4 accent-brand-800"
                        checked={checked}
                        disabled={!checked && selection.length >= MAX_COMPARED}
                        onChange={() => toggle(version.id)}
                      />
                      {depth > 0 && <span className="text-ink-400" aria-hidden="true">↳</span>}
                      {version.name}
                      {index === 0 && selection.length > 1 && (
                        <span className="rounded-full bg-white px-1.5 text-xs font-medium text-ink-600">base</span>
                      )}
                      {index > 0 && (
                        <button
                          type="button"
                          onClick={(e) => {
                            e.preventDefault();
                            makeBaseline(version.id);
                          }}
                          className="rounded px-1 text-xs font-medium text-brand-800 underline-offset-2 hover:underline"
                        >
                          usar como base
                        </button>
                      )}
                    </label>
                  </li>
                );
              })}
            </ul>
          )}
        </fieldset>
      </Card>

      {error && <Alert tone="error">{error}</Alert>}

      {selection.length < 2 ? (
        <p className="text-sm text-ink-500">Marcá al menos dos escenarios para ver la comparación.</p>
      ) : loading && !comparison ? (
        <div className="flex justify-center py-12 text-brand-800">
          <Spinner className="h-6 w-6" />
        </div>
      ) : (
        comparison &&
        baseline && (
          <div className={`flex flex-col gap-8 transition-opacity ${loading ? 'opacity-60' : ''}`}>
            {comparison.warnings.map((warning) => (
              <Alert key={warning} tone="error">
                {WARNING_MESSAGES[warning]}
              </Alert>
            ))}

            <Section title="Indicadores" subtitle="Cada escenario con su cambio respecto de la base.">
              <Card padded={false}>
                <div className="overflow-x-auto">
                  <table className="w-full min-w-[48rem] border-collapse">
                    <thead>
                      <tr className="border-b border-ink-100 align-bottom">
                        <th scope="col" className={headerCell}>Indicador</th>
                        {compared.map((v, i) => (
                          <th key={v.versionId} scope="col" className="px-3 py-2 text-right text-xs text-ink-500">
                            <span className="block text-sm font-semibold text-ink-800">
                              {v.name}
                              {i === 0 && <span className="ml-1 text-xs font-medium text-ink-500">(base)</span>}
                            </span>
                            {v.latestSimulation ? (
                              <span className="block">
                                {v.latestSimulation.years} años · {formatDateTime(v.latestSimulation.createdAt)}
                              </span>
                            ) : (
                              <Button
                                variant="ghost"
                                className="mt-1 px-2 py-0.5 text-xs"
                                onClick={() => setSimulating(v)}
                              >
                                Simular
                              </Button>
                            )}
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-ink-100">
                      {INDICATORS.map((indicator) => {
                        const base = indicator.read(baseline);
                        return (
                          <tr key={indicator.key}>
                            <th scope="row" className={`${bodyCell} text-left font-medium text-ink-600`}>
                              {indicator.label}
                            </th>
                            {compared.map((v, i) => {
                              const value = indicator.read(v);
                              return (
                                <td key={v.versionId} className={`${bodyCell} text-right`}>
                                  <span className="block font-semibold tabular-nums text-ink-800">
                                    {indicator.format(value)}
                                  </span>
                                  {i > 0 && (
                                    <span className="block">
                                      <Delta value={value} base={base} direction={indicator.direction} />
                                    </span>
                                  )}
                                </td>
                              );
                            })}
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </Card>
            </Section>

            <Section title="Gráficos" subtitle="Un panel por indicador; la tabla de arriba tiene los mismos valores.">
              <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
                {INDICATORS.filter((i) => CHARTED.includes(i.key)).map((indicator) => {
                  const base = indicator.read(baseline);
                  const items: BarItem[] = compared.map((v, i) => ({
                    key: v.versionId,
                    label: v.name,
                    value: indicator.read(v),
                    note: i === 0 ? 'base' : v.latestSimulation ? undefined : 'sin simular',
                  }));
                  return (
                    <Card key={indicator.key}>
                      <BarList
                        title={indicator.label}
                        subtitle={indicator.direction === 'higher' ? 'Más es mejor' : 'Menos es mejor'}
                        items={items}
                        format={indicator.format}
                        annotate={(item) =>
                          item.key === baseline.versionId ? null : (
                            <Delta value={item.value} base={base} direction={indicator.direction} />
                          )
                        }
                      />
                    </Card>
                  );
                })}
              </div>
            </Section>

            <Section
              title="Cambios en el modelo"
              subtitle={`Nodos y conexiones de cada escenario frente a ${baseline.name}, emparejados por identidad.`}
            >
              {compared.slice(1).map((v) => (
                <Card key={v.versionId}>
                  <div className="flex flex-wrap items-baseline justify-between gap-2">
                    <h3 className="text-base font-semibold text-ink-800">{v.name}</h3>
                    {v.diff && (
                      <p className="text-sm text-ink-600">
                        {v.diff.added} agregados · {v.diff.removed} quitados · {v.diff.modified} modificados ·{' '}
                        {v.diff.unchanged} sin cambios · conexiones +{v.diff.connectionsAdded} / −
                        {v.diff.connectionsRemoved}
                      </p>
                    )}
                  </div>
                  {v.diff && v.diff.nodes.length > 0 ? (
                    <ul className="mt-3 flex flex-col divide-y divide-ink-100 text-sm">
                      {v.diff.nodes.map((node) => (
                        <li key={`${node.change}-${node.identityId}`} className="flex flex-wrap items-baseline gap-x-3 gap-y-1 py-2">
                          <span className="w-24 shrink-0 text-xs font-semibold uppercase tracking-wide text-ink-500">
                            {CHANGE_LABELS[node.change]}
                          </span>
                          <span className="font-medium text-ink-800">{node.name}</span>
                          <span className="text-ink-500">{nodeTypeLabel(node.nodeType)}</span>
                          {node.changedFields.length > 0 && (
                            <span className="text-ink-600">
                              Cambió: {node.changedFields.map(fieldLabel).join(', ')}
                            </span>
                          )}
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <p className="mt-2 text-sm text-ink-500">Mismos nodos y parámetros que la base.</p>
                  )}
                </Card>
              ))}
            </Section>

            {verticalKeys.length > 0 && (
              <Section title="Costo por vertical" subtitle="CAPEX + OPEX + mantenimiento de la última simulación.">
                <Card padded={false}>
                  <div className="overflow-x-auto">
                    <table className="w-full min-w-[40rem] border-collapse">
                      <thead>
                        <tr className="border-b border-ink-100">
                          <th scope="col" className={headerCell}>Vertical</th>
                          {compared.map((v) => (
                            <th key={v.versionId} scope="col" className={`${headerCell} text-right`}>
                              {v.name}
                            </th>
                          ))}
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-ink-100">
                        {verticalKeys.map((key) => {
                          const read = (v: ComparedVersion) =>
                            v.verticals?.find((row) => row.vertical === key)?.totalCost ?? (v.verticals ? 0 : null);
                          return (
                            <tr key={key}>
                              <th scope="row" className={`${bodyCell} text-left font-medium text-ink-600`}>
                                {VERTICAL_LABELS[key] ?? key}
                              </th>
                              {compared.map((v, i) => (
                                <td key={v.versionId} className={`${bodyCell} text-right`}>
                                  <span className="block tabular-nums text-ink-800">{formatMoney(read(v), true)}</span>
                                  {i > 0 && (
                                    <span className="block">
                                      <Delta value={read(v)} base={read(baseline)} direction="lower" />
                                    </span>
                                  )}
                                </td>
                              ))}
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                </Card>
              </Section>
            )}
          </div>
        )
      )}

      {simulating && (
        <SimulateModal
          open
          onClose={() => setSimulating(null)}
          versionId={simulating.versionId}
          versionName={simulating.name ?? nameOf(simulating.versionId)}
          defaultYears={baseline?.latestSimulation?.years ?? 20}
          onDone={() => {
            setSimulating(null);
            void loadComparison();
          }}
        />
      )}
    </div>
  );
}
