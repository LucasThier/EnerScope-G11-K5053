import { useMemo, useState } from 'react';
import { useTheme } from '../../hooks/useTheme';
import { Button } from '../ui/Button';
import { TextField } from '../ui/TextField';
import { NodeIcon } from './nodeIcons';
import {
  NODE_STATES,
  NODE_TYPE_SPECS,
  verticalColor,
  defaultBaseValues,
  operationalFields,
  specForType,
  type NodeBaseValues,
  type NodeTypeSpec,
} from './nodeCatalog';
import type { NodeState, NodeType } from '../../types/diagram';

export interface NodeFormInitial {
  type: NodeType;
  base: NodeBaseValues;
  typeFields: Record<string, number>;
}

interface NodeFormPanelProps {
  mode: 'create' | 'edit';
  busy: boolean;
  initial?: NodeFormInitial;
  /** Pre-selects the type on create (e.g. dropped/clicked from the palette). */
  initialType?: NodeType;
  onSubmit: (
    spec: NodeTypeSpec,
    base: NodeBaseValues,
    typeFields: Record<string, number>,
  ) => Promise<void>;
  onClose: () => void;
}

const selectClass =
  'rounded-lg border border-ink-200 bg-surface px-3 py-2.5 text-sm text-ink-800 focus:border-focus focus:outline-none focus:ring-2 focus:ring-focus/60';

/** Create or edit a node. On edit the type is fixed; on create it is chosen. */
export function NodeFormPanel({
  mode,
  busy,
  initial,
  initialType,
  onSubmit,
  onClose,
}: NodeFormPanelProps) {
  const [type, setType] = useState<NodeType>(initial?.type ?? initialType ?? 'WELL');
  const [base, setBase] = useState<NodeBaseValues>(initial?.base ?? { ...defaultBaseValues() });
  // Keep editable text until submission so clearing a number does not immediately restore zero.
  const [numericBase, setNumericBase] = useState({
    lifespanInMonths: initial ? String(initial.base.lifespanInMonths) : '',
    maintenanceIntervalInDays: String(initial?.base.maintenanceIntervalInDays ?? defaultBaseValues().maintenanceIntervalInDays),
  });
  const [typeFields, setTypeFields] = useState<Record<string, string>>(
    Object.fromEntries(Object.entries(initial?.typeFields ?? {}).map(([key,value]) => [key,String(value)])),
  );
  const { resolved } = useTheme();

  const [declineType, setDeclineType] = useState<import('../../types/diagram').DeclineDistribution['type']>(initial?.base.declineCurve?.type ?? 'CONSTANT');
  const [declineA, setDeclineA] = useState(String(initial?.base.declineCurve?.val_a ?? initial?.typeFields.declineCurve ?? ''));
  const [declineB, setDeclineB] = useState(String(initial?.base.declineCurve?.val_b ?? ''));
  const a = Number(declineA), b = Number(declineB);
  const validDecline = type !== 'WELL' || (declineA.trim() !== '' && Number.isFinite(a)
    && (declineType === 'NORMAL' || (a >= 0 && a <= 100))
    && (declineType === 'CONSTANT' || (declineB.trim() !== '' && Number.isFinite(b)
      && (declineType === 'NORMAL' ? b > 0 : b > a && b <= 100))));

  const spec = useMemo(() => specForType(type), [type]);
  const color = verticalColor(resolved, spec?.vertical);
  // REMOVED is set by deleting a node, not chosen here; only expose it so an
  // already-removed node's state still renders while editing.
  const stateOptions: NodeState[] =
    base.state === 'REMOVED' ? ['REMOVED', ...NODE_STATES] : NODE_STATES;

  function num(v: string): number {
    const n = Number(v);
    return Number.isFinite(n) ? n : 0;
  }

  async function submit() {
    if (!spec || !base.name.trim() || !validDecline) return;
    await onSubmit(spec, { ...base,
      ...(type === 'WELL' ? { declineCurve: { type: declineType, val_a: a, val_b: declineType === 'CONSTANT' ? null : b } } : {}),
      lifespanInMonths: num(numericBase.lifespanInMonths),
      maintenanceIntervalInDays: num(numericBase.maintenanceIntervalInDays),
    }, Object.fromEntries(Object.entries(typeFields).map(([key,value]) => [key,num(value)])));
    onClose();
  }

  return (
    <div className="flex max-h-full flex-col gap-3 overflow-y-auto p-4">
      <div className="flex items-center justify-between">
        <h3 className="flex items-center gap-2 text-sm font-semibold text-ink-700">
          <span
            className="flex h-6 w-6 items-center justify-center rounded-md text-on-accent"
            style={{ background: color }}
          >
            <NodeIcon type={type} className="h-4 w-4" />
          </span>
          {mode === 'create' ? 'Add node' : 'Edit node'}
        </h3>
        <Button variant="ghost" onClick={onClose} className="px-2 py-1 text-xs">
          Close
        </Button>
      </div>

      <div className="flex flex-col gap-1.5">
        <label htmlFor="node-form-type" className="text-sm font-medium text-ink-600">
          Type
        </label>
        <select
          id="node-form-type"
          value={type}
          disabled={mode === 'edit'}
          onChange={(e) => {
            setType(e.target.value as NodeType);
            setTypeFields({});
          }}
          className={selectClass + (mode === 'edit' ? ' opacity-60' : '')}
        >
          {NODE_TYPE_SPECS.map((s) => (
            <option key={s.type} value={s.type}>
              {s.label}
            </option>
          ))}
        </select>
      </div>

      <TextField
        label="Name"
        value={base.name}
        onChange={(e) => setBase((b) => ({ ...b, name: e.target.value }))}
        placeholder="e.g. North Well 1"
      />

      <div className="flex flex-col gap-1.5">
        <label htmlFor="node-form-state" className="text-sm font-medium text-ink-600">
          State
        </label>
        <select
          id="node-form-state"
          value={base.state}
          onChange={(e) => setBase((b) => ({ ...b, state: e.target.value as NodeState }))}
          className={selectClass}
        >
          {stateOptions.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
      </div>

      <p className="text-xs text-ink-500">La configuración económica se edita en Configuración del Escenario.</p>

      <div className="grid grid-cols-2 gap-2">
        <TextField
          label="Lifespan (months)"
          type="number"
          value={numericBase.lifespanInMonths}
          onChange={(e) => setNumericBase((b) => ({ ...b, lifespanInMonths: e.target.value }))}
        />
        <TextField
          label="Maint. interval (days)"
          type="number"
          value={numericBase.maintenanceIntervalInDays}
          onChange={(e) => setNumericBase((b) => ({ ...b, maintenanceIntervalInDays: e.target.value }))}
        />
      </div>

      {spec && operationalFields(spec).length > 0 && (
        <div className="flex flex-col gap-2 border-t border-ink-100 pt-3">
          <span className="text-xs font-semibold uppercase tracking-wide text-ink-500">
            {spec.label} fields
          </span>
          <div className="grid grid-cols-2 gap-2">
            {operationalFields(spec).filter(f => f.key !== 'declineCurve').map((f) => (
              <TextField
                key={f.key}
                label={f.label}
                type="number"
                step="any"
                value={typeFields[f.key] ?? ''}
                onChange={(e) =>
                  setTypeFields((prev) => ({ ...prev, [f.key]: e.target.value }))
                }
              />
            ))}
          </div>
        </div>
      )}

      {type === 'WELL' && <fieldset className="flex flex-col gap-2 border-t border-ink-100 pt-3">
        <legend className="text-sm font-medium">Decline curve</legend>
        <label htmlFor="decline-type" className="text-sm">Distribución de declinación</label>
        <select id="decline-type" className={selectClass} value={declineType}
          onChange={e => setDeclineType(e.target.value as typeof declineType)}>
          <option value="CONSTANT">Constante</option><option value="NORMAL">Normal</option><option value="UNIFORM">Uniforme</option>
        </select>
        <TextField label={declineType === 'CONSTANT' ? 'Declinación anual (puntos porcentuales)' : declineType === 'NORMAL' ? 'Media anual (puntos porcentuales)' : 'Mínimo anual (puntos porcentuales)'} type="number" step="any" value={declineA} onChange={e => setDeclineA(e.target.value)} />
        {declineType !== 'CONSTANT' && <TextField label={declineType === 'NORMAL' ? 'Desviación estándar' : 'Máximo anual (puntos porcentuales)'} type="number" step="any" value={declineB} onChange={e => setDeclineB(e.target.value)} />}
        <p className="text-xs text-ink-500">Ingresá 1,45 para una declinación de 1,45 puntos por año. Las muestras se limitan a 0–100 y se acumulan hasta 100.</p>
        {!validDecline && <p role="status" className="text-xs text-red-600">Completá los parámetros: constante entre 0 y 100; uniforme con mínimo menor al máximo entre 0 y 100; normal con desviación mayor a cero.</p>}
      </fieldset>}
      <Button variant="primary" loading={busy} disabled={!base.name.trim() || !validDecline} onClick={submit}>
        {mode === 'create' ? 'Create node' : 'Save node'}
      </Button>
    </div>
  );
}
