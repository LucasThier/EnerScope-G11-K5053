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

interface StateStyle {
  label: string;
  bg: string;
  text: string;
  border: 'solid' | 'dashed';
}

const STATE_STYLES: Record<NodeState, StateStyle> = {
  RUNNING: { label: 'Running', bg: '#d6efc6', text: '#326626', border: 'solid' },
  PROPOSED: { label: 'Proposed', bg: '#eaf6de', text: '#55a238', border: 'dashed' },
  PENDING: { label: 'Pending', bg: '#fbf0d6', text: '#9a6f1c', border: 'dashed' },
  REMOVED: { label: 'Removed', bg: '#f3dede', text: '#b04b4b', border: 'solid' },
};

/** Custom diagram node: type icon, name, and a state dot, accented by vertical. */
export function EnerNode({ data, selected }: NodeProps) {
  const d = data as EnerNodeData;
  const color = VERTICAL_COLORS[d.vertical] ?? '#6f767f';
  const st = STATE_STYLES[d.state];
  return (
    <div
      className="flex w-[172px] items-center gap-2 rounded-xl border-2 bg-white px-3 py-2 shadow-sm"
      style={{
        borderColor: color,
        borderStyle: st.border,
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
        <div className="flex items-center gap-1 text-[10px]">
          <span
            className="shrink-0 rounded px-1 py-px font-semibold uppercase tracking-wide"
            style={{ background: st.bg, color: st.text }}
          >
            {st.label}
          </span>
          <span className="truncate text-ink-400">{d.nodeType.replace(/_/g, ' ').toLowerCase()}</span>
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
