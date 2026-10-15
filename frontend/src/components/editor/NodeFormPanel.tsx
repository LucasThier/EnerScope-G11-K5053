import { useMemo, useState, type ReactNode } from 'react';
import { Button } from '../ui/Button';
import { TextField } from '../ui/TextField';
import { PlusIcon, TrashIcon } from '../ui/icons';
import { NodeIcon } from './nodeIcons';
import {
  COST_BASES,
  COST_BASIS_LABELS,
  DISTRIBUTION_LABELS,
  DISTRIBUTION_PARAMETERS,
  NODE_STATE_LABELS,
  NODE_STATES,
  NODE_TYPE_SPECS,
  VERTICAL_COLORS,
  defaultBaseValues,
  defaultDeclineCurve,
  defaultTypeFields,
  distributionError,
  specForType,
  type NodeBaseValues,
  type NodeExtras,
  type NodeTypeSpec,
} from './nodeCatalog';
import type {
  CostBasis,
  Distribution,
  DistributionType,
  InvestmentComponent,
  NodeState,
  NodeType,
} from '../../types/diagram';

export interface NodeFormInitial {
  type: NodeType;
  base: NodeBaseValues;
  typeFields: Record<string, number>;
  declineCurve: Distribution | null;
  investmentCost: InvestmentComponent[];
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
    extras: NodeExtras,
  ) => Promise<void>;
  onClose: () => void;
}

const selectClass =
  'rounded-lg border border-ink-200 bg-white px-3 py-2 text-sm text-ink-800 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-400/40';

function num(v: string): number {
  const n = Number(v);
  return Number.isFinite(n) ? n : 0;
}

/** A labelled group of fields inside the panel. */
function Section({ title, hint, children }: { title: string; hint?: string; children: ReactNode }) {
  return (
    <fieldset className="flex flex-col gap-2 border-t border-ink-100 pt-3">
      <legend className="text-xs font-semibold uppercase tracking-wide text-ink-500">{title}</legend>
      {hint && <p className="-mt-1 text-xs text-ink-500">{hint}</p>}
      {children}
    </fieldset>
  );
}

/**
 * Create or edit a node. On edit the type is fixed; on create it is chosen.
 * Besides the type's own numbers it edits what the simulation and the cost
 * calculation read: lifespan and maintenance, the monthly operating cost and the
 * cost per maintenance stop, the investment components (CAPEX) and, for a well,
 * the decline curve as a distribution.
 */
export function NodeFormPanel({
  mode,
  busy,
  initial,
  initialType,
  onSubmit,
  onClose,
}: NodeFormPanelProps) {
  const startType = initial?.type ?? initialType ?? 'WELL';
  const [type, setType] = useState<NodeType>(startType);
  const [base, setBase] = useState<NodeBaseValues>(initial?.base ?? { ...defaultBaseValues() });
  const [typeFields, setTypeFields] = useState<Record<string, number>>(
    initial?.typeFields ?? defaultTypeFields(specForType(startType)),
  );
  const [declineCurve, setDeclineCurve] = useState<Distribution>(
    initial?.declineCurve ?? defaultDeclineCurve(),
  );
  const [components, setComponents] = useState<InvestmentComponent[]>(initial?.investmentCost ?? []);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const spec = useMemo(() => specForType(type), [type]);
  const color = spec ? VERTICAL_COLORS[spec.vertical] : '#6f767f';
  // REMOVED is set by deleting a node, not chosen here; only expose it so an
  // already-removed node's state still renders while editing.
  const stateOptions: NodeState[] =
    base.state === 'REMOVED' ? ['REMOVED', ...NODE_STATES] : NODE_STATES;

  const problem = useMemo(() => {
    if (!base.name.trim()) return 'Poné un nombre al nodo.';
    if (base.lifespanInMonths < 1) {
      return 'La vida útil tiene que ser de al menos 1 mes: con 0 el nodo se apaga en la primera hora.';
    }
    if (base.maintenanceIntervalInDays < 1) {
      return 'El intervalo de mantenimiento tiene que ser de al menos 1 día.';
    }
    if (base.maintenanceDuration < 0) return 'La duración del mantenimiento no puede ser negativa.';
    const tooHigh = spec?.fields.find((f) => f.max != null && (typeFields[f.key] ?? 0) > f.max);
    if (tooHigh) return `${tooHigh.label}: el máximo es ${tooHigh.max}.`;
    const negative = spec?.fields.find((f) => (typeFields[f.key] ?? 0) < 0);
    if (negative) return `${negative.label} no puede ser negativo.`;
    if (spec?.hasDeclineCurve) {
      const curveError = distributionError(declineCurve);
      if (curveError) return `Curva de declinación: ${curveError}`;
    }
    if (components.some((c) => c.name.trim() && c.amount < 0)) {
      return 'Los montos de inversión no pueden ser negativos.';
    }
    return null;
  }, [base, spec, typeFields, declineCurve, components]);

  async function submit() {
    if (!spec || problem) return;
    setSubmitError(null);
    try {
      await onSubmit(spec, base, typeFields, {
        declineCurve: spec.hasDeclineCurve ? declineCurve : null,
        investmentCost: components,
      });
      onClose();
    } catch {
      // useDiagram already surfaces the API error above the canvas; keep the
      // panel open with what the user typed so nothing is lost.
      setSubmitError('No se pudo guardar el nodo. Revisá el mensaje arriba del lienzo.');
    }
  }

  function setBaseNumber(key: keyof NodeBaseValues, value: string) {
    setBase((b) => ({ ...b, [key]: num(value) }));
  }

  function updateComponent(index: number, patch: Partial<InvestmentComponent>) {
    setComponents((list) => list.map((c, i) => (i === index ? { ...c, ...patch } : c)));
  }

  const [paramA, paramB] = DISTRIBUTION_PARAMETERS[declineCurve.type];

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
          {mode === 'create' ? 'Nuevo nodo' : 'Editar nodo'}
        </h3>
        <Button variant="ghost" onClick={onClose} className="px-2 py-1 text-xs">
          Cerrar
        </Button>
      </div>

      <div className="flex flex-col gap-2">
        <label htmlFor="node-form-type" className="text-sm font-medium text-ink-600">
          Tipo
        </label>
        <select
          id="node-form-type"
          value={type}
          disabled={mode === 'edit'}
          onChange={(e) => {
            const next = e.target.value as NodeType;
            setType(next);
            setTypeFields(defaultTypeFields(specForType(next)));
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
        label="Nombre"
        value={base.name}
        onChange={(e) => setBase((b) => ({ ...b, name: e.target.value }))}
        placeholder="Ej.: Pozo Norte 1"
      />

      <div className="flex flex-col gap-2">
        <label htmlFor="node-form-state" className="text-sm font-medium text-ink-600">
          Estado
        </label>
        <select
          id="node-form-state"
          value={base.state}
          onChange={(e) => setBase((b) => ({ ...b, state: e.target.value as NodeState }))}
          className={selectClass}
        >
          {stateOptions.map((s) => (
            <option key={s} value={s}>
              {NODE_STATE_LABELS[s]}
            </option>
          ))}
        </select>
      </div>

      {spec && spec.fields.length > 0 && (
        <Section title={`Parámetros de ${spec.label.toLowerCase()}`}>
          <div className="grid grid-cols-2 gap-2">
            {spec.fields.map((f) => (
              <TextField
                key={f.key}
                label={f.label}
                type="number"
                min={0}
                max={f.max}
                step={f.integer ? 1 : 'any'}
                value={typeFields[f.key] ?? 0}
                onChange={(e) => setTypeFields((prev) => ({ ...prev, [f.key]: num(e.target.value) }))}
              />
            ))}
          </div>
        </Section>
      )}

      {spec?.hasDeclineCurve && (
        <Section
          title="Curva de declinación"
          hint="Cuánto cae la producción del pozo cada año, en %. Uniforme o normal hace que cada ronda de la simulación sortee un valor distinto."
        >
          <label className="flex flex-col gap-2">
            <span className="text-sm font-medium text-ink-600">Distribución</span>
            <select
              value={declineCurve.type}
              onChange={(e) => {
                const next = e.target.value as DistributionType;
                setDeclineCurve((curve) => ({
                  type: next,
                  val_a: curve.val_a,
                  val_b: next === 'CONSTANT' ? null : (curve.val_b ?? (next === 'UNIFORM' ? curve.val_a + 2 : 1)),
                }));
              }}
              className={selectClass}
            >
              {(Object.keys(DISTRIBUTION_LABELS) as DistributionType[]).map((d) => (
                <option key={d} value={d}>
                  {DISTRIBUTION_LABELS[d]}
                </option>
              ))}
            </select>
          </label>
          <div className="grid grid-cols-2 gap-2">
            <TextField
              label={`${paramA} (%)`}
              type="number"
              step="any"
              value={declineCurve.val_a}
              onChange={(e) => setDeclineCurve((curve) => ({ ...curve, val_a: num(e.target.value) }))}
            />
            {paramB && (
              <TextField
                label={`${paramB} (%)`}
                type="number"
                step="any"
                value={declineCurve.val_b ?? 0}
                onChange={(e) => setDeclineCurve((curve) => ({ ...curve, val_b: num(e.target.value) }))}
              />
            )}
          </div>
        </Section>
      )}

      <Section title="Vida útil y mantenimiento">
        <div className="grid grid-cols-2 gap-2">
          <TextField
            label="Vida útil (meses)"
            type="number"
            min={1}
            value={base.lifespanInMonths}
            onChange={(e) => setBaseNumber('lifespanInMonths', e.target.value)}
          />
          <TextField
            label="Mantenimiento cada (días)"
            type="number"
            min={1}
            value={base.maintenanceIntervalInDays}
            onChange={(e) => setBaseNumber('maintenanceIntervalInDays', e.target.value)}
          />
          <TextField
            label="Duración del mantenimiento (horas)"
            type="number"
            min={0}
            value={base.maintenanceDuration}
            onChange={(e) => setBaseNumber('maintenanceDuration', e.target.value)}
          />
          <TextField
            label="Merma (%)"
            type="number"
            min={0}
            step="any"
            value={base.wastePercentage}
            onChange={(e) => setBaseNumber('wastePercentage', e.target.value)}
          />
        </div>
      </Section>

      <Section title="Costos de operación">
        <div className="grid grid-cols-2 gap-2">
          <TextField
            label="Operación (por mes)"
            type="number"
            min={0}
            step="any"
            value={base.operatingCosts}
            onChange={(e) => setBaseNumber('operatingCosts', e.target.value)}
          />
          <TextField
            label="Cada mantenimiento"
            type="number"
            min={0}
            step="any"
            value={base.upkeepCosts}
            onChange={(e) => setBaseNumber('upkeepCosts', e.target.value)}
          />
        </div>
      </Section>

      <Section
        title="Inversión (CAPEX)"
        hint="Se suma una sola vez. Un componente por km o por m usa la longitud del nodo; uno que no aplica a este tipo de nodo suma 0."
      >
        {components.length === 0 && <p className="text-xs text-ink-500">Sin componentes de inversión.</p>}
        {components.map((component, index) => (
          <div key={index} className="flex flex-col gap-2 rounded-lg border border-ink-100 p-2">
            <div className="flex items-end gap-2">
              <div className="min-w-0 flex-1">
                <TextField
                  label="Concepto"
                  value={component.name}
                  onChange={(e) => updateComponent(index, { name: e.target.value })}
                  placeholder="Ej.: Perforación"
                />
              </div>
              <button
                type="button"
                onClick={() => setComponents((list) => list.filter((_, i) => i !== index))}
                aria-label={`Quitar el componente ${component.name || index + 1}`}
                title="Quitar"
                className="mb-1 rounded-lg p-1.5 text-ink-500 hover:bg-ink-50 hover:text-danger-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-400"
              >
                <TrashIcon className="h-4 w-4" />
              </button>
            </div>
            <div className="grid grid-cols-2 gap-2">
              <TextField
                label="Monto"
                type="number"
                min={0}
                step="any"
                value={component.amount}
                onChange={(e) => updateComponent(index, { amount: num(e.target.value) })}
              />
              <label className="flex flex-col gap-2">
                <span className="text-sm font-medium text-ink-600">Base</span>
                <select
                  value={component.costBasis}
                  onChange={(e) => updateComponent(index, { costBasis: e.target.value as CostBasis })}
                  className={selectClass}
                >
                  {COST_BASES.map((basis) => (
                    <option key={basis} value={basis}>
                      {COST_BASIS_LABELS[basis]}
                    </option>
                  ))}
                </select>
              </label>
            </div>
          </div>
        ))}
        <Button
          variant="ghost"
          onClick={() => setComponents((list) => [...list, { name: '', amount: 0, costBasis: 'FLAT' }])}
          className="self-start px-2 py-1 text-xs"
        >
          <PlusIcon className="h-4 w-4" />
          Agregar componente
        </Button>
      </Section>

      {/* A missing name only disables the button: shouting about it before
          the user has typed anything would be noise. */}
      {(submitError || (problem && base.name.trim())) && (
        <p role="status" className="text-xs text-danger-700">
          {submitError ?? problem}
        </p>
      )}

      <Button variant="primary" loading={busy} disabled={!!problem} onClick={submit}>
        {mode === 'create' ? 'Crear nodo' : 'Guardar cambios'}
      </Button>
    </div>
  );
}
