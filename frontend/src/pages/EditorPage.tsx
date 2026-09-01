import { useCallback, useEffect, useMemo, useState } from 'react';
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
import type { Project, VersionSummary } from '../types/diagram';

type Mode = 'diagram' | 'map';
type Projection = 'mercator' | 'globe';
type PanelMode = 'data' | 'create' | 'edit';

interface EditState {
  nodeId: string;
  identity: string;
  graphData: GraphDataInput;
  initial: NodeFormInitial;
}

const selectClass =
  'rounded-lg border border-ink-200 bg-white px-3 py-2 text-sm text-ink-800 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-400/40';

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
  const [editState, setEditState] = useState<EditState | null>(null);

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

  // Selecting a node shows its data panel (leaves the create/edit form).
  const selectNode = useCallback((id: string | null) => {
    setSelectedNodeId(id);
    if (id) setPanelMode('data');
  }, []);

  const openCreateAt = useCallback((x: number, y: number) => {
    setCreatePos({ x, y });
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

  // Load projects when the organization changes.
  useEffect(() => {
    loadProjects();
  }, [loadProjects]);

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

  // Load versions when the project changes.
  useEffect(() => {
    loadVersions();
  }, [loadVersions]);

  const selectedNode = useMemo(
    () => diagram?.nodes.find((n) => n.id === selectedNodeId) ?? null,
    [diagram, selectedNodeId],
  );

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

  const nextNodePosition = useMemo(() => {
    const count = diagram?.nodes.length ?? 0;
    return { x: 120 + (count % 6) * 40, y: 120 + (count % 6) * 40 };
  }, [diagram?.nodes.length]);

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      {/* Toolbar */}
      <div className="flex flex-wrap items-center gap-3 border-b border-ink-100 bg-white px-4 py-2">
        <select className={selectClass} value={orgId} onChange={(e) => setOrgId(e.target.value)}>
          {organizations.length === 0 && <option value="">No organizations</option>}
          {organizations.map((o) => (
            <option key={o.id} value={o.id}>
              {o.name}
            </option>
          ))}
        </select>

        <Button variant="ghost" onClick={handleCreateOrganization} className="text-xs">
          + New org
        </Button>

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

        <Button variant="ghost" onClick={handleCreateProject} disabled={!orgId} className="text-xs">
          + New project
        </Button>

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

        <Button variant="ghost" onClick={handleCreateVersion} disabled={!projectId} className="text-xs">
          + New version
        </Button>

        <div className="mx-2 h-6 w-px bg-ink-100" />

        {/* Mode toggle */}
        <div className="inline-flex overflow-hidden rounded-lg border border-ink-200">
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

        {mode === 'map' && (
          <div className="inline-flex overflow-hidden rounded-lg border border-ink-200">
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
        )}

        <div className="ml-auto flex items-center gap-2">
          {(loading || busy) && <Spinner className="h-4 w-4 text-brand-600" />}
          <Button
            onClick={() => {
              if (panelMode === 'create') {
                setPanelMode('data');
              } else {
                setCreatePos(nextNodePosition);
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

      {/* Editor body */}
      <div className="flex min-h-0 flex-1">
        <div className="relative min-w-0 flex-1">
          {!versionId ? (
            <div className="flex h-full items-center justify-center p-8 text-center text-sm text-ink-400">
              Pick an organization, project and version to start — or create a version to begin a new
              diagram.
            </div>
          ) : !diagram ? (
            <div className="flex h-full items-center justify-center">
              <Spinner className="h-6 w-6 text-brand-600" />
            </div>
          ) : mode === 'diagram' ? (
            <div className="h-full w-full">
              <DiagramCanvas
                diagram={diagram}
                selectedNodeId={selectedNodeId}
                onSelectNode={selectNode}
                onMoveNode={moveNodeGraph}
                onConnect={addConnection}
                onDeleteNode={deleteNode}
                onDeleteConnection={deleteConnection}
                onCreateAt={openCreateAt}
              />
            </div>
          ) : (
            <MapView
              diagram={diagram}
              projection={projection}
              selectedNodeId={selectedNodeId}
              onSelectNode={selectNode}
              onMoveGeo={moveNodeGeo}
            />
          )}
        </div>

        <aside className="w-80 shrink-0 overflow-y-auto border-l border-ink-100 bg-white">
          {panelMode === 'create' ? (
            <NodeFormPanel
              mode="create"
              busy={busy}
              onClose={() => setPanelMode('data')}
              onSubmit={(spec, base, typeFields) =>
                addNode(spec, base, typeFields, createPos.x, createPos.y)
              }
            />
          ) : panelMode === 'edit' && editState ? (
            <NodeFormPanel
              mode="edit"
              busy={busy}
              initial={editState.initial}
              onClose={() => setPanelMode('data')}
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
            <NodeDataPanel
              node={selectedNode}
              busy={busy}
              onUpdateBasics={updateNodeBasics}
              onEditData={handleEditData}
              onDelete={(id) => {
                void deleteNode(id);
                setSelectedNodeId(null);
                setPanelMode('data');
              }}
            />
          )}
        </aside>
      </div>
    </div>
  );
}
