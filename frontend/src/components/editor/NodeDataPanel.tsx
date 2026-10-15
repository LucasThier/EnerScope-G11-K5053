import { Fragment, useEffect, useState } from 'react';
import { Button } from '../ui/Button';
import { TextField } from '../ui/TextField';
import {
  COST_BASIS_LABELS,
  DISTRIBUTION_LABELS,
  NODE_STATE_LABELS,
  NODE_STATES,
  VERTICAL_COLORS,
  VERTICAL_LABELS,
  specForType,
} from './nodeCatalog';
import type { DiagramNode, Distribution, NodeDetail, NodeState } from '../../types/diagram';
import { formatNumber } from '../../utils/number';

interface NodeDataPanelProps {
  node: DiagramNode | null;
  /** Full detail (type-specific values), loaded on selection. */
  detail: NodeDetail | null;
  busy: boolean;
  onUpdateBasics: (nodeId: string, basics: { name?: string; state?: NodeState }) => void;
  onEditData: (nodeId: string) => void;
  onStartConnect: (nodeId: string) => void;
  onRestore: (nodeId: string) => void;
  onDelete: (nodeId: string) => void;
}

function fmt(n: number | null | undefined): string {
  return n == null ? '—' : formatNumber(n, 4);
}

function describeCurve(curve: Distribution | null): string {
  if (!curve) return 'Sin declinación';
  const label = DISTRIBUTION_LABELS[curve.type];
  if (curve.type === 'CONSTANT') return `${label}: ${fmt(curve.val_a)} %/año`;
  if (curve.type === 'UNIFORM') return `${label}: ${fmt(curve.val_a)}–${fmt(curve.val_b)} %/año`;
  return `${label}: ${fmt(curve.val_a)} ± ${fmt(curve.val_b)} %/año`;
}

/** Details of the selected node, with lightweight rename/state edit and delete. */
export function NodeDataPanel({
  node,
  detail,
  busy,
  onUpdateBasics,
  onEditData,
  onStartConnect,
  onRestore,
  onDelete,
}: NodeDataPanelProps) {
  const [name, setName] = useState('');
  const [state, setState] = useState<NodeState>('PROPOSED');

  useEffect(() => {
    setName(node?.name ?? '');
    setState(node?.state ?? 'PROPOSED');
  }, [node]);

  if (!node) {
    return (
      <div className="p-4 text-sm text-ink-500">
        Seleccioná un nodo para ver sus datos, o agregá uno desde la paleta.
      </div>
    );
  }

  const dirty = name !== node.name || state !== node.state;
  const removed = node.state === 'REMOVED';
  // REMOVED is not a manual choice, so it only appears while the node is removed
  // (so the current value renders) and never as a fresh option to pick.
  const stateOptions: NodeState[] = removed ? ['REMOVED', ...NODE_STATES] : NODE_STATES;
  const graph = node.graphData?.graphPosition;
  const geo = node.graphData?.geographicalPosition;
  const spec = specForType(node.type.nodeType);
  const showDetail = detail && detail.id === node.id;
  const capex = showDetail
    ? detail.investmentCost.reduce((sum, component) => sum + component.amount, 0)
    : 0;

  return (
    <div className="flex flex-col gap-4 overflow-y-auto p-4">
      <div className="flex items-center gap-2">
        <span
          className="inline-block h-3 w-3 rounded-full"
          style={{ background: VERTICAL_COLORS[node.type.vertical] }}
        />
        <h3 className="text-sm font-semibold text-ink-700">{spec?.label ?? node.type.nodeType}</h3>
      </div>

      <TextField label="Nombre" value={name} onChange={(e) => setName(e.target.value)} />

      <div className="flex flex-col gap-2">
        <label htmlFor="node-state" className="text-sm font-medium text-ink-600">
          Estado
        </label>
        <select
          id="node-state"
          value={state}
          onChange={(e) => setState(e.target.value as NodeState)}
          className="rounded-lg border border-ink-200 bg-white px-3 py-2 text-sm text-ink-800 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-400/40"
        >
          {stateOptions.map((s) => (
            <option key={s} value={s}>
              {NODE_STATE_LABELS[s]}
            </option>
          ))}
        </select>
      </div>

      <div className="flex gap-2">
        <Button
          variant="primary"
          loading={busy}
          disabled={!dirty || !name.trim()}
          onClick={() => onUpdateBasics(node.id, { name: name.trim(), state })}
          className="flex-1"
        >
          Guardar
        </Button>
        <Button variant="secondary" onClick={() => onEditData(node.id)} className="flex-1">
          Editar datos
        </Button>
      </div>

      <dl className="grid grid-cols-2 gap-x-3 gap-y-2 border-t border-ink-100 pt-3 text-xs">
        <dt className="text-ink-500">Vertical</dt>
        <dd className="text-ink-700">{VERTICAL_LABELS[node.type.vertical] ?? node.type.vertical}</dd>
        <dt className="text-ink-500">Lienzo x / y</dt>
        <dd className="text-ink-700">
          {fmt(graph?.x)} / {fmt(graph?.y)}
        </dd>
        <dt className="text-ink-500">Long. / Lat.</dt>
        <dd className="text-ink-700">
          {fmt(geo?.longitude)} / {fmt(geo?.latitude)}
        </dd>
      </dl>

      {showDetail && (
        <div className="border-t border-ink-100 pt-3">
          <span className="text-xs font-semibold uppercase tracking-wide text-ink-500">Datos</span>
          <dl className="mt-2 grid grid-cols-2 gap-x-3 gap-y-1 text-xs">
            {spec?.fields.map((f) => (
              <Fragment key={f.key}>
                <dt className="text-ink-500">{f.label}</dt>
                <dd className="text-ink-700">{fmt(detail.attributes[f.key])}</dd>
              </Fragment>
            ))}
            {spec?.hasDeclineCurve && (
              <>
                <dt className="text-ink-500">Declinación</dt>
                <dd className="text-ink-700">{describeCurve(detail.declineCurve)}</dd>
              </>
            )}
            <dt className="text-ink-500">Vida útil</dt>
            <dd className="text-ink-700">{fmt(detail.lifespanInMonths)} meses</dd>
            <dt className="text-ink-500">Mantenimiento</dt>
            <dd className="text-ink-700">
              cada {fmt(detail.maintenanceIntervalInDays)} días, {fmt(detail.maintenanceDuration)} h
            </dd>
            <dt className="text-ink-500">Operación / mes</dt>
            <dd className="text-ink-700">{fmt(detail.operatingCosts)}</dd>
            <dt className="text-ink-500">Cada mantenimiento</dt>
            <dd className="text-ink-700">{fmt(detail.upkeepCosts)}</dd>
            <dt className="text-ink-500">Inversión (nominal)</dt>
            <dd className="text-ink-700">{fmt(capex)}</dd>
          </dl>
          {detail.investmentCost.length > 0 && (
            <ul className="mt-2 flex flex-col gap-1 text-xs text-ink-600">
              {detail.investmentCost.map((component, index) => (
                <li key={index} className="flex justify-between gap-2">
                  <span className="truncate">
                    {component.name} · {COST_BASIS_LABELS[component.costBasis] ?? component.costBasis}
                  </span>
                  <span className="tabular-nums">{fmt(component.amount)}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      <Button variant="secondary" onClick={() => onStartConnect(node.id)}>
        Conectar con otro nodo…
      </Button>

      {removed ? (
        <Button
          variant="secondary"
          onClick={() => onRestore(node.id)}
          className="border-brand-300 text-brand-700 hover:bg-brand-50"
        >
          Restaurar nodo
        </Button>
      ) : (
        <Button
          variant="ghost"
          className="text-danger-600 hover:bg-danger-50"
          onClick={() => onDelete(node.id)}
        >
          Eliminar nodo
        </Button>
      )}
    </div>
  );
}
