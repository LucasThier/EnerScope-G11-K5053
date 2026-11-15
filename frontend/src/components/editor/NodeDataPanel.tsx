import { Fragment, useEffect, useState } from 'react';
import { Button } from '../ui/Button';
import { SelectField } from '../ui/SelectField';
import { TextField } from '../ui/TextField';
import { useTheme } from '../../hooks/useTheme';
import { DECLINE_LABELS, NODE_STATES, NODE_STATE_LABELS, ROLE_LABELS, VERTICAL_LABELS, nodeTypeLabel, specForType, verticalColor, operationalFields } from './nodeCatalog';
import type { DiagramNode, NodeDetail, NodeState } from '../../types/diagram';

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
  return n == null ? '—' : String(Number(n.toFixed(4)));
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
  const { resolved } = useTheme();

  useEffect(() => {
    setName(node?.name ?? '');
    setState(node?.state ?? 'PROPOSED');
  }, [node]);

  if (!node) {
    return (
      <div className="p-4 text-sm text-ink-500">
        Elegí un nodo para ver sus datos, o agregá uno desde la paleta.
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

  return (
    <div className="flex flex-col gap-4 p-4">
      <div className="flex items-center gap-2">
        <span
          className="inline-block h-3 w-3 rounded-full"
          style={{ background: verticalColor(resolved, node.type.vertical) }}
        />
        <h3 className="text-sm font-semibold text-ink-700">Datos del nodo</h3>
      </div>

      <TextField label="Nombre" value={name} onChange={(e) => setName(e.target.value)} />

      <SelectField
        id="node-state"
        label="Estado"
        value={state}
        onChange={(e) => setState(e.target.value as NodeState)}
      >
        {stateOptions.map((s) => (
          <option key={s} value={s}>
            {NODE_STATE_LABELS[s]}
          </option>
        ))}
      </SelectField>

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
          Editar
        </Button>
      </div>

      <dl className="grid grid-cols-2 gap-x-3 gap-y-2 border-t border-ink-100 pt-3 text-xs">
        <dt className="text-ink-500">Tipo</dt>
        <dd className="text-ink-700">{nodeTypeLabel(node.type.nodeType)}</dd>
        <dt className="text-ink-500">Vertical</dt>
        <dd className="text-ink-700">{VERTICAL_LABELS[node.type.vertical]}</dd>
        <dt className="text-ink-500">Rol</dt>
        <dd className="text-ink-700">{ROLE_LABELS[node.type.role]}</dd>
        <dt className="text-ink-500">Lienzo x / y</dt>
        <dd className="text-ink-700">
          {fmt(graph?.x)} / {fmt(graph?.y)}
        </dd>
        <dt className="text-ink-500">Long. / Lat.</dt>
        <dd className="text-ink-700">
          {fmt(geo?.longitude)} / {fmt(geo?.latitude)}
        </dd>
        <dt className="text-ink-500">ID</dt>
        <dd className="truncate text-ink-500" title={node.id}>
          {node.id.slice(0, 8)}…
        </dd>
      </dl>

      {showDetail && (
        <div className="border-t border-ink-100 pt-3">
          <span className="text-xs font-semibold uppercase tracking-wide text-ink-500">Datos</span>
          <dl className="mt-2 grid grid-cols-2 gap-x-3 gap-y-1 text-xs">
            <dt className="text-ink-400">Vida útil (meses)</dt>
            <dd className="text-ink-700">{fmt(detail.lifespanInMonths)}</dd>
            <dt className="text-ink-500">Intervalo de mantenimiento (días)</dt>
            <dd className="text-ink-700">{fmt(detail.maintenanceIntervalInDays)}</dd>
            <dt className="text-ink-500">Pérdidas (%)</dt>
            <dd className="text-ink-700">{fmt(detail.wastePercentage)}</dd>
            {spec && operationalFields(spec).map((f) => (
              <Fragment key={f.key}>
                <dt className="text-ink-400">{f.label}</dt>
                <dd className="text-ink-700">{f.key === 'declineCurve' && detail.declineCurve ? `${DECLINE_LABELS[detail.declineCurve.type]}: ${detail.declineCurve.val_a}${detail.declineCurve.val_b == null ? '' : ` / ${detail.declineCurve.val_b}`}` : fmt(detail.attributes[f.key])}</dd>
              </Fragment>
            ))}
          </dl>
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
          className="text-danger-600! hover:bg-danger-50"
          onClick={() => onDelete(node.id)}
        >
          Eliminar nodo
        </Button>
      )}
    </div>
  );
}
