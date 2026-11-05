import type { Diagram } from '../types/diagram';

/** Hide soft-deleted nodes and their edges without changing the saved snapshot. */
export function visibleDiagram(diagram: Diagram): Diagram {
  const nodes = diagram.nodes.filter(node => node.state !== 'REMOVED');
  const visibleIds = new Set(nodes.map(node => node.id));
  return {
    ...diagram,
    nodes,
    connections: diagram.connections.filter(edge =>
      visibleIds.has(edge.fromNodeId) && visibleIds.has(edge.toNodeId)),
  };
}
