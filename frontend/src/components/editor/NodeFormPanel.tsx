import { useMemo, useState } from 'react';
import { Button } from '../ui/Button';
import { TextField } from '../ui/TextField';
import { NodeIcon } from './nodeIcons';
import {
  NODE_STATES,
  NODE_TYPE_SPECS,
  VERTICAL_COLORS,
  defaultBaseValues,
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
  onSubmit: (
    spec: NodeTypeSpec,
    base: NodeBaseValues,
    typeFields: Record<string, number>,
  ) => Promise<void>;
  onClose: () => void;
}

const selectClass =
  'rounded-lg border border-ink-200 bg-white px-3 py-2.5 text-sm text-ink-800 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-400/40';

/** Create or edit a node. On edit the type is fixed; on create it is chosen. */
export function NodeFormPanel({ mode, busy, initial, onSubmit, onClose }: NodeFormPanelProps) {
  const [type, setType] = useState<NodeType>(initial?.type ?? 'WELL');
  const [base, setBase] = useState<NodeBaseValues>(initial?.base ?? { ...defaultBaseValues() });
  const [typeFields, setTypeFields] = useState<Record<string, number>>(initial?.typeFields ?? {});

  const spec = useMemo(() => specForType(type), [type]);
  const color = spec ? VERTICAL_COLORS[spec.vertical] : '#6f767f';

  function num(v: string): number {
    const n = Number(v);
    return Number.isFinite(n) ? n : 0;
  }

  async function submit() {
    if (!spec || !base.name.trim()) return;
    await onSubmit(spec, base, typeFields);
    onClose();
  }

  return (
    <div className="flex max-h-full flex-col gap-3 overflow-y-auto p-4">
      <div className="flex items-center justify-between">
        <h3 className="flex items-center gap-2 text-sm font-semibold text-ink-700">
          <span
            className="flex h-6 w-6 items-center justify-center rounded-md text-white"
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
          {NODE_STATES.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
      </div>

      <div className="grid grid-cols-2 gap-2">
        <TextField
          label="Upkeep cost"
          type="number"
          value={base.upkeepCosts}
          onChange={(e) => setBase((b) => ({ ...b, upkeepCosts: num(e.target.value) }))}
        />
        <TextField
          label="Operating cost"
          type="number"
          value={base.operatingCosts}
          onChange={(e) => setBase((b) => ({ ...b, operatingCosts: num(e.target.value) }))}
        />
        <TextField
          label="Lifespan (months)"
          type="number"
          value={base.lifespanInMonths}
          onChange={(e) => setBase((b) => ({ ...b, lifespanInMonths: num(e.target.value) }))}
        />
        <TextField
          label="Maint. interval (days)"
          type="number"
          value={base.maintenanceIntervalInDays}
          onChange={(e) => setBase((b) => ({ ...b, maintenanceIntervalInDays: num(e.target.value) }))}
        />
      </div>

      {spec && spec.fields.length > 0 && (
        <div className="flex flex-col gap-2 border-t border-ink-100 pt-3">
          <span className="text-xs font-semibold uppercase tracking-wide text-ink-400">
            {spec.label} fields
          </span>
          <div className="grid grid-cols-2 gap-2">
            {spec.fields.map((f) => (
              <TextField
                key={f.key}
                label={f.label}
                type="number"
                value={typeFields[f.key] ?? 0}
                onChange={(e) =>
                  setTypeFields((prev) => ({ ...prev, [f.key]: num(e.target.value) }))
                }
              />
            ))}
          </div>
        </div>
      )}

      <Button variant="primary" loading={busy} disabled={!base.name.trim()} onClick={submit}>
        {mode === 'create' ? 'Create node' : 'Save node'}
      </Button>
    </div>
  );
}
