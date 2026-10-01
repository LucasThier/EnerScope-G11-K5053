import type { NodeType } from '../../types/diagram';
import { NODE_TYPE_SPECS, VERTICAL_COLORS } from './nodeCatalog';
import { NodeIcon } from './nodeIcons';

/** MIME-ish key carried on the drag so the canvas knows which node was dropped. */
export const NODE_DRAG_TYPE = 'application/enerscope-node';

interface NodePaletteProps {
  /** Click a tile to add that node type (drag-and-drop is the primary path). */
  onPick: (type: NodeType) => void;
  disabled?: boolean;
}

/**
 * Vertical rail of every node type. Each tile can be dragged onto the canvas to
 * place a node at the drop point, or clicked to add one near the current spread.
 */
export function NodePalette({ onPick, disabled = false }: NodePaletteProps) {
  return (
    <aside className="z-10 flex w-48 shrink-0 flex-col border-r border-ink-100 bg-white">
      <div className="border-b border-ink-100 px-3 py-2.5">
        <h3 className="text-xs font-semibold uppercase tracking-wide text-ink-400">Nodes</h3>
        <p className="mt-0.5 text-[11px] leading-tight text-ink-400">Drag onto the canvas</p>
      </div>
      <div className="flex flex-1 flex-col gap-1 overflow-y-auto p-2">
        {NODE_TYPE_SPECS.map((spec) => {
          const color = VERTICAL_COLORS[spec.vertical];
          return (
            <button
              key={spec.type}
              type="button"
              draggable={!disabled}
              onDragStart={(e) => {
                e.dataTransfer.setData(NODE_DRAG_TYPE, spec.type);
                e.dataTransfer.effectAllowed = 'copy';
              }}
              onClick={() => !disabled && onPick(spec.type)}
              disabled={disabled}
              title={`${spec.label} — drag onto the canvas or click to add`}
              className="group flex items-center gap-2.5 rounded-lg border border-transparent px-2 py-1.5 text-left transition-colors hover:border-ink-100 hover:bg-ink-50 disabled:opacity-50"
              style={{ cursor: disabled ? 'not-allowed' : 'grab' }}
            >
              <span
                className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-white shadow-sm"
                style={{ background: color }}
              >
                <NodeIcon type={spec.type} className="h-5 w-5" />
              </span>
              <span className="min-w-0 flex-1 truncate text-sm font-medium text-ink-700">
                {spec.label}
              </span>
            </button>
          );
        })}
      </div>
    </aside>
  );
}
