import { useActiveProject } from '../hooks/useActiveProject';
import { useSearchParams } from 'react-router-dom';
import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties, type ReactNode } from 'react';
import { useOrganizations } from '../hooks/useOrganizations';
import { projectsApi } from '../api/projects';
import { getErrorMessage } from '../api/errors';
import { useDiagram } from '../hooks/useDiagram';
import { DiagramCanvas } from '../components/editor/DiagramCanvas';
import { MapView } from '../components/editor/MapView';
import { NodeDataPanel } from '../components/editor/NodeDataPanel';
import { NodeFormPanel, type NodeFormInitial } from '../components/editor/NodeFormPanel';
import { NodePalette } from '../components/editor/NodePalette';
import {
  FIRST_VERSION_ADMIN_MESSAGE,
  NewVersionModal,
} from '../components/versions/NewVersionModal';
import { useGuardedProjectAccess } from '../hooks/useGuardedProjectAccess';
import { editorGates } from '../utils/editorPermissions';
import { AccessFailureAlert } from '../components/versionRoles/AccessFailureAlert';
import {
  baseValuesFromDetail,
  canConnect,
  nodeTypeLabel,
  type GraphDataInput,
  type NodeBaseValues,
} from '../components/editor/nodeCatalog';
import { Alert } from '../components/ui/Alert';
import { Button } from '../components/ui/Button';
import { controlCompact } from '../components/ui/controlClasses';
import { Spinner } from '../components/ui/Spinner';
import type { NodeDetail, NodeType, VersionSummary } from '../types/diagram';

type Mode = 'diagram' | 'map';
type Projection = 'mercator' | 'globe';
type PanelMode = 'data' | 'create' | 'edit';

const MODE_LABELS: Record<Mode, string> = { diagram: 'Diagrama', map: 'Mapa' };

interface EditState {
  nodeId: string;
  identity: string;
  graphData: GraphDataInput;
  /** `null` while the full detail is still loading (panel shows a spinner). */
  initial: NodeFormInitial | null;
}

/** Builds the edit form's initial values from a loaded node detail. */
function editStateFromDetail(detail: NodeDetail): EditState {
  const base: NodeBaseValues = baseValuesFromDetail(detail);
  return {
    nodeId: detail.id,
    identity: detail.identity,
    graphData: detail.graphData ?? {},
    initial: { type: detail.type.nodeType, base, typeFields: detail.attributes },
  };
}

const PANEL_WIDTH = 320;

const selectClass = controlCompact;

/** A labelled select for the toolbar. */
function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="flex flex-col gap-0.5">
      <span className="px-0.5 text-[10px] font-semibold uppercase tracking-wide text-ink-500">{label}</span>
      {children}
    </label>
  );
}

/** A floating card overlaying the canvas. */
function FloatingPanel({ style, children }: { style: CSSProperties; children: ReactNode }) {
  return (
    <div
      className="absolute z-20 flex max-h-[calc(100%-2rem)] w-80 flex-col overflow-hidden rounded-card border border-ink-100 bg-raised shadow-float"
      style={style}
    >
      {children}
    </div>
  );
}

/** The visual editor: pick org → project → version, then edit the diagram on a canvas or a map. */
export function EditorPage() {
  const { activeProject, isLoading } = useActiveProject();
  if (isLoading) return <p role="status">Cargando proyectos…</p>;
  return <EditorWorkspace key={activeProject?.id ?? 'empty'} />;
}
function EditorWorkspace() {
  const { activeProject, projects: allProjects, selectProject, reload: reloadProjects } = useActiveProject();
  const [searchParams] = useSearchParams();
  const preferredVersion = searchParams.get('versionId');
  const { organizations, createOrganization } = useOrganizations();
  const [orgId, setOrgId] = useState(activeProject?.organizationId ?? '');
  const projects = allProjects.filter(p => p.organizationId === orgId);
  const projectId = activeProject?.organizationId === orgId ? activeProject.id : '';
  const setProjectId = selectProject;
  const [versionState, setVersionState] = useState<{ project: string; list: VersionSummary[]; selected: string }>({ project: '', list: [], selected: '' });
  const versions = versionState.project === projectId ? versionState.list : [];
  const versionId = versions.some(v => v.id === versionState.selected) ? versionState.selected : '';
  const setVersionId = (id: string) => setVersionState(state => ({ ...state, selected: id }));
  const {
    access,
    loading: accessLoading,
    failure: accessFailure,
    retry: retryAccess,
  } = useGuardedProjectAccess(projectId || undefined);
  const accessFailed = accessFailure !== null;
  // Deny by default: nothing is editable until /access answers for this version.
  const gates = useMemo(
    () => editorGates(access, versionId || null, accessFailed, accessLoading),
    [access, versionId, accessFailed, accessLoading],
  );
  const { canEdit, canEditConnections, canCreateRoot, canBranchFrom } = gates;
  const versionRequest = useRef(0);
  const [selectorError, setSelectorError] = useState<string | null>(null);

  const [mode, setMode] = useState<Mode>('diagram');
  const [projection, setProjection] = useState<Projection>('mercator');
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [panelMode, setPanelMode] = useState<PanelMode>('data');
  const [createPos, setCreatePos] = useState<{ x: number; y: number }>({ x: 120, y: 120 });
  const [createAnchor, setCreateAnchor] = useState<{ x: number; y: number } | null>(null);
  const [createType, setCreateType] = useState<NodeType | null>(null);
  const [editState, setEditState] = useState<EditState | null>(null);
  const [isNewVersionModalOpen, setIsNewVersionModalOpen] = useState(false);
  const [connectingFrom, setConnectingFrom] = useState<string | null>(null);
  const [connectError, setConnectError] = useState<string | null>(null);
  const [selectedDetail, setSelectedDetail] = useState<NodeDetail | null>(null);
  const canvasWrapRef = useRef<HTMLDivElement | null>(null);
  const selectedIdentityRef = useRef<string | null>(null);

  const {
    diagram,
    loading,
    error,
    busy,
    addNode: addNodeRaw,
    removeNode: removeNodeRaw,
    restoreNode: restoreNodeRaw,
    updateNodeBasics: updateNodeBasicsRaw,
    getNodeDetail,
    editNodeData: editNodeDataRaw,
    addConnection: addConnectionRaw,
    deleteConnection: deleteConnectionRaw,
    moveNodeGraph: moveNodeGraphRaw,
    moveNodeGeo: moveNodeGeoRaw,
  } = useDiagram(versionId || null);

  // Every mutation re-checks its permission here, so no entry point (button,
  // double click, key, drag, drop) can write without it.
  const addNode = useCallback<typeof addNodeRaw>(
    async (...args) => {
      if (!canEdit) return;
      await addNodeRaw(...args);
    },
    [canEdit, addNodeRaw],
  );
  const editNodeData = useCallback<typeof editNodeDataRaw>(
    async (...args) => {
      if (!canEdit) return;
      await editNodeDataRaw(...args);
    },
    [canEdit, editNodeDataRaw],
  );
  const removeNode = useCallback<typeof removeNodeRaw>(
    async (...args) => {
      if (!canEdit) return;
      await removeNodeRaw(...args);
    },
    [canEdit, removeNodeRaw],
  );
  const restoreNode = useCallback<typeof restoreNodeRaw>(
    async (...args) => {
      if (!canEdit) return;
      await restoreNodeRaw(...args);
    },
    [canEdit, restoreNodeRaw],
  );
  const updateNodeBasics = useCallback<typeof updateNodeBasicsRaw>(
    async (...args) => {
      if (!canEdit) return;
      await updateNodeBasicsRaw(...args);
    },
    [canEdit, updateNodeBasicsRaw],
  );
  const moveNodeGraph = useCallback<typeof moveNodeGraphRaw>(
    async (...args) => {
      if (!canEdit) return;
      await moveNodeGraphRaw(...args);
    },
    [canEdit, moveNodeGraphRaw],
  );
  const moveNodeGeo = useCallback<typeof moveNodeGeoRaw>(
    async (...args) => {
      if (!canEdit) return;
      await moveNodeGeoRaw(...args);
    },
    [canEdit, moveNodeGeoRaw],
  );
  const addConnection = useCallback<typeof addConnectionRaw>(
    async (...args) => {
      if (!canEditConnections) return;
      await addConnectionRaw(...args);
    },
    [canEditConnections, addConnectionRaw],
  );
  const deleteConnection = useCallback<typeof deleteConnectionRaw>(
    async (...args) => {
      if (!canEditConnections) return;
      await deleteConnectionRaw(...args);
    },
    [canEditConnections, deleteConnectionRaw],
  );

  // Guards a new connection against the value-chain rules before creating it,
  // surfacing a clear message when the two node types cannot be linked.
  const handleConnect = useCallback(
    (fromNodeId: string, toNodeId: string) => {
      if (!canEditConnections) return;
      const from = diagram?.nodes.find((n) => n.id === fromNodeId);
      const to = diagram?.nodes.find((n) => n.id === toNodeId);
      if (!from || !to) return;
      if (!canConnect(from.type.nodeType, to.type.nodeType)) {
        const fromLabel = nodeTypeLabel(from.type.nodeType);
        const toLabel = nodeTypeLabel(to.type.nodeType);
        setConnectError(`Esta conexión no está permitida: ${fromLabel} → ${toLabel}.`);
        return;
      }
      setConnectError(null);
      void addConnection(fromNodeId, toNodeId);
    },
    [diagram, addConnection, canEditConnections],
  );

  const closePanel = useCallback(() => {
    setPanelMode('data');
    setCreateAnchor(null);
    setCreateType(null);
    setEditState(null);
  }, []);

  // Clears the selected node and every panel derived from it (data panel,
  // edit form, connect mode). Called synchronously alongside the state update
  // that switches organization, project or version, so the node-detail effect
  // never sees a selection that belongs to the context being left.
  const clearNodeSelection = useCallback(() => {
    closePanel();
    setSelectedNodeId(null);
    setSelectedDetail(null);
    setConnectingFrom(null);
    setConnectError(null);
  }, [closePanel]);

  // Selecting a node shows its data panel — or completes a pending connection.
  const selectNode = useCallback(
    (id: string | null) => {
      if (id === null) {
        setConnectingFrom(null);
        setSelectedNodeId(null);
        return;
      }
      if (connectingFrom && id !== connectingFrom) {
        handleConnect(connectingFrom, id);
        setConnectingFrom(null);
      }
      setSelectedNodeId(id);
      setPanelMode('data');
    },
    [connectingFrom, handleConnect],
  );

  const openCreateAt = useCallback(
    (flowX: number, flowY: number, localX: number, localY: number, type?: NodeType | null) => {
      if (!canEdit) return;
      setCreatePos({ x: flowX, y: flowY });
      setCreateAnchor({ x: localX, y: localY });
      setCreateType(type ?? null);
      setPanelMode('create');
    },
    [canEdit],
  );

  // Dropping a palette tile onto the canvas opens the create form at the drop
  // point, pre-selected to the dropped node type (the user still names it).
  const handleDropNode = useCallback(
    (type: NodeType, flowX: number, flowY: number, localX: number, localY: number) => {
      openCreateAt(flowX, flowY, localX, localY, type);
    },
    [openCreateAt],
  );

  const handleEditData = useCallback(
    async (nodeId: string) => {
      if (!canEdit) return;
      // Open the edit panel immediately so there is no perceived delay. If the
      // node's full detail is already loaded (it was selected first), use it
      // straight away; otherwise show a spinner and fill in once it arrives.
      setPanelMode('edit');
      const cached =
        selectedDetail && selectedDetail.id === nodeId ? selectedDetail : null;
      if (cached) {
        setEditState(editStateFromDetail(cached));
        return;
      }
      setEditState({ nodeId, identity: '', graphData: {}, initial: null });
      const detail = await getNodeDetail(nodeId);
      if (!detail) {
        closePanel();
        return;
      }
      setEditState(editStateFromDetail(detail));
    },
    [canEdit, selectedDetail, getNodeDetail, closePanel],
  );

  // Double-clicking a node (or the panel's Edit button) selects it and opens
  // its full editable form.
  const openEditNode = useCallback(
    (nodeId: string) => {
      setSelectedNodeId(nodeId);
      if (canEdit) void handleEditData(nodeId);
    },
    [canEdit, handleEditData],
  );

  const startConnect = useCallback(
    (nodeId: string) => {
      if (!canEditConnections) return;
      setConnectError(null);
      setConnectingFrom(nodeId);
    },
    [canEditConnections],
  );

  // Permissions can be lost while a form or a pending connection is open
  // (access reloaded, version switched): close whatever is no longer allowed.
  useEffect(() => {
    if (!canEdit && (panelMode === 'create' || panelMode === 'edit')) {
      closePanel();
    }
  }, [canEdit, panelMode, closePanel]);
  useEffect(() => {
    if (!canEditConnections) {
      setConnectingFrom(null);
    }
  }, [canEditConnections]);

  // Safety net: reset per-diagram UI state when the open version changes.
  // The actual fix lives where versionId/projectId/orgId are set — see
  // clearNodeSelection — since by the time this effect runs, a sibling
  // effect reading the still-stale selectedNodeId may already have fired.
  useEffect(() => {
    clearNodeSelection();
  }, [versionId, clearNodeSelection]);

  // Load full detail of the selected node so its data shows immediately.
  useEffect(() => {
    let cancelled = false;
    if (!selectedNodeId) {
      setSelectedDetail(null);
      selectedIdentityRef.current = null;
      return;
    }
    const nodes = diagram?.nodes ?? [];
    const current = nodes.find((n) => n.id === selectedNodeId);
    if (current) {
      selectedIdentityRef.current = current.identity;
    } else if (diagram && selectedIdentityRef.current) {
      const replacement = nodes.find((n) => n.identity === selectedIdentityRef.current);
      if (replacement) {
        setSelectedNodeId(replacement.id);
        return;
      }
    }
    void getNodeDetail(selectedNodeId).then((d) => {
      if (!cancelled) setSelectedDetail(d);
    });
    return () => {
      cancelled = true;
    };
  }, [selectedNodeId, diagram, getNodeDetail]);

  // Escape cancels a pending connection.
  useEffect(() => {
    if (!connectingFrom) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setConnectingFrom(null);
        setConnectError(null);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [connectingFrom]);

  // Default the org selection once organizations load.
  useEffect(() => {
    if (!orgId && organizations.length > 0) setOrgId(organizations[0].id);
  }, [organizations, orgId]);

  const loadVersions = useCallback(async (selectId?: string) => {
    const request = ++versionRequest.current;
    clearNodeSelection();
    setVersionState({ project: projectId, list: [], selected: '' });
    setSelectorError(null);
    if (!projectId) return;
    try {
      const res = await projectsApi.listVersions(projectId);
      if (request !== versionRequest.current) return;
      const list = res.data.data ?? [];
      const preferred = selectId ?? preferredVersion;
      setVersionState({ project: projectId, list, selected: list.find(v => v.id === preferred)?.id ?? list[0]?.id ?? '' });
    } catch (err) {
      if (request === versionRequest.current) setSelectorError(getErrorMessage(err, 'No se pudieron cargar las versiones'));
    }
  }, [projectId, preferredVersion, clearNodeSelection]);
  const invalidateRequest = useCallback(() => { versionRequest.current++; }, []);
  useEffect(() => {
    void loadVersions();
    return invalidateRequest;
  }, [loadVersions, invalidateRequest]);

  async function handleCreateOrganization() {
    const name = window.prompt('Nombre de la nueva organización');
    if (!name || !name.trim()) return;
    try {
      const created = await createOrganization(name.trim());
      setOrgId(created.id);
    } catch (err) {
      setSelectorError(getErrorMessage(err, 'No se pudo crear la organización'));
    }
  }

  async function handleCreateProject() {
    if (!orgId) {
      setSelectorError('Primero creá o elegí una organización.');
      return;
    }
    const name = window.prompt('Nombre del nuevo proyecto');
    if (!name || !name.trim()) return;
    const description = window.prompt('Descripción del proyecto', '') ?? '';
    try {
      const res = await projectsApi.create({
        name: name.trim(),
        description: description.trim() || name.trim(),
        organizationId: orgId,
      });
      await reloadProjects();
      if (res.data.data?.id) selectProject(res.data.data.id);
    } catch (err) {
      setSelectorError(getErrorMessage(err, 'No se pudo crear el proyecto'));
    }
  }

  const selectedNode = useMemo(
    () => diagram?.nodes.find((n) => n.id === selectedNodeId) ?? null,
    [diagram, selectedNodeId],
  );

  const connectingNode = useMemo(
    () => diagram?.nodes.find((n) => n.id === connectingFrom) ?? null,
    [diagram, connectingFrom],
  );

  const nextNodePosition = useMemo(() => {
    const count = diagram?.nodes.length ?? 0;
    return { x: 120 + (count % 6) * 40, y: 120 + (count % 6) * 40 };
  }, [diagram?.nodes.length]);

  // Clicking a palette tile adds that node type near the current spread.
  const handlePickFromPalette = useCallback(
    (type: NodeType) => {
      if (!canEdit) return;
      setCreatePos(nextNodePosition);
      setCreateAnchor(null);
      setCreateType(type);
      setPanelMode('create');
    },
    [canEdit, nextNodePosition],
  );

  function panelStyle(): CSSProperties {
    if (panelMode === 'create' && createAnchor) {
      const w = canvasWrapRef.current?.clientWidth ?? 1000;
      const h = canvasWrapRef.current?.clientHeight ?? 700;
      const left = Math.max(8, Math.min(createAnchor.x, w - PANEL_WIDTH - 8));
      const spaceBelow = h - createAnchor.y - 8;
      const spaceAbove = createAnchor.y - 8;
      // Anchor to whichever side has more room and cap the height to it, so the
      // panel always stays on-screen (it scrolls internally if it needs to).
      if (spaceBelow >= spaceAbove) {
        return { left, top: Math.max(8, createAnchor.y), maxHeight: Math.max(120, spaceBelow) };
      }
      return { left, bottom: Math.max(8, h - createAnchor.y), maxHeight: Math.max(120, spaceAbove) };
    }
    return { right: 16, top: 16 };
  }

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      {/* Toolbar */}
      <div className="flex flex-wrap items-end gap-3 border-b border-ink-100 bg-surface px-4 py-2.5">
        <Field label="Organización">
          <div className="flex items-center gap-1">
            <select
                className={selectClass}
                value={orgId}
                onChange={(e) => {
                  setOrgId(e.target.value);
                  const first = allProjects.find(p => p.organizationId === e.target.value);
                  if (first) selectProject(first.id);
                }}
            >
              {organizations.length === 0 && <option value="">Sin organizaciones</option>}
              {organizations.map((o) => (
                <option key={o.id} value={o.id}>
                  {o.name}
                </option>
              ))}
            </select>
            <Button variant="ghost" size="compact" onClick={handleCreateOrganization} className="px-3" aria-label="Nueva organización" title="Nueva organización">
              +
            </Button>
          </div>
        </Field>

        <Field label="Proyecto">
          <div className="flex items-center gap-1">
            <select
              className={selectClass}
              value={projectId}
              onChange={(e) => {
                clearNodeSelection();
                setProjectId(e.target.value);
              }}
              disabled={projects.length === 0}
            >
              {projects.length === 0 && <option value="">Sin proyectos</option>}
              {projects.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
            <Button
              variant="ghost"
              onClick={handleCreateProject}
              disabled={!orgId}
              size="compact"
              className="px-3"
              aria-label="Nuevo proyecto"
              title="Nuevo proyecto"
            >
              +
            </Button>
          </div>
        </Field>

        <Field label="Versión">
          <div className="flex items-center gap-1">
            <select
              className={selectClass}
              value={versionId}
              onChange={(e) => {
                clearNodeSelection();
                setVersionId(e.target.value);
              }}
              disabled={versions.length === 0}
            >
              {versions.length === 0 && <option value="">Sin versiones</option>}
              {versions.map((v) => (
                <option key={v.id} value={v.id}>
                  {v.name}
                </option>
              ))}
            </select>
            {(versions.length > 0 || canCreateRoot) && (
              <Button
                variant="ghost"
                onClick={() => setIsNewVersionModalOpen(true)}
                disabled={!projectId}
                size="compact"
                className="px-3"
                aria-label="Nueva versión"
                title="Nueva versión"
              >
                +
              </Button>
            )}
            {versions.length === 0 && !canCreateRoot && projectId && !accessLoading && !accessFailed && (
              <span className="text-xs text-ink-500">{FIRST_VERSION_ADMIN_MESSAGE}</span>
            )}
          </div>
        </Field>

        <div className="mx-1 h-8 w-px self-center bg-ink-100" />

        <Field label="Vista">
          <div className="inline-flex h-control-compact overflow-hidden rounded-control border border-control-border">
            {(['diagram', 'map'] as Mode[]).map((m) => (
              <button
                key={m}
                onClick={() => setMode(m)}
                className={
                  'px-3 py-1.5 text-sm font-medium capitalize transition-colors ' +
                  'focus:outline-none focus-visible:ring-2 focus-visible:ring-inset ' +
                  (mode === m
                    ? 'bg-brand-800 text-on-accent focus-visible:ring-on-accent'
                    : 'bg-surface text-ink-600 hover:bg-ink-50 focus-visible:ring-focus')
                }
              >
                {MODE_LABELS[m]}
              </button>
            ))}
          </div>
        </Field>

        {mode === 'map' && (
          <Field label="Proyección">
            <div className="inline-flex h-control-compact overflow-hidden rounded-control border border-control-border">
              {(['mercator', 'globe'] as Projection[]).map((p) => (
                <button
                  key={p}
                  onClick={() => setProjection(p)}
                  className={
                    'px-3 py-1.5 text-sm font-medium transition-colors ' +
                    'focus:outline-none focus-visible:ring-2 focus-visible:ring-inset ' +
                    (projection === p
                      ? 'bg-fill-neutral text-on-accent focus-visible:ring-on-accent'
                      : 'bg-surface text-ink-600 hover:bg-ink-50 focus-visible:ring-focus')
                  }
                >
                  {p === 'mercator' ? '2D' : 'Globo'}
                </button>
              ))}
            </div>
          </Field>
        )}

        <div className="ml-auto flex items-center gap-2 self-center pt-3">
          {(loading || busy) && <Spinner className="h-4 w-4 text-brand-600" />}
          <Button
            onClick={() => {
              if (!canEdit) return;
              if (panelMode === 'create') {
                closePanel();
              } else {
                setCreatePos(nextNodePosition);
                setCreateAnchor(null);
                setCreateType(null);
                setPanelMode('create');
              }
            }}
            disabled={!versionId || !canEdit}
            className="text-sm"
          >
            {panelMode === 'create' ? 'Cancelar' : '+ Agregar nodo'}
          </Button>
        </div>
      </div>

      <div className="px-4 pt-2">
        <AccessFailureAlert message={accessFailure} onRetry={() => void retryAccess()} />
      </div>

      {(error || selectorError) && (
        <div className="px-4 pt-2">
          <Alert tone="error">{error ?? selectorError}</Alert>
        </div>
      )}

      {/* Editor body — palette rail + canvas/map with floating menus on top */}
      <div className="relative flex min-h-0 flex-1">
        {!versionId ? (
          <div className="flex h-full w-full items-center justify-center p-8 text-center text-sm text-ink-500">
            Elegí una organización, un proyecto y una versión para empezar, o creá una versión para armar un
            diagrama nuevo.
          </div>
        ) : !diagram ? (
          <div className="flex h-full w-full items-center justify-center">
            <Spinner className="h-6 w-6 text-brand-600" />
          </div>
        ) : (
          <>
            {mode === 'diagram' && (
              <NodePalette onPick={handlePickFromPalette} disabled={busy || !canEdit} />
            )}
            <div ref={canvasWrapRef} className="relative min-h-0 flex-1">
            <div className="absolute inset-0">
              {mode === 'diagram' ? (
                <DiagramCanvas
                  key={versionId}
                  diagram={diagram}
                  selectedNodeId={selectedNodeId}
                  onSelectNode={selectNode}
                  onMoveNode={moveNodeGraph}
                  onConnect={handleConnect}
                  onDeleteNode={removeNode}
                  onDeleteConnection={deleteConnection}
                  onCreateAt={openCreateAt}
                  onDropNode={handleDropNode}
                  onEditNode={openEditNode}
                  connecting={!!connectingFrom}
                  canEdit={canEdit}
                  canEditConnections={canEditConnections}
                />
              ) : (
                <MapView
                  key={versionId}
                  diagram={diagram}
                  projection={projection}
                  selectedNodeId={selectedNodeId}
                  onSelectNode={selectNode}
                  onMoveGeo={moveNodeGeo}
                  canEdit={canEdit}
                />
              )}
            </div>

            {/* Rejected-connection warning */}
            {connectError && (
              <div className="absolute left-1/2 top-4 z-30 -translate-x-1/2">
                <div className="flex items-center gap-3 rounded-full border border-warn-200 bg-warn-50 px-4 py-2 text-sm text-warn-800 shadow-float">
                  <span>{connectError}</span>
                  <button
                    onClick={() => setConnectError(null)}
                    className="rounded-full px-2 py-0.5 text-xs font-semibold text-warn-700 hover:bg-warn-100"
                  >
                    Cerrar
                  </button>
                </div>
              </div>
            )}

            {/* Connect-mode hint */}
            {connectingFrom && (
              <div className="pointer-events-none absolute left-1/2 top-4 z-30 -translate-x-1/2">
                <div className="pointer-events-auto flex items-center gap-3 rounded-full border border-brand-200 bg-brand-50 px-4 py-2 text-sm text-brand-800 shadow-float">
                  <span>
                    Conectando desde <strong>{connectingNode?.name ?? 'nodo'}</strong>: hacé clic en el nodo de
                    destino
                  </span>
                  <button
                    onClick={() => setConnectingFrom(null)}
                    className="rounded-full px-2 py-0.5 text-xs font-semibold text-brand-900 hover:bg-brand-100"
                  >
                    Cancelar (Esc)
                  </button>
                </div>
              </div>
            )}

            {/* Floating menu */}
            {mode === 'diagram' && (
              <FloatingPanel style={panelStyle()}>
                {panelMode === 'create' && canEdit ? (
                  <NodeFormPanel
                    mode="create"
                    busy={busy}
                    initialType={createType ?? undefined}
                    onClose={closePanel}
                    onSubmit={(spec, base, typeFields) =>
                      addNode(spec, base, typeFields, createPos.x, createPos.y)
                    }
                  />
                ) : panelMode === 'edit' && editState && canEdit ? (
                  editState.initial ? (
                    <NodeFormPanel
                      mode="edit"
                      busy={busy}
                      initial={editState.initial}
                      onClose={closePanel}
                      onSubmit={(spec, base, typeFields) =>
                        editNodeData(
                          editState.nodeId,
                          spec,
                          base,
                          typeFields,
                          editState.graphData,
                          editState.identity,
                        )
                      }
                    />
                  ) : (
                    <div className="flex items-center justify-center p-8">
                      <Spinner className="h-6 w-6 text-brand-600" />
                    </div>
                  )
                ) : selectedNode ? (
                  <NodeDataPanel
                    node={selectedNode}
                    detail={selectedDetail}
                    busy={busy}
                    canEdit={canEdit}
                    canEditConnections={canEditConnections}
                    onUpdateBasics={updateNodeBasics}
                    onEditData={handleEditData}
                    onStartConnect={startConnect}
                    onRestore={(id) => {
                      void restoreNode(id);
                    }}
                    onDelete={(id) => {
                      void removeNode(id);
                      setSelectedNodeId(null);
                      closePanel();
                    }}
                  />
                ) : (
                  <div className="p-4 text-sm text-ink-500">
                    Hacé doble clic en un espacio vacío del lienzo para agregar un nodo, o hacé clic en un nodo para ver sus datos.
                  </div>
                )}
              </FloatingPanel>
            )}
            </div>
          </>
        )}
      </div>

      {projectId && (
        <NewVersionModal
          projectId={projectId}
          canCreateRoot={canCreateRoot}
          canBranchFrom={canBranchFrom}
          open={isNewVersionModalOpen}
          onClose={() => setIsNewVersionModalOpen(false)}
          onCreated={(createdId) => {
            loadVersions(createdId);
          }}
          defaultParentId={versionId || undefined}
        />
      )}
    </div>
  );
}
