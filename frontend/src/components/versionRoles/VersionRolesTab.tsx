import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { Spinner } from '../ui/Spinner';
import { MyAccessCard } from './MyAccessCard';
import { VersionPicker } from './VersionPicker';
import { VersionRolesPanel } from './VersionRolesPanel';
import { defaultVersionId, versionRows } from '../../utils/versionRoles';
import type { VersionSummary } from '../../types/diagram';
import type { ProjectMember } from '../../types/project';
import type { ProjectAccess } from '../../types/versionAccess';

interface VersionRolesTabProps {
  projectId: string;
  access: ProjectAccess | null;
  summaries: VersionSummary[];
  loading: boolean;
  error: string | null;
  projectMembers: ProjectMember[];
  callerUserId: string | null;
  reload: () => Promise<void>;
}

export function VersionRolesTab({
  projectId,
  access,
  summaries,
  loading,
  error,
  projectMembers,
  callerUserId,
  reload,
}: VersionRolesTabProps) {
  const [params, setParams] = useSearchParams();
  const [query, setQuery] = useState('');
  const [onlyManageable, setOnlyManageable] = useState(false);

  if (loading) {
    return (
      <div className="flex items-center gap-2 text-sm text-ink-500" role="status">
        <Spinner className="h-4 w-4" /> Cargando accesos…
      </div>
    );
  }
  if (error || access === null) {
    return (
      <div className="flex flex-col items-start gap-3">
        <Alert tone="error" className="w-full">
          {error ?? 'No se pudieron cargar los accesos'}
        </Alert>
        <Button type="button" variant="ghost" size="compact" onClick={() => void reload()}>
          Reintentar
        </Button>
      </div>
    );
  }

  const allRows = versionRows(access, summaries, '', false);
  if (allRows.length === 0) {
    return (
      <Alert tone="info">
        Este proyecto todavía no tiene versiones.{' '}
        <Link to={`/projects/${projectId}/versions`} className="font-semibold underline">
          Ir a Versionado
        </Link>
      </Alert>
    );
  }

  const visibleRows = versionRows(access, summaries, query, onlyManageable);
  const selectedId = defaultVersionId(allRows, params.get('version'));
  const selected = access.versions.find((entry) => entry.versionId === selectedId) ?? null;

  function select(versionId: string) {
    setParams(
      (previous) => {
        const next = new URLSearchParams(previous);
        next.set('version', versionId);
        return next;
      },
      { replace: true },
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <MyAccessCard access={access} onOpenVersion={select} />

      <div className="grid gap-6 lg:grid-cols-[18rem_minmax(0,1fr)]">
        <VersionPicker
          rows={visibleRows}
          selectedId={selectedId}
          totalCount={allRows.length}
          query={query}
          onlyManageable={onlyManageable}
          canFilterManageable={allRows.some((row) => row.access.canManageRoles)}
          onSelect={select}
          onQueryChange={setQuery}
          onOnlyManageableChange={setOnlyManageable}
        />
        {selected && (
          <VersionRolesPanel
            key={selected.versionId}
            version={selected}
            projectMembers={projectMembers}
            callerUserId={callerUserId}
            onChanged={reload}
          />
        )}
      </div>
    </div>
  );
}
