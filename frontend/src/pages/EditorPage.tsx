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
  canConnect,
  specForType,
  type GraphDataInput,
  type NodeBaseValues,
} from '../components/editor/nodeCatalog';
import { Alert } from '../components/ui/Alert';
import { Button } from '../components/ui/Button';
import { Spinner } from '../components/ui/Spinner';
import type { NodeDetail, NodeType, VersionSummary } from '../types/diagram';
import type { ProjectSummary } from '../types/project';

type Mode = 'diagram' | 'map';
type Projection = 'mercator' | 'globe';
type PanelMode = 'data' | 'create' | 'edit';

interface EditState {
  nodeId: string;
  identity: string;
  graphData: GraphDataInput;
  /** `null` while the full detail is still loading (panel shows a spinner). */
  initial: NodeFormInitial | null;
}

/** Builds the edit form's initial values from a loaded node detail. */
function editStateFromDetail(detail: NodeDetail): EditState {
  const base: NodeBaseValues = {
    name: detail.name,
    state: detail.state,
    upkeepCosts: detail.upkeepCosts,
    operatingCosts: detail.operatingCosts,
    lifespanInMonths: detail.lifespanInMonths,
    maintenanceIntervalInDays: detail.maintenanceIntervalInDays,
    wastePercentage: detail.wastePercentage,
  };
  return {
    nodeId: detail.id,
    identity: detail.identity,
    graphData: detail.graphData ?? {},
    initial: { type: detail.type.nodeType, base, typeFields: detail.attributes },
  };
}

const PANEL_WIDTH = 320;

const selectClass =
  'rounded-lg border border-ink-200 bg-white px-2.5 py-1.5 text-sm text-ink-700 shadow-sm ' +
  'focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-400/40 disabled:opacity-50';

/** A labelled select for the toolbar. */
function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="flex flex-col gap-0.5">
      <span className="px-0.5 text-[10px] font-semibold uppercase tracking-wide text-ink-400">{label}</span>
      {children}
    </label>
  );
}

/** A floating card overlaying the canvas. */
function FloatingPanel({ style, children }: { style: CSSProperties; children: ReactNode }) {
  return (
    <div
      className="absolute z-20 flex max-h-[calc(100%-2rem)] w-80 flex-col overflow-hidden rounded-2xl border border-ink-100 bg-white shadow-xl ring-1 ring-black/5"
      style={style}
    >
      {children}
    </div>
  );
}

/** The visual editor: pick org → project → version, then edit the diagram on a canvas or a map. */
export function EditorPage() {
  const { organizations, createOrganization } = useOrganizations();
  const [orgId, setOrgId] = useState('');
  const [projects, setProjects] = useState<ProjectSummary[]>([]);
  const [projectId, setProjectId] = useState('');
  const [versions, setVersions] = useState<VersionSummary[]>([]);
  const [versionId, setVersionId] = useState('');
  const [selectorError, setSelectorError] = useState<string | null>(null);

  const [mode, setMode] = useState<Mode>('diagram');
  const [projection, setProjection] = useState<Projection>('mercator');
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [panelMode, setPanelMode] = useState<PanelMode>('data');
  const [createPos, setCreatePos] = useState<{ x: number; y: number }>({ x: 120, y: 120 });
  const [createAnchor, setCreateAnchor] = useState<{ x: number; y: number } | null>(null);
  const [createType, setCreateType] = useState<NodeType | null>(null);
  const [editState, setEditState] = useState<EditState | null>(null);
  const [connectingFrom, setConnectingFrom] = useState<string | null>(null);
  const [connectError, setConnectError] = useState<string | null>(null);
  const [selectedDetail, setSelectedDetail] = useState<NodeDetail | null>(null);
  const canvasWrapRef = useRef<HTMLDivElement | null>(null);

  const {
    diagram,
    loading,
    error,
    busy,
    addNode,
    removeNode,
    restoreNode,
    updateNodeBasics,
    getNodeDetail,
    editNodeData,
    addConnection,
    deleteConnection,
    moveNodeGraph,
    moveNodeGeo,
  } = useDiagram(versionId || null);

  // Guards a new connection against the value-chain rules before creating it,
  // surfacing a clear message when the two node types cannot be linked.
  const handleConnect = useCallback(
    (fromNodeId: string, toNodeId: string) => {
      const from = diagram?.nodes.find((n) => n.id === fromNodeId);
      const to = diagram?.nodes.find((n) => n.id === toNodeId);
      if (!from || !to) return;
      if (!canConnect(from.type.nodeType, to.type.nodeType)) {
        const fromLabel = specForType(from.type.nodeType)?.label ?? from.type.nodeType;
        const toLabel = specForType(to.type.nodeType)?.label ?? to.type.nodeType;
        setConnectError(`A ${fromLabel} cannot connect to a ${toLabel}.`);
        return;
      }
      setConnectError(null);
      void addConnection(fromNodeId, toNodeId);
    },
    [diagram, addConnection],
  );

  const closePanel = useCallback(() => {
    setPanelMode('data');
    setCreateAnchor(null);
    setCreateType(null);
    setEditState(null);
  }, []);

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
      setCreatePos({ x: flowX, y: flowY });
      setCreateAnchor({ x: localX, y: localY });
      setCreateType(type ?? null);
      setPanelMode('create');
    },
    [],
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
    [selectedDetail, getNodeDetail, closePanel],
  );

  // Double-clicking a node (or the panel's Edit button) selects it and opens
  // its full editable form.
  const openEditNode = useCallback(
    (nodeId: string) => {
      setSelectedNodeId(nodeId);
      void handleEditData(nodeId);
    },
    [handleEditData],
  );

  const startConnect = useCallback((nodeId: string) => {
    setConnectError(null);
    setConnectingFrom(nodeId);
  }, []);

  // Reset per-diagram UI state when the open version changes.
  useEffect(() => {
    setSelectedNodeId(null);
    setSelectedDetail(null);
    setConnectingFrom(null);
    setConnectError(null);
    setEditState(null);
    setPanelMode('data');
  }, [versionId]);

  // Load full detail of the selected node so its data shows immediately.
  useEffect(() => {
    let cancelled = false;
    if (!selectedNodeId) {
      setSelectedDetail(null);
      return;
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

  const loadProjects = useCallback(
    (selectId?: string) => {
      if (!orgId) return;
      setSelectorError(null);
      projectsApi
        .list(orgId)
        .then((res) => {
          const list = res.data.data ?? [];
          setProjects(list);
          setProjectId(selectId ?? list[0]?.id ?? '');
        })
        .catch((err) => setSelectorError(getErrorMessage(err, 'Could not load projects')));
    },
    [orgId],
  );

  useEffect(() => {
    loadProjects();
  }, [loadProjects]);

  const loadVersions = useCallback(
    (selectId?: string) => {
      if (!projectId) {
        setVersions([]);
        setVersionId('');
        return;
      }
      projectsApi
        .listVersions(projectId)
        .then((res) => {
          const list = res.data.data ?? [];
          setVersions(list);
          setVersionId(selectId ?? list[0]?.id ?? '');
        })
        .catch((err) => setSelectorError(getErrorMessage(err, 'Could not load versions')));
    },
    [projectId],
  );

  useEffect(() => {
    loadVersions();
  }, [loadVersions]);

  async function handleCreateOrganization() {
    const name = window.prompt('New organization name');
    if (!name || !name.trim()) return;
    try {
      const created = await createOrganization(name.trim());
      setOrgId(created.id);
    } catch (err) {
      setSelectorError(getErrorMessage(err, 'Could not create the organization'));
    }
  }

  async function handleCreateProject() {
    if (!orgId) {
      setSelectorError('Create or pick an organization first.');
      return;
    }
    const name = window.prompt('New project name');
    if (!name || !name.trim()) return;
    const description = window.prompt('Project description', '') ?? '';
    try {
      const res = await projectsApi.create({
        name: name.trim(),
        description: description.trim() || name.trim(),
        organizationId: orgId,
      });
      loadProjects(res.data.data?.id);
    } catch (err) {
      setSelectorError(getErrorMessage(err, 'Could not create the project'));
    }
  }

  async function handleCreateVersion() {
    if (!projectId) return;
    const name = window.prompt('New version name');
    if (!name || !name.trim()) return;
    try {
      const parent = versionId || undefined;
      await projectsApi.createVersion(projectId, { name: name.trim(), parentVersion: parent });
      loadVersions();
    } catch (err) {
      setSelectorError(getErrorMessage(err, 'Could not create the version'));
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
      setCreatePos(nextNodePosition);
      setCreateAnchor(null);
      setCreateType(type);
      setPanelMode('create');
    },
    [nextNodePosition],
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
      <div className="flex flex-wrap items-end gap-3 border-b border-ink-100 bg-white px-4 py-2.5">
        <Field label="Organization">
          <div className="flex items-center gap-1">
            <select className={selectClass} value={orgId} onChange={(e) => setOrgId(e.target.value)}>
              {organizations.length === 0 && <option value="">No organizations</option>}
              {organizations.map((o) => (
                <option key={o.id} value={o.id}>
                  {o.name}
                </option>
              ))}
            </select>
            <Button variant="ghost" onClick={handleCreateOrganization} className="px-2 py-1.5 text-xs">
              +
            </Button>
          </div>
        </Field>

        <Field label="Project">
          <div className="flex items-center gap-1">
            <select
              className={selectClass}
              value={projectId}
              onChange={(e) => setProjectId(e.target.value)}
              disabled={projects.length === 0}
            >
              {projects.length === 0 && <option value="">No projects</option>}
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
              className="px-2 py-1.5 text-xs"
            >
              +
            </Button>
          </div>
        </Field>

        <Field label="Version">
          <div className="flex items-center gap-1">
            <select
              className={selectClass}
              value={versionId}
              onChange={(e) => setVersionId(e.target.value)}
              disabled={versions.length === 0}
            >
              {versions.length === 0 && <option value="">No versions</option>}
              {versions.map((v) => (
                <option key={v.id} value={v.id}>
                  {v.name}
                </option>
              ))}
            </select>
            <Button
              variant="ghost"
              onClick={handleCreateVersion}
              disabled={!projectId}
              className="px-2 py-1.5 text-xs"
            >
              +
            </Button>
          </div>
        </Field>

        <div className="mx-1 h-8 w-px self-center bg-ink-100" />

        <Field label="View">
          <div className="inline-flex overflow-hidden rounded-lg border border-ink-200 shadow-sm">
            {(['diagram', 'map'] as Mode[]).map((m) => (
              <button
                key={m}
                onClick={() => setMode(m)}
                className={
                  'px-3 py-1.5 text-sm font-medium capitalize transition-colors ' +
                  (mode === m ? 'bg-brand-500 text-white' : 'bg-white text-ink-600 hover:bg-ink-50')
                }
              >
                {m}
              </button>
            ))}
          </div>
        </Field>

        {mode === 'map' && (
          <Field label="Projection">
            <div className="inline-flex overflow-hidden rounded-lg border border-ink-200 shadow-sm">
              {(['mercator', 'globe'] as Projection[]).map((p) => (
                <button
                  key={p}
                  onClick={() => setProjection(p)}
                  className={
                    'px-3 py-1.5 text-sm font-medium transition-colors ' +
                    (projection === p ? 'bg-ink-700 text-white' : 'bg-white text-ink-600 hover:bg-ink-50')
                  }
                >
                  {p === 'mercator' ? '2D' : 'Globe'}
                </button>
              ))}
            </div>
          </Field>
        )}

        <div className="ml-auto flex items-center gap-2 self-center pt-3">
          {(loading || busy) && <Spinner className="h-4 w-4 text-brand-600" />}
          <Button
            onClick={() => {
              if (panelMode === 'create') {
                closePanel();
              } else {
                setCreatePos(nextNodePosition);
                setCreateAnchor(null);
                setCreateType(null);
                setPanelMode('create');
              }
            }}
            disabled={!versionId}
            className="text-sm"
          >
            {panelMode === 'create' ? 'Cancel' : '+ Add node'}
          </Button>
        </div>
      </div>

      {(error || selectorError) && (
        <div className="px-4 pt-2">
          <Alert tone="error">{error ?? selectorError}</Alert>
        </div>
      )}

      {/* Editor body — palette rail + canvas/map with floating menus on top */}
      <div className="relative flex min-h-0 flex-1">
        {!versionId ? (
          <div className="flex h-full w-full items-center justify-center p-8 text-center text-sm text-ink-400">
            Pick an organization, project and version to start — or create a version to begin a new
            diagram.
          </div>
        ) : !diagram ? (
          <div className="flex h-full w-full items-center justify-center">
            <Spinner className="h-6 w-6 text-brand-600" />
          </div>
        ) : (
          <>
            {mode === 'diagram' && (
              <NodePalette onPick={handlePickFromPalette} disabled={busy} />
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
                />
              ) : (
                <MapView
                  key={versionId}
                  diagram={diagram}
                  projection={projection}
                  selectedNodeId={selectedNodeId}
                  onSelectNode={selectNode}
                  onMoveGeo={moveNodeGeo}
                />
              )}
            </div>

            {/* Rejected-connection warning */}
            {connectError && (
              <div className="absolute left-1/2 top-4 z-30 -translate-x-1/2">
                <div className="flex items-center gap-3 rounded-full border border-amber-200 bg-amber-50 px-4 py-2 text-sm text-amber-800 shadow-md">
                  <span>{connectError}</span>
                  <button
                    onClick={() => setConnectError(null)}
                    className="rounded-full px-2 py-0.5 text-xs font-semibold text-amber-700 hover:bg-amber-100"
                  >
                    Dismiss
                  </button>
                </div>
              </div>
            )}

            {/* Connect-mode hint */}
            {connectingFrom && (
              <div className="pointer-events-none absolute left-1/2 top-4 z-30 -translate-x-1/2">
                <div className="pointer-events-auto flex items-center gap-3 rounded-full border border-brand-200 bg-brand-50 px-4 py-2 text-sm text-brand-800 shadow-md">
                  <span>
                    Connecting from <strong>{connectingNode?.name ?? 'node'}</strong> — click a target
                    node
                  </span>
                  <button
                    onClick={() => setConnectingFrom(null)}
                    className="rounded-full px-2 py-0.5 text-xs font-semibold text-brand-700 hover:bg-brand-100"
                  >
                    Cancel (Esc)
                  </button>
                </div>
              </div>
            )}

            {/* Floating menu */}
            {mode === 'diagram' && (
              <FloatingPanel style={panelStyle()}>
                {panelMode === 'create' ? (
                  <NodeFormPanel
                    mode="create"
                    busy={busy}
                    initialType={createType ?? undefined}
                    onClose={closePanel}
                    onSubmit={(spec, base, typeFields) =>
                      addNode(spec, base, typeFields, createPos.x, createPos.y)
                    }
                  />
                ) : panelMode === 'edit' && editState ? (
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
                  <div className="p-4 text-sm text-ink-400">
                    Double-click empty canvas to add a node, or click a node to see its data.
                  </div>
                )}
              </FloatingPanel>
            )}
            </div>
          </>
        )}
      </div>
    </div>
  );
}
