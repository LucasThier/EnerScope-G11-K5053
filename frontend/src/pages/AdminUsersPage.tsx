import { useId, useMemo, useState } from 'react';
import { Alert } from '../components/ui/Alert';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { controlClasses } from '../components/ui/controlClasses';
import { FilterSelect } from '../components/ui/FilterSelect';
import { PlusIcon, SearchIcon } from '../components/ui/icons';
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
  const [viewing, setViewing] = useState<UserListItem | null>(null);
  const [changingRole, setChangingRole] = useState<UserListItem | null>(null);
  const [deactivating, setDeactivating] = useState<UserListItem | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  async function handleReactivate(user: UserListItem) {
    setActionError(null);
    try {
      await usersApi.reactivate(user.id);
      await reload();
    } catch (err) {
      setActionError(getErrorMessage(err, 'No se pudo reactivar la cuenta'));
    }
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
      <header className="mb-6 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-ink-800">Gestión de usuarios</h1>
          <p className="mt-1 text-sm text-ink-500">
            Todas las cuentas de la plataforma, incluidas las inactivas y las que no
            pertenecen a ninguna organización.
          </p>
        </div>
        <Button onClick={() => setIsModalOpen(true)}>
          <PlusIcon className="h-4 w-4" />
          Crear usuario
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
              Buscar usuarios
            </label>
            <input
              id={searchId}
              type="search"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Buscar por nombre, email o puesto"
              className={`w-full pl-9 placeholder:text-ink-400 ${controlClasses}`}
            />
          </div>
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
          <p className="px-4 py-8 text-center text-sm text-ink-500">Cargando usuarios…</p>
        ) : error ? (
          <div className="px-4 py-6">
            <Alert tone="error">{error}</Alert>
          </div>
        ) : users.length === 0 ? (
          <p className="px-4 py-8 text-center text-sm text-ink-500">Todavía no hay usuarios.</p>
        ) : visibleUsers.length === 0 ? (
          <div className="px-4 py-8 text-center">
            <p className="text-sm text-ink-500">
              Ningún usuario coincide con los filtros aplicados.
            </p>
            <Button variant="secondary" className="mt-3" onClick={clearFilters}>
              Limpiar filtros
            </Button>
          </div>
        ) : (
          <UsersTable
            users={visibleUsers}
            onViewDetail={setViewing}
            onChangeRole={setChangingRole}
            onDeactivate={setDeactivating}
            onReactivate={handleReactivate}
          />
        )}
      </Card>

      <NewUserModal
        open={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onCreated={reload}
      />

      <UserDetailModal user={viewing} onClose={() => setViewing(null)} />

      <ChangeRoleModal
        user={changingRole}
        onClose={() => setChangingRole(null)}
        onChanged={reload}
      />

      <DeactivateUserDialog
        user={deactivating}
        onClose={() => setDeactivating(null)}
        onDeactivated={reload}
      />
    </div>
  );
}
