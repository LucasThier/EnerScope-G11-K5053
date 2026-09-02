import { useCallback, useEffect, useRef } from 'react';
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  MarkerType,
  useNodesState,
  useEdgesState,
  type Node,
  type Edge,
  type Connection,
  type NodeMouseHandler,
  type EdgeMouseHandler,
  type ReactFlowInstance,
} from '@xyflow/react';
import type { Diagram, DiagramNode } from '../../types/diagram';
import { VERTICAL_COLORS } from './nodeCatalog';
import { EnerNode, type EnerNodeData } from './EnerNode';

interface DiagramCanvasProps {
  diagram: Diagram;
  selectedNodeId: string | null;
  onSelectNode: (nodeId: string | null) => void;
  onMoveNode: (nodeId: string, x: number, y: number) => void;
  onConnect: (fromNodeId: string, toNodeId: string) => void;
  onDeleteNode: (nodeId: string) => void;
  onDeleteConnection: (connectionId: string) => void;
  /**
   * Double-click on empty canvas: create a node. `flowX/flowY` is the diagram
   * position for the new node; `localX/localY` is where to anchor the floating
   * menu, relative to the canvas.
   */
  onCreateAt: (flowX: number, flowY: number, localX: number, localY: number) => void;
}

const nodeTypes = { ener: EnerNode };

function fallbackPosition(index: number): { x: number; y: number } {
  const perRow = 4;
  return { x: 80 + (index % perRow) * 220, y: 80 + Math.floor(index / perRow) * 140 };
}

function toRfNode(node: DiagramNode, index: number, selected: boolean): Node {
  const pos = node.graphData?.graphPosition;
  const position =
    pos && pos.x != null && pos.y != null ? { x: pos.x, y: pos.y } : fallbackPosition(index);
  const data: EnerNodeData = {
    name: node.name,
    state: node.state,
    vertical: node.type.vertical,
    nodeType: node.type.nodeType,
  };
  return {
    id: node.id,
    type: 'ener',
    position,
    selected,
    // Only the selected node can be dragged (select first, then move).
    draggable: selected,
    data,
  };
}

function toRfEdge(fromNodeId: string, toNodeId: string, id: string): Edge {
  return {
    id,
    source: fromNodeId,
    target: toNodeId,
    markerEnd: { type: MarkerType.ArrowClosed, width: 18, height: 18, color: '#4b515a' },
    style: { stroke: '#4b515a', strokeWidth: 1.5 },
  };
}

/**
 * The "diagram" view. Nodes must be selected (single click) before they can be
 * dragged; double-clicking empty canvas creates a node there; double-clicking a
 * connection removes it; dragging from one node's handle to another connects
 * them.
 */
export function DiagramCanvas({
  diagram,
  selectedNodeId,
  onSelectNode,
  onMoveNode,
  onConnect,
  onDeleteNode,
  onDeleteConnection,
  onCreateAt,
}: DiagramCanvasProps) {
  const [nodes, setNodes, onNodesChange] = useNodesState<Node>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);
  const instanceRef = useRef<ReactFlowInstance<Node, Edge> | null>(null);

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

  const handleNodeClick: NodeMouseHandler<Node> = useCallback(
    (_event, node) => onSelectNode(node.id),
    [onSelectNode],
  );

  const handleEdgeDoubleClick: EdgeMouseHandler<Edge> = useCallback(
    (_event, edge) => onDeleteConnection(edge.id),
    [onDeleteConnection],
  );

  const handleWrapperDoubleClick = useCallback(
    (event: React.MouseEvent) => {
      const target = event.target as HTMLElement;
      // Ignore double-clicks on a node or an edge; only empty canvas creates.
      if (target.closest('.react-flow__node') || target.closest('.react-flow__edge')) return;
      const instance = instanceRef.current;
      if (!instance) return;
      const flow = instance.screenToFlowPosition({ x: event.clientX, y: event.clientY });
      const rect = event.currentTarget.getBoundingClientRect();
      onCreateAt(flow.x, flow.y, event.clientX - rect.left, event.clientY - rect.top);
    },
    [onCreateAt],
  );

  return (
    <div className="h-full w-full" onDoubleClick={handleWrapperDoubleClick}>
      <ReactFlow
        nodes={nodes}
        edges={edges}
        nodeTypes={nodeTypes}
        onInit={(instance) => {
          instanceRef.current = instance;
        }}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={handleConnect}
        onNodeClick={handleNodeClick}
        onNodeDragStop={(_e, node) => onMoveNode(node.id, node.position.x, node.position.y)}
        onNodesDelete={(deleted) => deleted.forEach((n) => onDeleteNode(n.id))}
        onEdgeDoubleClick={handleEdgeDoubleClick}
        onEdgesDelete={(deleted) => deleted.forEach((e) => onDeleteConnection(e.id))}
        onPaneClick={() => onSelectNode(null)}
        zoomOnDoubleClick={false}
        fitView
        proOptions={{ hideAttribution: true }}
      >
        <Background />
        <Controls />
        <MiniMap
          pannable
          zoomable
          nodeColor={(n) => VERTICAL_COLORS[(n.data as EnerNodeData).vertical] ?? '#6f767f'}
        />
      </ReactFlow>
    </div>
  );
}
