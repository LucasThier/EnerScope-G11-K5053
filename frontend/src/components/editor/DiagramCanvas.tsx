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
  type AriaLabelConfig,
} from '@xyflow/react';
import { useTheme } from '../../hooks/useTheme';
import type { Diagram, DiagramNode, NodeType } from '../../types/diagram';
import { CONNECTION_COLORS, verticalColor } from './nodeCatalog';
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
  /** Create, move and delete nodes. Deny by default. */
  canEdit?: boolean;
  /** Create and delete connections. Deny by default. */
  canEditConnections?: boolean;
}

const nodeTypes = { ener: EnerNode };

const MOVE_DIRECTIONS: Record<string, string> = {
  up: 'arriba',
  down: 'abajo',
  left: 'la izquierda',
  right: 'la derecha',
};

const FLOW_ARIA_LABELS: Partial<AriaLabelConfig> = {
  'node.a11yDescription.default':
    'Presioná Enter o la barra espaciadora para seleccionar un nodo. Presioná Suprimir para eliminarlo y Escape para cancelar.',
  'node.a11yDescription.keyboardDisabled':
    'Presioná Enter o la barra espaciadora para seleccionar un nodo. Después podés moverlo con las flechas. Presioná Suprimir para eliminarlo y Escape para cancelar.',
  'node.a11yDescription.ariaLiveMessage': ({ direction, x, y }) =>
    `Se movió el nodo seleccionado hacia ${MOVE_DIRECTIONS[direction] ?? direction}. Nueva posición: x ${x}, y ${y}.`,
  'edge.a11yDescription.default':
    'Presioná Enter o la barra espaciadora para seleccionar una conexión. Después podés presionar Suprimir para eliminarla o Escape para cancelar.',
  'controls.ariaLabel': 'Panel de controles',
  'controls.zoomIn.ariaLabel': 'Acercar',
  'controls.zoomOut.ariaLabel': 'Alejar',
  'controls.fitView.ariaLabel': 'Ajustar la vista',
  'controls.interactive.ariaLabel': 'Activar o desactivar la edición',
  'minimap.ariaLabel': 'Minimapa',
  'handle.ariaLabel': 'Punto de conexión',
};

function fallbackPosition(index: number): { x: number; y: number } {
  const perRow = 4;
  return { x: 80 + (index % perRow) * 220, y: 80 + Math.floor(index / perRow) * 140 };
}

function toRfNode(node: DiagramNode, index: number, selected: boolean, canEdit: boolean): Node {
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
    draggable: selected && canEdit,
    deletable: canEdit,
    data,
  };
}

function toRfEdge(
  fromNodeId: string,
  toNodeId: string,
  id: string,
  stroke: string,
  canEditConnections: boolean,
): Edge {
  return {
    id,
    source: fromNodeId,
    target: toNodeId,
    type: 'smoothstep',
    deletable: canEditConnections,
    markerEnd: { type: MarkerType.ArrowClosed, width: 20, height: 20, color: stroke },
    // `cursor: pointer` signals the edge is clickable (double-click removes it).
    style: { stroke, strokeWidth: 1.75, cursor: 'pointer' },
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
  canEdit = false,
  canEditConnections = false,
}: DiagramCanvasProps) {
  const { resolved } = useTheme();
  const [nodes, setNodes, onNodesChange] = useNodesState<Node>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);
  const instanceRef = useRef<ReactFlowInstance<Node, Edge> | null>(null);
  // Track a drag-connection so a drag that doesn't reach another node counts as
  // a plain click (select), not a failed connection.
  const connectStartNodeRef = useRef<string | null>(null);
  const connectionMadeRef = useRef(false);

  useEffect(() => {
    setNodes(diagram.nodes.map((n, i) => toRfNode(n, i, n.id === selectedNodeId, canEdit)));
  }, [diagram.nodes, selectedNodeId, canEdit, setNodes]);

  useEffect(() => {
    setEdges(
      diagram.connections.map((c) =>
        toRfEdge(c.fromNodeId, c.toNodeId, c.id, CONNECTION_COLORS[resolved], canEditConnections),
      ),
    );
  }, [diagram.connections, resolved, canEditConnections, setEdges]);

  const handleConnect = useCallback(
    (connection: Connection) => {
      if (!canEditConnections) return;
      if (connection.source && connection.target && connection.source !== connection.target) {
        connectionMadeRef.current = true;
        onConnect(connection.source, connection.target);
      }
    },
    [onConnect, canEditConnections],
  );

  const handleNodeClick: NodeMouseHandler<Node> = useCallback(
    (_event, node) => onSelectNode(node.id),
    [onSelectNode],
  );

  const handleNodeDoubleClick: NodeMouseHandler<Node> = useCallback(
    (_event, node) => {
      if (canEdit) onEditNode(node.id);
    },
    [onEditNode, canEdit],
  );

  const handleDragOver = useCallback((event: React.DragEvent) => {
    event.preventDefault();
    event.dataTransfer.dropEffect = 'copy';
  }, []);

  const handleDrop = useCallback(
    (event: React.DragEvent) => {
      event.preventDefault();
      if (!canEdit) return;
      const type = event.dataTransfer.getData(NODE_DRAG_TYPE) as NodeType;
      if (!type) return;
      const instance = instanceRef.current;
      if (!instance) return;
      const flow = instance.screenToFlowPosition({ x: event.clientX, y: event.clientY });
      const rect = event.currentTarget.getBoundingClientRect();
      onDropNode(type, flow.x, flow.y, event.clientX - rect.left, event.clientY - rect.top);
    },
    [onDropNode, canEdit],
  );

  const handleEdgeDoubleClick: EdgeMouseHandler<Edge> = useCallback(
    (_event, edge) => {
      if (canEditConnections) onDeleteConnection(edge.id);
    },
    [onDeleteConnection, canEditConnections],
  );

  const handleWrapperDoubleClick = useCallback(
    (event: React.MouseEvent) => {
      if (!canEdit) return;
      const target = event.target as HTMLElement;
      // Ignore double-clicks on a node or an edge; only empty canvas creates.
      if (target.closest('.react-flow__node') || target.closest('.react-flow__edge')) return;
      const instance = instanceRef.current;
      if (!instance) return;
      const flow = instance.screenToFlowPosition({ x: event.clientX, y: event.clientY });
      const rect = event.currentTarget.getBoundingClientRect();
      onCreateAt(flow.x, flow.y, event.clientX - rect.left, event.clientY - rect.top);
    },
    [onCreateAt, canEdit],
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
        onNodeDragStop={(_e, node) => {
          if (canEdit) onMoveNode(node.id, node.position.x, node.position.y);
        }}
        onNodesDelete={(deleted) => {
          if (canEdit) deleted.forEach((n) => onDeleteNode(n.id));
        }}
        nodesDraggable={canEdit}
        nodesConnectable={canEditConnections}
        onEdgeDoubleClick={handleEdgeDoubleClick}
        onEdgesDelete={(deleted) => {
          if (canEditConnections) deleted.forEach((e) => onDeleteConnection(e.id));
        }}
        onPaneClick={() => onSelectNode(null)}
        zoomOnDoubleClick={false}
        colorMode={resolved}
        fitView
        proOptions={{ hideAttribution: true }}
        ariaLabelConfig={FLOW_ARIA_LABELS}
      >
        <Background />
        <Controls />
        <MiniMap
          pannable
          zoomable
          nodeColor={(n) => verticalColor(resolved, (n.data as EnerNodeData).vertical)}
        />
      </ReactFlow>
    </div>
  );
}
