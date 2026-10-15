import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties, type ReactNode } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { projectsApi } from '../api/projects';
import { getErrorMessage } from '../api/errors';
import { useActiveProject } from '../hooks/useActiveProject';
import { useDiagram } from '../hooks/useDiagram';
import { NewScenarioModal } from '../components/scenarios/NewScenarioModal';
import { SimulateModal } from '../components/scenarios/SimulateModal';
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

/**
 * The backend stores these as 32-bit floats, so 0.01 comes back as
 * 0.0099999997…; seven significant digits is all a float carries anyway.
 */
function tidy(value: number): number {
  return Number.isFinite(value) ? Number(value.toPrecision(7)) : 0;
}

/** Builds the edit form's initial values from a loaded node detail. */
function editStateFromDetail(detail: NodeDetail): EditState {
  const base: NodeBaseValues = {
    name: detail.name,
    state: detail.state,
    upkeepCosts: tidy(detail.upkeepCosts),
    operatingCosts: tidy(detail.operatingCosts),
    lifespanInMonths: detail.lifespanInMonths,
    maintenanceIntervalInDays: detail.maintenanceIntervalInDays,
    maintenanceDuration: detail.maintenanceDuration ?? 0,
    wastePercentage: tidy(detail.wastePercentage),
  };
  return {
    nodeId: detail.id,
    identity: detail.identity,
    graphData: detail.graphData ?? {},
    initial: {
      type: detail.type.nodeType,
      base,
      typeFields: Object.fromEntries(
        Object.entries(detail.attributes).map(([key, value]) => [key, tidy(value)]),
      ),
      declineCurve: detail.declineCurve && {
        ...detail.declineCurve,
        val_a: tidy(detail.declineCurve.val_a),
        val_b: detail.declineCurve.val_b == null ? null : tidy(detail.declineCurve.val_b),
      },
      investmentCost: detail.investmentCost ?? [],
    },
  };
}

const PANEL_WIDTH = 360;

const selectClass =
  'rounded-lg border border-ink-200 bg-white px-2.5 py-1.5 text-sm text-ink-700 shadow-sm ' +
  'focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-400/40 disabled:opacity-50';

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
      className="absolute z-20 flex max-h-[calc(100%-2rem)] w-[360px] flex-col overflow-hidden rounded-2xl border border-ink-100 bg-white shadow-xl ring-1 ring-black/5"
      style={style}
    >
      {children}
    </div>
  );
}

/**
 * The visual editor of a project's scenarios: pick a version, then edit its
 * diagram on a canvas or a map. Lives under the project
 * (`/projects/:projectId/editor`), with the open version in `?version=` so the
 * scenarios page and a shared link can open a specific one.
 */
export function EditorPage() {
  const { projectId = '' } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const { projects } = useActiveProject();
  const project = projects.find((p) => p.id === projectId) ?? null;
  const [versions, setVersions] = useState<VersionSummary[]>([]);
  const [versionsLoaded, setVersionsLoaded] = useState(false);
  const [selectorError, setSelectorError] = useState<string | null>(null);
  const [isNewScenarioOpen, setIsNewScenarioOpen] = useState(false);
  const [isSimulateOpen, setIsSimulateOpen] = useState(false);

  const requestedVersion = searchParams.get('version');
  const versionId =
    versions.find((v) => v.id === requestedVersion)?.id ?? versions[0]?.id ?? '';
  const currentVersion = versions.find((v) => v.id === versionId) ?? null;

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
        setConnectError(`Un nodo "${fromLabel}" no puede conectarse a "${toLabel}".`);
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
    // Also skip while the diagram on screen is still the previous scenario's:
    // right after switching, this effect runs (getNodeDetail changed) before
    // the old diagram and selection are replaced, and asking the new version for
    // the previous version's node answers 400 and flashes an error banner.
    if (
      !selectedNodeId ||
      diagram?.versionId !== versionId ||
      !diagram.nodes.some((n) => n.id === selectedNodeId)
    ) {
      setSelectedDetail(null);
      return;
    }
    void getNodeDetail(selectedNodeId).then((d) => {
      if (!cancelled) setSelectedDetail(d);
    });
    return () => {
      cancelled = true;
    };
  }, [selectedNodeId, diagram, versionId, getNodeDetail]);

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

  const loadVersions = useCallback(async () => {
    if (!projectId) return;
    setSelectorError(null);
    try {
      const res = await projectsApi.listVersions(projectId);
      setVersions(res.data.data ?? []);
    } catch (err) {
      setSelectorError(getErrorMessage(err, 'No se pudieron cargar los escenarios'));
    } finally {
      setVersionsLoaded(true);
    }
  }, [projectId]);

  useEffect(() => {
    setVersionsLoaded(false);
    void loadVersions();
  }, [loadVersions]);

  const openVersion = useCallback(
    (id: string) => setSearchParams(id ? { version: id } : {}, { replace: true }),
    [setSearchParams],
  );

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
        <div className="flex min-w-0 flex-col gap-0.5">
          <span className="px-0.5 text-[10px] font-semibold uppercase tracking-wide text-ink-500">
            Proyecto
          </span>
          <span className="truncate px-0.5 py-1.5 text-sm font-semibold text-ink-800">
            {project?.name ?? 'Proyecto'}
          </span>
        </div>

        <Field label="Escenario">
          <div className="flex items-center gap-1">
            <select
              className={selectClass}
              value={versionId}
              onChange={(e) => openVersion(e.target.value)}
              disabled={versions.length === 0}
            >
              {versions.length === 0 && <option value="">Sin escenarios</option>}
              {versions.map((v) => (
                <option key={v.id} value={v.id}>
                  {v.name}
                </option>
              ))}
            </select>
            <Button
              variant="ghost"
              onClick={() => setIsNewScenarioOpen(true)}
              disabled={!projectId}
              className="px-2 py-1.5 text-xs"
              title="Nuevo escenario"
            >
              + Nuevo
            </Button>
          </div>
        </Field>

        <div className="mx-1 h-8 w-px self-center bg-ink-100" />

        <Field label="Vista">
          <div className="inline-flex overflow-hidden rounded-lg border border-ink-200 shadow-sm">
            {(['diagram', 'map'] as Mode[]).map((m) => (
              <button
                key={m}
                onClick={() => setMode(m)}
                className={
                  'px-3 py-1.5 text-sm font-medium transition-colors ' +
                  (mode === m ? 'bg-brand-800 text-white' : 'bg-white text-ink-600 hover:bg-ink-50')
                }
              >
                {m === 'diagram' ? 'Diagrama' : 'Mapa'}
              </button>
            ))}
          </div>
        </Field>

        {mode === 'map' && (
          <Field label="Proyección">
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
                  {p === 'mercator' ? '2D' : 'Globo'}
                </button>
              ))}
            </div>
          </Field>
        )}

        <div className="ml-auto flex items-center gap-2 self-center pt-3">
          {(loading || busy) && <Spinner className="h-4 w-4 text-brand-600" />}
          <Link
            to={`/projects/${projectId}/scenarios`}
            className="rounded-lg px-3 py-2 text-sm font-medium text-brand-800 hover:bg-brand-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-400"
          >
            Escenarios
          </Link>
          <Button
            variant="secondary"
            onClick={() => setIsSimulateOpen(true)}
            disabled={!versionId || (diagram?.nodes.length ?? 0) === 0}
            className="text-sm"
            title={(diagram?.nodes.length ?? 0) === 0 ? 'Agregá nodos para poder simular' : undefined}
          >
            Simular
          </Button>
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
            {panelMode === 'create' ? 'Cancelar' : '+ Agregar nodo'}
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
          <div className="flex h-full w-full flex-col items-center justify-center gap-3 p-8 text-center text-sm text-ink-500">
            {versionsLoaded ? (
              <>
                <p>Este proyecto todavía no tiene escenarios. Creá uno para empezar a modelar.</p>
                <Button onClick={() => setIsNewScenarioOpen(true)}>Crear el primer escenario</Button>
              </>
            ) : (
              <Spinner className="h-6 w-6 text-brand-600" />
            )}
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
                    Cerrar
                  </button>
                </div>
              </div>
            )}

            {/* Connect-mode hint */}
            {connectingFrom && (
              <div className="pointer-events-none absolute left-1/2 top-4 z-30 -translate-x-1/2">
                <div className="pointer-events-auto flex items-center gap-3 rounded-full border border-brand-200 bg-brand-50 px-4 py-2 text-sm text-brand-800 shadow-md">
                  <span>
                    Conectando desde <strong>{connectingNode?.name ?? 'nodo'}</strong>: hacé clic en
                    el nodo destino
                  </span>
                  <button
                    onClick={() => setConnectingFrom(null)}
                    className="rounded-full px-2 py-0.5 text-xs font-semibold text-brand-700 hover:bg-brand-100"
                  >
                    Cancelar (Esc)
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
                    onSubmit={(spec, base, typeFields, extras) =>
                      addNode(spec, base, typeFields, extras, createPos.x, createPos.y)
                    }
                  />
                ) : panelMode === 'edit' && editState ? (
                  editState.initial ? (
                    <NodeFormPanel
                      mode="edit"
                      busy={busy}
                      initial={editState.initial}
                      onClose={closePanel}
                      onSubmit={(spec, base, typeFields, extras) =>
                        editNodeData(
                          editState.nodeId,
                          spec,
                          base,
                          typeFields,
                          extras,
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
                  <div className="p-4 text-sm text-ink-500">
                    Hacé doble clic en el lienzo vacío para agregar un nodo, o clic en un nodo para ver
                    sus datos.
                  </div>
                )}
              </FloatingPanel>
            )}
            </div>
          </>
        )}
      </div>

      <NewScenarioModal
        open={isNewScenarioOpen}
        onClose={() => setIsNewScenarioOpen(false)}
        projectId={projectId}
        versions={versions}
        defaultParentId={versionId || null}
        onCreated={(created) => {
          void loadVersions().then(() => openVersion(created.id));
        }}
      />
      {currentVersion && (
        <SimulateModal
          open={isSimulateOpen}
          onClose={() => setIsSimulateOpen(false)}
          versionId={currentVersion.id}
          versionName={currentVersion.name}
          onDone={(detail) => {
            setIsSimulateOpen(false);
            navigate(
              `/projects/${projectId}/scenarios/${currentVersion.id}/runs/${detail.summary.id}`,
            );
          }}
        />
      )}
    </div>
  );
}
