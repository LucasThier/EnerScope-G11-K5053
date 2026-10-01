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
import type { Diagram, DiagramNode, NodeType } from '../../types/diagram';
import { VERTICAL_COLORS } from './nodeCatalog';
import { NODE_DRAG_TYPE } from './NodePalette';
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
  /** A node type was dropped from the palette at the given canvas position. */
  onDropNode: (
    type: NodeType,
    flowX: number,
    flowY: number,
    localX: number,
    localY: number,
  ) => void;
  /** Double-click a node: open its editable form. */
  onEditNode: (nodeId: string) => void;
  /** A connection is being drawn (click-to-connect); shows a crosshair cursor. */
  connecting?: boolean;
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
    type: 'smoothstep',
    markerEnd: { type: MarkerType.ArrowClosed, width: 20, height: 20, color: '#4b515a' },
    // `cursor: pointer` signals the edge is clickable (double-click removes it).
    style: { stroke: '#4b515a', strokeWidth: 1.75, cursor: 'pointer' },
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
  onDropNode,
  onEditNode,
  connecting = false,
}: DiagramCanvasProps) {
  const [nodes, setNodes, onNodesChange] = useNodesState<Node>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);
  const instanceRef = useRef<ReactFlowInstance<Node, Edge> | null>(null);
  // Track a drag-connection so a drag that doesn't reach another node counts as
  // a plain click (select), not a failed connection.
  const connectStartNodeRef = useRef<string | null>(null);
  const connectionMadeRef = useRef(false);

  useEffect(() => {
    setNodes(diagram.nodes.map((n, i) => toRfNode(n, i, n.id === selectedNodeId)));
  }, [diagram.nodes, selectedNodeId, setNodes]);

  useEffect(() => {
    setEdges(diagram.connections.map((c) => toRfEdge(c.fromNodeId, c.toNodeId, c.id)));
  }, [diagram.connections, setEdges]);

  const handleConnect = useCallback(
    (connection: Connection) => {
      if (connection.source && connection.target && connection.source !== connection.target) {
        connectionMadeRef.current = true;
        onConnect(connection.source, connection.target);
      }
    },
    [onConnect],
  );

  const handleNodeClick: NodeMouseHandler<Node> = useCallback(
    (_event, node) => onSelectNode(node.id),
    [onSelectNode],
  );

  const handleNodeDoubleClick: NodeMouseHandler<Node> = useCallback(
    (_event, node) => onEditNode(node.id),
    [onEditNode],
  );

  const handleDragOver = useCallback((event: React.DragEvent) => {
    event.preventDefault();
    event.dataTransfer.dropEffect = 'copy';
  }, []);

  const handleDrop = useCallback(
    (event: React.DragEvent) => {
      event.preventDefault();
      const type = event.dataTransfer.getData(NODE_DRAG_TYPE) as NodeType;
      if (!type) return;
      const instance = instanceRef.current;
      if (!instance) return;
      const flow = instance.screenToFlowPosition({ x: event.clientX, y: event.clientY });
      const rect = event.currentTarget.getBoundingClientRect();
      onDropNode(type, flow.x, flow.y, event.clientX - rect.left, event.clientY - rect.top);
    },
    [onDropNode],
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
    <div
      className={
        'h-full w-full ' +
        (connecting ? '[&_.react-flow__pane]:!cursor-crosshair' : '')
      }
      onDoubleClick={handleWrapperDoubleClick}
      onDragOver={handleDragOver}
      onDrop={handleDrop}
    >
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
        onConnectStart={(_event, params) => {
          connectStartNodeRef.current = params.nodeId ?? null;
          connectionMadeRef.current = false;
        }}
        onConnectEnd={() => {
          // Dragging from the node body but not onto another node = a select.
          if (!connectionMadeRef.current && connectStartNodeRef.current) {
            onSelectNode(connectStartNodeRef.current);
          }
          connectStartNodeRef.current = null;
        }}
        connectionRadius={45}
        onNodeClick={handleNodeClick}
        onNodeDoubleClick={handleNodeDoubleClick}
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
