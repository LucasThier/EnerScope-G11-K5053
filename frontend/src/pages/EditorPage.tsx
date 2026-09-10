import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties, type ReactNode } from 'react';
import { useOrganizations } from '../hooks/useOrganizations';
import { projectsApi } from '../api/projects';
import { getErrorMessage } from '../api/errors';
import { useDiagram } from '../hooks/useDiagram';
import { DiagramCanvas } from '../components/editor/DiagramCanvas';
import { MapView } from '../components/editor/MapView';
import { NodeDataPanel } from '../components/editor/NodeDataPanel';
import { NodeFormPanel, type NodeFormInitial } from '../components/editor/NodeFormPanel';
import type { GraphDataInput, NodeBaseValues } from '../components/editor/nodeCatalog';
import { Alert } from '../components/ui/Alert';
import { Button } from '../components/ui/Button';
import { Spinner } from '../components/ui/Spinner';
import type { NodeDetail, Project, VersionSummary } from '../types/diagram';

type Mode = 'diagram' | 'map';
type Projection = 'mercator' | 'globe';
type PanelMode = 'data' | 'create' | 'edit';

interface EditState {
  nodeId: string;
  identity: string;
  graphData: GraphDataInput;
  initial: NodeFormInitial;
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
  const [projects, setProjects] = useState<Project[]>([]);
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
  const [editState, setEditState] = useState<EditState | null>(null);
  const [connectingFrom, setConnectingFrom] = useState<string | null>(null);
  const [selectedDetail, setSelectedDetail] = useState<NodeDetail | null>(null);
  const bodyRef = useRef<HTMLDivElement | null>(null);

  const {
    diagram,
    loading,
    error,
    busy,
    addNode,
    deleteNode,
    updateNodeBasics,
    getNodeDetail,
    editNodeData,
    addConnection,
    deleteConnection,
    moveNodeGraph,
    moveNodeGeo,
  } = useDiagram(versionId || null);

  const closePanel = useCallback(() => {
    setPanelMode('data');
    setCreateAnchor(null);
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
        void addConnection(connectingFrom, id);
        setConnectingFrom(null);
      }
      setSelectedNodeId(id);
      setPanelMode('data');
    },
    [connectingFrom, addConnection],
  );

  const openCreateAt = useCallback((flowX: number, flowY: number, localX: number, localY: number) => {
    setCreatePos({ x: flowX, y: flowY });
    setCreateAnchor({ x: localX, y: localY });
    setPanelMode('create');
  }, []);

  const handleEditData = useCallback(
    async (nodeId: string) => {
      const detail = await getNodeDetail(nodeId);
      if (!detail) return;
      const base: NodeBaseValues = {
        name: detail.name,
        state: detail.state,
        upkeepCosts: detail.upkeepCosts,
        operatingCosts: detail.operatingCosts,
        lifespanInMonths: detail.lifespanInMonths,
        maintenanceIntervalInDays: detail.maintenanceIntervalInDays,
        wastePercentage: detail.wastePercentage,
      };
      setEditState({
        nodeId,
        identity: detail.identity,
        graphData: detail.graphData ?? {},
        initial: { type: detail.type.nodeType, base, typeFields: detail.attributes },
      });
      setPanelMode('edit');
    },
    [getNodeDetail],
  );

  // Reset per-diagram UI state when the open version changes.
  useEffect(() => {
    setSelectedNodeId(null);
    setSelectedDetail(null);
    setConnectingFrom(null);
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
      if (e.key === 'Escape') setConnectingFrom(null);
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
        .listByOrganization(orgId)
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

  function panelStyle(): CSSProperties {
    if (panelMode === 'create' && createAnchor) {
      const w = bodyRef.current?.clientWidth ?? 1000;
      const h = bodyRef.current?.clientHeight ?? 700;
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

      {/* Editor body — canvas/map with floating menus on top */}
      <div ref={bodyRef} className="relative flex min-h-0 flex-1">
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
            <div className="absolute inset-0">
              {mode === 'diagram' ? (
                <DiagramCanvas
                  key={versionId}
                  diagram={diagram}
                  selectedNodeId={selectedNodeId}
                  onSelectNode={selectNode}
                  onMoveNode={moveNodeGraph}
                  onConnect={addConnection}
                  onDeleteNode={deleteNode}
                  onDeleteConnection={deleteConnection}
                  onCreateAt={openCreateAt}
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
                    onClose={closePanel}
                    onSubmit={(spec, base, typeFields) =>
                      addNode(spec, base, typeFields, createPos.x, createPos.y)
                    }
                  />
                ) : panelMode === 'edit' && editState ? (
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
                ) : selectedNode ? (
                  <NodeDataPanel
                    node={selectedNode}
                    detail={selectedDetail}
                    busy={busy}
                    onUpdateBasics={updateNodeBasics}
                    onEditData={handleEditData}
                    onStartConnect={setConnectingFrom}
                    onDelete={(id) => {
                      void deleteNode(id);
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
          </>
        )}
      </div>
    </div>
  );
}
