import { useId, useMemo, useState } from 'react';
import { Alert } from '../components/ui/Alert';
import { EmptyState } from '../components/ui/EmptyState';
import { LoadingState } from '../components/ui/LoadingState';
import { PageHeader } from '../components/ui/PageHeader';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { SearchField } from '../components/ui/SearchField';
import { PlusIcon } from '../components/ui/icons';
import { DeactivateOrganizationDialog } from '../components/organizations/DeactivateOrganizationDialog';
import { EditOrganizationModal } from '../components/organizations/EditOrganizationModal';
import { NewOrganizationModal } from '../components/organizations/NewOrganizationModal';
import { OrganizationMembersModal } from '../components/organizations/OrganizationMembersModal';
import { OrganizationsTable } from '../components/organizations/OrganizationsTable';
import { organizationsApi } from '../api/organizations';
import { getErrorMessage } from '../api/errors';
import { useOrganizations } from '../hooks/useOrganizations';
import type { OrganizationSummary } from '../types/auth';

export function AdminOrganizationsPage() {
  const searchId = useId();
  const { organizations, loading, error, reload, createOrganization } = useOrganizations();
  const [search, setSearch] = useState('');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [viewing, setViewing] = useState<OrganizationSummary | null>(null);
  const [addingTo, setAddingTo] = useState<OrganizationSummary | null>(null);
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
      <PageHeader
        title="Gestión de organizaciones"
        description="Las organizaciones de la plataforma y quiénes las integran."
        actions={
          <Button onClick={() => setIsModalOpen(true)}>
            <PlusIcon className="h-4 w-4" />
            Nueva organización
          </Button>
        }
      />

      {actionError && (
        <Alert tone="error" className="mb-4">
          {actionError}
        </Alert>
      )}

      <Card padded={false} className="overflow-hidden">
        <div className="flex flex-wrap items-center gap-3 border-b border-ink-100 px-4 py-4">
          <SearchField
            id={searchId}
            label="Buscar organizaciones"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Buscar organizaciones"
            wrapperClassName="min-w-56 flex-1"
          />
        </div>

        {loading ? (
          <LoadingState>Cargando organizaciones…</LoadingState>
        ) : error ? (
          <div className="px-4 py-6">
            <Alert tone="error">{error}</Alert>
          </div>
        ) : organizations.length === 0 ? (
          <EmptyState
            action={
              <Button onClick={() => setIsModalOpen(true)}>
                  <PlusIcon className="h-4 w-4" />
                  Nueva organización
                </Button>
            }
          >
            Todavía no hay organizaciones.
          </EmptyState>
        ) : visibleOrganizations.length === 0 ? (
          <EmptyState>Ninguna organización coincide con la búsqueda.</EmptyState>
        ) : (
          <OrganizationsTable
            organizations={visibleOrganizations}
            onViewMembers={setViewing}
            onAddMembers={setAddingTo}
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
        organization={viewing ?? addingTo}
        initialStep={addingTo ? 'choose' : 'list'}
        onClose={() => {
          setViewing(null);
          setAddingTo(null);
        }}
        onChanged={reload}
      />
    </div>
  );
}
