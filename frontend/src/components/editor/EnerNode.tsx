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
      <Handle
        id="in"
        type="target"
        position={Position.Left}
        style={{ background: color, width: 11, height: 11 }}
      />
      {/* When not selected, the whole node is a connection source: drag from
          anywhere on it to connect. When selected, it is dragged to move. */}
      {!selected && (
        <Handle
          id="body"
          type="source"
          position={Position.Right}
          style={{
            position: 'absolute',
            left: 0,
            top: 0,
            width: '100%',
            height: '100%',
            transform: 'none',
            borderRadius: 12,
            border: 'none',
            background: 'transparent',
            opacity: 0,
            zIndex: 1,
          }}
        />
      )}
      <span
        className="pointer-events-none z-[2] flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-white"
        style={{ background: color }}
      >
        <NodeIcon type={d.nodeType} className="h-5 w-5" />
      </span>
      <div className="pointer-events-none z-[2] min-w-0 flex-1">
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
      <Handle
        id="out"
        type="source"
        position={Position.Right}
        style={{ background: color, width: 11, height: 11, zIndex: 3 }}
      />
    </div>
  );
}
