import { Handle, Position, type NodeProps } from '@xyflow/react';
import type { NodeState, NodeType, Vertical } from '../../types/diagram';
import { VERTICAL_COLORS } from './nodeCatalog';
import { NodeIcon } from './nodeIcons';

export interface EnerNodeData {
  name: string;
  state: NodeState;
  vertical: Vertical;
  nodeType: NodeType;
  [key: string]: unknown;
}

const STATE_COLORS: Record<NodeState, string> = {
  RUNNING: '#3f8130',
  PROPOSED: '#83cd58',
  PENDING: '#e0a63a',
  REMOVED: '#b04b4b',
};

/** Custom diagram node: type icon, name, and a state dot, accented by vertical. */
export function EnerNode({ data, selected }: NodeProps) {
  const d = data as EnerNodeData;
  const color = VERTICAL_COLORS[d.vertical] ?? '#6f767f';
  return (
    <div
      className="flex w-[172px] items-center gap-2 rounded-xl border-2 bg-white px-3 py-2 shadow-sm"
      style={{
        borderColor: color,
        boxShadow: selected ? `0 0 0 3px ${color}55` : undefined,
        opacity: d.state === 'REMOVED' ? 0.5 : 1,
      }}
    >
      <Handle type="target" position={Position.Left} style={{ background: color, width: 9, height: 9 }} />
      <span
        className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-white"
        style={{ background: color }}
      >
        <NodeIcon type={d.nodeType} className="h-5 w-5" />
      </span>
      <div className="min-w-0 flex-1">
        <div className="truncate text-sm font-semibold text-ink-700" title={d.name}>
          {d.name || '(unnamed)'}
        </div>
        <div className="flex items-center gap-1 text-[10px] text-ink-400">
          <span
            className="inline-block h-2 w-2 shrink-0 rounded-full"
            style={{ background: STATE_COLORS[d.state] }}
            title={d.state}
          />
          <span className="truncate">{d.nodeType.replace(/_/g, ' ').toLowerCase()}</span>
        </div>
      </div>
      <Handle type="source" position={Position.Right} style={{ background: color, width: 9, height: 9 }} />
    </div>
  );
}
