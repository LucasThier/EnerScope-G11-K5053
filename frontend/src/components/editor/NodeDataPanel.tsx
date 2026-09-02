import { useEffect, useState } from 'react';
import { Button } from '../ui/Button';
import { TextField } from '../ui/TextField';
import { NODE_STATES, VERTICAL_COLORS } from './nodeCatalog';
import type { DiagramNode, NodeState } from '../../types/diagram';

interface NodeDataPanelProps {
  node: DiagramNode | null;
  busy: boolean;
  onUpdateBasics: (nodeId: string, basics: { name?: string; state?: NodeState }) => void;
  onEditData: (nodeId: string) => void;
  onStartConnect: (nodeId: string) => void;
  onDelete: (nodeId: string) => void;
}

function fmt(n: number | null | undefined): string {
  return n == null ? '—' : String(Number(n.toFixed(4)));
}

/** Details of the selected node, with lightweight rename/state edit and delete. */
export function NodeDataPanel({
  node,
  busy,
  onUpdateBasics,
  onEditData,
  onStartConnect,
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
      <div className="p-4 text-sm text-ink-400">
        Select a node to see its data, or add one from the palette.
      </div>
    );
  }

  const dirty = name !== node.name || state !== node.state;
  const graph = node.graphData?.graphPosition;
  const geo = node.graphData?.geographicalPosition;

  return (
    <div className="flex flex-col gap-4 p-4">
      <div className="flex items-center gap-2">
        <span
          className="inline-block h-3 w-3 rounded-full"
          style={{ background: VERTICAL_COLORS[node.type.vertical] }}
        />
        <h3 className="text-sm font-semibold text-ink-700">Node data</h3>
      </div>

      <TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} />

      <div className="flex flex-col gap-1.5">
        <label htmlFor="node-state" className="text-sm font-medium text-ink-600">
          State
        </label>
        <select
          id="node-state"
          value={state}
          onChange={(e) => setState(e.target.value as NodeState)}
          className="rounded-lg border border-ink-200 bg-white px-3 py-2.5 text-sm text-ink-800 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-400/40"
        >
          {NODE_STATES.map((s) => (
            <option key={s} value={s}>
              {s}
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
          Save
        </Button>
        <Button variant="secondary" onClick={() => onEditData(node.id)} className="flex-1">
          Edit all data
        </Button>
      </div>

      <dl className="grid grid-cols-2 gap-x-3 gap-y-2 border-t border-ink-100 pt-3 text-xs">
        <dt className="text-ink-400">Type</dt>
        <dd className="text-ink-700">{node.type.nodeType}</dd>
        <dt className="text-ink-400">Vertical</dt>
        <dd className="text-ink-700">{node.type.vertical}</dd>
        <dt className="text-ink-400">Role</dt>
        <dd className="text-ink-700">{node.type.role}</dd>
        <dt className="text-ink-400">Canvas x / y</dt>
        <dd className="text-ink-700">
          {fmt(graph?.x)} / {fmt(graph?.y)}
        </dd>
        <dt className="text-ink-400">Lng / Lat</dt>
        <dd className="text-ink-700">
          {fmt(geo?.longitude)} / {fmt(geo?.latitude)}
        </dd>
        <dt className="text-ink-400">Id</dt>
        <dd className="truncate text-ink-500" title={node.id}>
          {node.id.slice(0, 8)}…
        </dd>
      </dl>

      <Button variant="secondary" onClick={() => onStartConnect(node.id)}>
        Connect to another node…
      </Button>

      <Button variant="ghost" className="text-red-600 hover:bg-red-50" onClick={() => onDelete(node.id)}>
        Delete node
      </Button>
    </div>
  );
}
