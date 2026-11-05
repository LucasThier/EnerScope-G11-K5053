import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, Navigate, useParams } from 'react-router-dom';
import { Alert } from '../components/ui/Alert';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { PageLoader } from '../components/ui/PageLoader';
import { PlusIcon } from '../components/ui/icons';
import { DeleteVersionDialog } from '../components/versions/DeleteVersionDialog';
import { MergeVersionDialog } from '../components/versions/MergeVersionDialog';
import { NewVersionModal } from '../components/versions/NewVersionModal';
import { ResolveConflictsDialog } from '../components/versions/ResolveConflictsDialog';
import { VersionTree } from '../components/versions/VersionTree';
import { useActiveProject } from '../hooks/useActiveProject';
import { useAuth } from '../hooks/useAuth';
import { useVersionConflicts } from '../hooks/useVersionConflicts';
import { useVersions } from '../hooks/useVersions';
import { buildVersionTree, getDescendants } from '../utils/versionTree';
import type { VersionSummary } from '../types/diagram';

interface PendingDeletion {
  version: VersionSummary;
  isRoot: boolean;
}

export function VersionsPage() {
  const { projectId } = useParams();
  return <VersionsWorkspace key={projectId} projectId={projectId} />;
}

function VersionsWorkspace({ projectId }: { projectId?: string }) {
  const { user } = useAuth();
  const { projects, isLoading: projectsLoading } = useActiveProject();
  const { versions, loading, error, errorStatus, reload, deleteVersion, mergeVersion } = useVersions(
    projectId ?? null,
  );
  const { unresolvedByVersion, reload: reloadConflicts, reloadVersion } =
    useVersionConflicts(versions);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [deleting, setDeleting] = useState<PendingDeletion | null>(null);
  const [merging, setMerging] = useState<VersionSummary | null>(null);
  const [resolving, setResolving] = useState<VersionSummary | null>(null);

  const project = projects.find((candidate) => candidate.id === projectId);
  const isPlatformAdmin = user?.platformRole === 'ADMIN';
  const canEdit = isPlatformAdmin || project?.myRole === 'ADMIN' || project?.myRole === 'EDITOR';

  const tree = useMemo(() => buildVersionTree(versions), [versions]);
  const descendants = useMemo(
    () => (deleting ? getDescendants(versions, deleting.version.id) : []),
    [deleting, versions],
  );
  const parentOf = useCallback(
    (version: VersionSummary | null) =>
      versions.find((candidate) => candidate.id === version?.parentVersionId) ?? null,
    [versions],
  );
  const mergeParent = useMemo(() => parentOf(merging), [parentOf, merging]);
  const resolveParent = useMemo(() => parentOf(resolving), [parentOf, resolving]);
  const mergeSiblings = useMemo(
    () =>
      merging
        ? versions.filter(
            (candidate) =>
              candidate.parentVersionId === merging.parentVersionId &&
              candidate.id !== merging.id,
          )
        : [],
    [versions, merging],
  );

  const handleMerge = useCallback(
    async (subId: string) => {
      await mergeVersion(subId);
      await reloadConflicts();
    },
    [mergeVersion, reloadConflicts],
  );

  const openResolve = useCallback(
    (versionId: string) => {
      setMerging(null);
      setResolving(versions.find((candidate) => candidate.id === versionId) ?? null);
    },
    [versions],
  );

  const handleMergeNow = useCallback((version: VersionSummary) => {
    setResolving(null);
    setMerging(version);
  }, []);

  useEffect(() => {
    setDeleting(null);
    setMerging(null);
    setResolving(null);
  }, [projectId]);

  if (!projectId) {
    return null;
  }
  if (errorStatus === 403) {
    return <Navigate to="/versions" replace />;
  }
  if (!projectsLoading && !projects.some((candidate) => candidate.id === projectId)) {
    return <Navigate to="/versions" replace />;
  }
  if (loading) {
    return <PageLoader />;
  }

  return (
    <div>
      <header className="mb-6 flex flex-wrap items-start justify-between gap-4">
        <div>
          <Link to="/versions" className="text-sm font-semibold text-brand-800 hover:underline">
            ← Versionado
          </Link>
          <h1 className="mt-2 text-2xl font-semibold text-ink-800">Versionado</h1>
          <p className="mt-1 text-sm text-ink-500">{project?.name ?? 'el proyecto'}</p>
        </div>
        {!error && canEdit && (
          <Button onClick={() => setIsModalOpen(true)}>
            <PlusIcon className="h-4 w-4" />
            Nueva versión
          </Button>
        )}
      </header>

      <Card padded={false} className="overflow-hidden">
        {error ? (
          <div className="px-4 py-6">
            <Alert tone="error">{error}</Alert>
          </div>
        ) : versions.length === 0 ? (
          <div className="px-4 py-8 text-center">
            <p className="text-sm text-ink-500">Este proyecto todavía no tiene versiones.</p>
            {canEdit && (
              <Button className="mt-4" onClick={() => setIsModalOpen(true)}>
                <PlusIcon className="h-4 w-4" />
                Nueva versión
              </Button>
            )}
          </div>
        ) : (
          <VersionTree
            nodes={tree}
            canEdit={canEdit}
            unresolvedConflicts={unresolvedByVersion}
            onDelete={(version, isRoot) => setDeleting({ version, isRoot })}
            onMerge={setMerging}
            onResolveConflicts={setResolving}
          />
        )}
      </Card>

      <ResolveConflictsDialog
        version={resolving}
        parent={resolveParent}
        canEdit={canEdit}
        onResolved={reloadVersion}
        onMergeNow={handleMergeNow}
        onClose={() => setResolving(null)}
      />

      {canEdit && (
        <>
          <NewVersionModal
            projectId={projectId}
            open={isModalOpen}
            onClose={() => setIsModalOpen(false)}
            onCreated={reload}
          />

          <DeleteVersionDialog
            version={deleting?.version ?? null}
            descendants={descendants}
            isRoot={deleting?.isRoot ?? false}
            onDelete={deleteVersion}
            onRefresh={reload}
            onClose={() => setDeleting(null)}
          />

          <MergeVersionDialog
            version={merging}
            parent={mergeParent}
            siblings={mergeSiblings}
            onMerge={handleMerge}
            onRefresh={reload}
            onResolveConflicts={openResolve}
            onClose={() => setMerging(null)}
          />
        </>
      )}
    </div>
  );
}
