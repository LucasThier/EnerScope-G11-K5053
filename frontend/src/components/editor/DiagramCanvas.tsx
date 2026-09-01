import { useCallback, useEffect, useMemo } from 'react';
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  useNodesState,
  useEdgesState,
  type Node,
  type Edge,
  type Connection,
  type NodeMouseHandler,
} from '@xyflow/react';
import type { Diagram, DiagramNode } from '../../types/diagram';
import { VERTICAL_COLORS } from './nodeCatalog';

interface DiagramCanvasProps {
  diagram: Diagram;
  selectedNodeId: string | null;
  onSelectNode: (nodeId: string | null) => void;
  onMoveNode: (nodeId: string, x: number, y: number) => void;
  onConnect: (fromNodeId: string, toNodeId: string) => void;
  onDeleteNode: (nodeId: string) => void;
  onDeleteConnection: (connectionId: string) => void;
}

/** Fallback grid position for nodes that have no diagram position yet. */
function fallbackPosition(index: number): { x: number; y: number } {
  const perRow = 4;
  return { x: 80 + (index % perRow) * 220, y: 80 + Math.floor(index / perRow) * 140 };
}

function toRfNode(node: DiagramNode, index: number, selected: boolean): Node {
  const pos = node.graphData?.graphPosition;
  const position =
    pos && pos.x != null && pos.y != null ? { x: pos.x, y: pos.y } : fallbackPosition(index);
  const color = VERTICAL_COLORS[node.type.vertical];
  return {
    id: node.id,
    position,
    data: { label: node.name || '(unnamed)' },
    style: {
      background: color,
      color: '#ffffff',
      border: selected ? '3px solid #23262b' : '1px solid rgba(0,0,0,0.2)',
      borderRadius: 10,
      padding: '8px 12px',
      fontSize: 12,
      fontWeight: 600,
      width: 160,
      textAlign: 'center' as const,
      opacity: node.state === 'REMOVED' ? 0.45 : 1,
    },
  };
}

function toRfEdge(fromNodeId: string, toNodeId: string, id: string): Edge {
  return { id, source: fromNodeId, target: toNodeId, animated: true, style: { stroke: '#4b515a' } };
}

/**
 * The "diagram" view: an interactive React Flow canvas. Nodes are positioned by
 * their graph (x/y) position, dragging persists via `onMoveNode`, and dragging
 * from one node to another creates a connection.
 */
export function DiagramCanvas({
  diagram,
  selectedNodeId,
  onSelectNode,
  onMoveNode,
  onConnect,
  onDeleteNode,
  onDeleteConnection,
}: DiagramCanvasProps) {
  const initialNodes = useMemo(
    () => diagram.nodes.map((n, i) => toRfNode(n, i, n.id === selectedNodeId)),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [],
  );
  const initialEdges = useMemo(
    () => diagram.connections.map((c) => toRfEdge(c.fromNodeId, c.toNodeId, c.id)),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [],
  );

  const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges);

  // Keep React Flow in sync with the diagram (after loads / structural changes).
  useEffect(() => {
    setNodes(diagram.nodes.map((n, i) => toRfNode(n, i, n.id === selectedNodeId)));
  }, [diagram.nodes, selectedNodeId, setNodes]);

  useEffect(() => {
    setEdges(diagram.connections.map((c) => toRfEdge(c.fromNodeId, c.toNodeId, c.id)));
  }, [diagram.connections, setEdges]);

  const handleConnect = useCallback(
    (connection: Connection) => {
      if (connection.source && connection.target && connection.source !== connection.target) {
        onConnect(connection.source, connection.target);
      }
    },
    [onConnect],
  );

  const handleNodeClick: NodeMouseHandler = useCallback(
    (_event, node) => onSelectNode(node.id),
    [onSelectNode],
  );

  return (
    <ReactFlow
      nodes={nodes}
      edges={edges}
      onNodesChange={onNodesChange}
      onEdgesChange={onEdgesChange}
      onConnect={handleConnect}
      onNodeClick={handleNodeClick}
      onNodeDragStop={(_e, node) => onMoveNode(node.id, node.position.x, node.position.y)}
      onNodesDelete={(deleted) => deleted.forEach((n) => onDeleteNode(n.id))}
      onEdgesDelete={(deleted) => deleted.forEach((e) => onDeleteConnection(e.id))}
      onPaneClick={() => onSelectNode(null)}
      fitView
      proOptions={{ hideAttribution: true }}
    >
      <Background />
      <Controls />
      <MiniMap pannable zoomable />
    </ReactFlow>
  );
}
