import { useId, useMemo, useState } from 'react';
import { Alert } from '../components/ui/Alert';
import { EmptyState } from '../components/ui/EmptyState';
import { LoadingState } from '../components/ui/LoadingState';
import { PageHeader } from '../components/ui/PageHeader';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { FilterSelect } from '../components/ui/FilterSelect';
import { SearchField } from '../components/ui/SearchField';
import { PlusIcon } from '../components/ui/icons';
import { ChangeRoleModal } from '../components/users/ChangeRoleModal';
import { DeactivateUserDialog } from '../components/users/DeactivateUserDialog';
import { NewUserModal } from '../components/users/NewUserModal';
import { UserDetailModal } from '../components/users/UserDetailModal';
import { UsersTable } from '../components/users/UsersTable';
import { usersApi } from '../api/users';
import { getErrorMessage } from '../api/errors';
import { useUsers } from '../hooks/useUsers';
import type { UserListItem } from '../types/auth';

const ALL = 'all';

const ROLE_OPTIONS = [
  { value: ALL, label: 'Todos los roles' },
  { value: 'ADMIN', label: 'Administradores' },
  { value: 'USER', label: 'Usuarios' },
];

const STATUS_OPTIONS = [
  { value: ALL, label: 'Todos los estados' },
  { value: 'active', label: 'Activos' },
  { value: 'suspended', label: 'Inactivos' },
];

export function AdminUsersPage() {
  const searchId = useId();
  const { users, loading, error, reload } = useUsers();
  const [search, setSearch] = useState('');
  const [role, setRole] = useState(ALL);
  const [status, setStatus] = useState(ALL);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [viewingId, setViewingId] = useState<string | null>(null);
  const [action, setAction] = useState<{ kind: 'role' | 'deactivate'; id: string } | null>(null);
  const [detailRefresh, setDetailRefresh] = useState(0);
  const [actionError, setActionError] = useState<string | null>(null);

  // The modals follow the list, so after an action they show the new role and state.
  const userById = (id: string | null) => users.find((candidate) => candidate.id === id) ?? null;
  const viewing = userById(viewingId);
  const changingRole = action?.kind === 'role' ? userById(action.id) : null;
  const deactivating = action?.kind === 'deactivate' ? userById(action.id) : null;

  async function reactivate(user: UserListItem) {
    await usersApi.reactivate(user.id);
    await reload();
    setDetailRefresh((n) => n + 1);
  }

  async function handleReactivate(user: UserListItem) {
    setActionError(null);
    try {
      await reactivate(user);
    } catch (err) {
      setActionError(getErrorMessage(err, 'No se pudo reactivar la cuenta'));
    }
  }

  // Back from an action to the detail it was opened from (if any), read again.
  function closeAction() {
    setAction(null);
    setDetailRefresh((n) => n + 1);
  }

  const hasActiveFilters = search.trim() !== '' || role !== ALL || status !== ALL;

  function clearFilters() {
    setSearch('');
    setRole(ALL);
    setStatus(ALL);
  }

  const visibleUsers = useMemo(() => {
    const term = search.trim().toLowerCase();
    return users.filter((user) => {
      if (role !== ALL && user.platformRole !== role) {
        return false;
      }
      if (status !== ALL && user.active !== (status === 'active')) {
        return false;
      }
      if (!term) {
        return true;
      }
      return (
        user.firstName.toLowerCase().includes(term) ||
        user.lastName.toLowerCase().includes(term) ||
        user.mail.toLowerCase().includes(term) ||
        (user.jobTitle ?? '').toLowerCase().includes(term)
      );
    });
  }, [users, search, role, status]);

  return (
    <div>
      <PageHeader
        title="Gestión de usuarios"
        description="Todas las cuentas de la plataforma, incluidas las inactivas y las que no pertenecen a ninguna organización."
        actions={
          <Button onClick={() => setIsModalOpen(true)}>
            <PlusIcon className="h-4 w-4" />
            Crear usuario
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
            label="Buscar usuarios"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Buscar por nombre, email o puesto"
            wrapperClassName="min-w-56 flex-1"
          />
          <FilterSelect
            label="Filtrar por rol"
            value={role}
            options={ROLE_OPTIONS}
            onChange={setRole}
          />
          <FilterSelect
            label="Filtrar por estado"
            value={status}
            options={STATUS_OPTIONS}
            onChange={setStatus}
          />
          {hasActiveFilters && !loading && !error && users.length > 0 && (
            <p className="text-sm text-ink-500" aria-live="polite">
              {visibleUsers.length} de {users.length} usuarios
            </p>
          )}
        </div>

        {loading ? (
          <LoadingState>Cargando usuarios…</LoadingState>
        ) : error ? (
          <div className="px-4 py-6">
            <Alert tone="error">{error}</Alert>
          </div>
        ) : users.length === 0 ? (
          <EmptyState>Todavía no hay usuarios.</EmptyState>
        ) : visibleUsers.length === 0 ? (
          <EmptyState
            action={
              <Button variant="secondary" onClick={clearFilters}>
                  Limpiar filtros
                </Button>
            }
          >
            Ningún usuario coincide con los filtros aplicados.
          </EmptyState>
        ) : (
          <UsersTable
            users={visibleUsers}
            onViewDetail={(user) => setViewingId(user.id)}
            onChangeRole={(user) => setAction({ kind: 'role', id: user.id })}
            onDeactivate={(user) => setAction({ kind: 'deactivate', id: user.id })}
            onReactivate={handleReactivate}
          />
        )}
      </Card>

      <NewUserModal
        open={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onCreated={reload}
      />

      {/* Hidden while an action is open: two stacked modals would fight over focus and Escape. */}
      <UserDetailModal
        user={action ? null : viewing}
        refreshToken={detailRefresh}
        onClose={() => setViewingId(null)}
        onChangeRole={(user) => setAction({ kind: 'role', id: user.id })}
        onDeactivate={(user) => setAction({ kind: 'deactivate', id: user.id })}
        onReactivate={reactivate}
      />

      <ChangeRoleModal user={changingRole} onClose={closeAction} onChanged={reload} />

      <DeactivateUserDialog user={deactivating} onClose={closeAction} onDeactivated={reload} />
    </div>
  );
}
