import { useCallback, useEffect, useState } from 'react';
import { diagramApi } from '../api/diagram';
import { getErrorMessage } from '../api/errors';
import { buildCreatePayload, type NodeBaseValues, type NodeTypeSpec } from '../components/editor/nodeCatalog';
import type { Diagram, DiagramNode, NodeState } from '../types/diagram';

interface UseDiagram {
  diagram: Diagram | null;
  loading: boolean;
  error: string | null;
  busy: boolean;
  reload: () => Promise<void>;
  addNode: (
    spec: NodeTypeSpec,
    base: NodeBaseValues,
    typeFields: Record<string, number>,
    x: number,
    y: number,
  ) => Promise<void>;
  deleteNode: (nodeId: string) => Promise<void>;
  updateNodeBasics: (nodeId: string, basics: { name?: string; state?: NodeState }) => Promise<void>;
  addConnection: (fromNodeId: string, toNodeId: string) => Promise<void>;
  deleteConnection: (connectionId: string) => Promise<void>;
  moveNodeGraph: (nodeId: string, x: number, y: number) => Promise<void>;
  moveNodeGeo: (nodeId: string, longitude: number, latitude: number) => Promise<void>;
}

/**
 * Loads and mutates the diagram of a single version. Structural changes (add /
 * delete node, connections) reload the diagram from the server to stay
 * consistent; position moves update local state and persist in the background so
 * dragging stays smooth and connections keep following their nodes.
 */
export function useDiagram(versionId: string | null): UseDiagram {
  const [diagram, setDiagram] = useState<Diagram | null>(null);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    if (!versionId) {
      setDiagram(null);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const res = await diagramApi.get(versionId);
      setDiagram(res.data.data ?? { versionId, nodes: [], connections: [] });
    } catch (err) {
      setError(getErrorMessage(err, 'Could not load the diagram'));
    } finally {
      setLoading(false);
    }
  }, [versionId]);

  useEffect(() => {
    void reload();
  }, [reload]);

  const addNode = useCallback(
    async (
      spec: NodeTypeSpec,
      base: NodeBaseValues,
      typeFields: Record<string, number>,
      x: number,
      y: number,
    ) => {
      if (!versionId) return;
      setBusy(true);
      setError(null);
      try {
        const payload = buildCreatePayload(spec, base, typeFields, x, y);
        await diagramApi.addNode(versionId, payload);
        await reload();
      } catch (err) {
        setError(getErrorMessage(err, 'Could not create the node'));
        throw err;
      } finally {
        setBusy(false);
      }
    },
    [versionId, reload],
  );

  const deleteNode = useCallback(
    async (nodeId: string) => {
      if (!versionId || !diagram) return;
      setBusy(true);
      setError(null);
      try {
        // The node<->connection FK is not cascading, so remove the node's edges
        // first, then the node itself.
        const touching = diagram.connections.filter(
          (c) => c.fromNodeId === nodeId || c.toNodeId === nodeId,
        );
        for (const conn of touching) {
          await diagramApi.deleteConnection(versionId, conn.id);
        }
        await diagramApi.deleteNode(versionId, nodeId);
        await reload();
      } catch (err) {
        setError(getErrorMessage(err, 'Could not delete the node'));
        throw err;
      } finally {
        setBusy(false);
      }
    },
    [versionId, diagram, reload],
  );

  const updateNodeBasics = useCallback(
    async (nodeId: string, basics: { name?: string; state?: NodeState }) => {
      if (!versionId) return;
      setBusy(true);
      setError(null);
      try {
        await diagramApi.updateBasics(versionId, nodeId, basics);
        await reload();
      } catch (err) {
        setError(getErrorMessage(err, 'Could not update the node'));
        throw err;
      } finally {
        setBusy(false);
      }
    },
    [versionId, reload],
  );

  const addConnection = useCallback(
    async (fromNodeId: string, toNodeId: string) => {
      if (!versionId) return;
      setBusy(true);
      setError(null);
      try {
        await diagramApi.addConnection(versionId, { fromNodeId, toNodeId });
        await reload();
      } catch (err) {
        setError(getErrorMessage(err, 'Could not create the connection'));
        throw err;
      } finally {
        setBusy(false);
      }
    },
    [versionId, reload],
  );

  const deleteConnection = useCallback(
    async (connectionId: string) => {
      if (!versionId) return;
      setBusy(true);
      setError(null);
      try {
        await diagramApi.deleteConnection(versionId, connectionId);
        await reload();
      } catch (err) {
        setError(getErrorMessage(err, 'Could not delete the connection'));
        throw err;
      } finally {
        setBusy(false);
      }
    },
    [versionId, reload],
  );

  const patchNodeLocally = useCallback((nodeId: string, updater: (n: DiagramNode) => DiagramNode) => {
    setDiagram((prev) =>
      prev
        ? { ...prev, nodes: prev.nodes.map((n) => (n.id === nodeId ? updater(n) : n)) }
        : prev,
    );
  }, []);

  const moveNodeGraph = useCallback(
    async (nodeId: string, x: number, y: number) => {
      if (!versionId) return;
      patchNodeLocally(nodeId, (n) => ({
        ...n,
        graphData: {
          graphPosition: { x, y },
          geographicalPosition: n.graphData?.geographicalPosition ?? null,
        },
      }));
      try {
        await diagramApi.updatePosition(versionId, nodeId, {
          graphPosition: { x, y },
          geographicalPosition: null,
        });
      } catch (err) {
        setError(getErrorMessage(err, 'Could not save the new position'));
      }
    },
    [versionId, patchNodeLocally],
  );

  const moveNodeGeo = useCallback(
    async (nodeId: string, longitude: number, latitude: number) => {
      if (!versionId) return;
      patchNodeLocally(nodeId, (n) => ({
        ...n,
        graphData: {
          graphPosition: n.graphData?.graphPosition ?? null,
          geographicalPosition: { longitude, latitude },
        },
      }));
      try {
        await diagramApi.updatePosition(versionId, nodeId, {
          graphPosition: null,
          geographicalPosition: { longitude, latitude },
        });
      } catch (err) {
        setError(getErrorMessage(err, 'Could not save the new position'));
      }
    },
    [versionId, patchNodeLocally],
  );

  return {
    diagram,
    loading,
    error,
    busy,
    reload,
    addNode,
    deleteNode,
    updateNodeBasics,
    addConnection,
    deleteConnection,
    moveNodeGraph,
    moveNodeGeo,
  };
}
