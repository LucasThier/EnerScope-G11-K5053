import { useId, useMemo, useState } from 'react';
import { Alert } from '../components/ui/Alert';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { PlusIcon, SearchIcon } from '../components/ui/icons';
import { DeactivateOrganizationDialog } from '../components/organizations/DeactivateOrganizationDialog';
import { EditOrganizationModal } from '../components/organizations/EditOrganizationModal';
import { NewOrganizationModal } from '../components/organizations/NewOrganizationModal';
import { OrganizationMembersModal } from '../components/organizations/OrganizationMembersModal';
import { OrganizationsTable } from '../components/organizations/OrganizationsTable';
import { organizationsApi } from '../api/organizations';
import { getErrorMessage } from '../api/errors';
import { useOrganizations } from '../hooks/useOrganizations';
import type { OrganizationSummary } from '../types/auth';

const controlClasses =
  'rounded-lg border border-ink-200 bg-white px-3 py-2 text-sm text-ink-800 ' +
  'focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-400/40';

export function AdminOrganizationsPage() {
  const searchId = useId();
  const { organizations, loading, error, reload, createOrganization } = useOrganizations();
  const [search, setSearch] = useState('');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [viewing, setViewing] = useState<OrganizationSummary | null>(null);
  const [renaming, setRenaming] = useState<OrganizationSummary | null>(null);
  const [deactivating, setDeactivating] = useState<OrganizationSummary | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  async function handleReactivate(organization: OrganizationSummary) {
    setActionError(null);
    try {
      await organizationsApi.reactivate(organization.id);
      await reload();
    } catch (err) {
      setActionError(getErrorMessage(err, 'No se pudo reactivar la organización'));
    }
  }

  const visibleOrganizations = useMemo(() => {
    const term = search.trim().toLowerCase();
    if (!term) {
      return organizations;
    }
    return organizations.filter((organization) =>
      organization.name.toLowerCase().includes(term),
    );
  }, [organizations, search]);

  return (
    <div>
      <header className="mb-6 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-ink-800">Gestión de organizaciones</h1>
          <p className="mt-1 text-sm text-ink-500">
            Las organizaciones de la plataforma y quiénes las integran.
          </p>
        </div>
        <Button onClick={() => setIsModalOpen(true)}>
          <PlusIcon className="h-4 w-4" />
          Nueva organización
        </Button>
      </header>

      {actionError && (
        <Alert tone="error" className="mb-4">
          {actionError}
        </Alert>
      )}

      <Card padded={false} className="overflow-hidden">
        <div className="flex flex-wrap items-center gap-3 border-b border-ink-100 px-4 py-4">
          <div className="relative min-w-56 flex-1">
            <SearchIcon className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-400" />
            <label htmlFor={searchId} className="sr-only">
              Buscar organizaciones
            </label>
            <input
              id={searchId}
              type="search"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Buscar organizaciones"
              className={`w-full pl-9 placeholder:text-ink-400 ${controlClasses}`}
            />
          </div>
        </div>

        {loading ? (
          <p className="px-4 py-8 text-center text-sm text-ink-500">Cargando organizaciones…</p>
        ) : error ? (
          <div className="px-4 py-6">
            <Alert tone="error">{error}</Alert>
          </div>
        ) : organizations.length === 0 ? (
          <div className="px-4 py-8 text-center">
            <p className="text-sm text-ink-500">Todavía no hay organizaciones.</p>
            <Button className="mt-4" onClick={() => setIsModalOpen(true)}>
              <PlusIcon className="h-4 w-4" />
              Nueva organización
            </Button>
          </div>
        ) : visibleOrganizations.length === 0 ? (
          <p className="px-4 py-8 text-center text-sm text-ink-500">
            Ninguna organización coincide con la búsqueda.
          </p>
        ) : (
          <OrganizationsTable
            organizations={visibleOrganizations}
            onViewMembers={setViewing}
            onRename={setRenaming}
            onDeactivate={setDeactivating}
            onReactivate={handleReactivate}
          />
        )}
      </Card>

      <NewOrganizationModal
        open={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onCreate={createOrganization}
      />

      <EditOrganizationModal
        organization={renaming}
        onClose={() => setRenaming(null)}
        onUpdated={reload}
      />

      <DeactivateOrganizationDialog
        organization={deactivating}
        onClose={() => setDeactivating(null)}
        onDeactivated={reload}
      />

      <OrganizationMembersModal
        organization={viewing}
        onClose={() => setViewing(null)}
        onChanged={reload}
      />
    </div>
  );
}
