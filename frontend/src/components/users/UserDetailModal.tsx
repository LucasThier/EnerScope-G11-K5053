import { useEffect, useState, type ReactNode } from 'react';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { Modal, ModalFooter } from '../ui/Modal';
import { RoleBadge } from '../ui/RoleBadge';
import { usersApi } from '../../api/users';
import { getErrorMessage } from '../../api/errors';
import {
  organizationRoleLabel,
  projectRoleLabel,
  userDetailActions,
  userStatusLabel,
} from '../../utils/userDetail';
import type { UserDetail, UserListItem } from '../../types/auth';

interface UserDetailModalProps {
  user: UserListItem | null;
  /** Bumped by the page after something changed the account, so the detail is read again. */
  refreshToken?: number;
  onClose: () => void;
  onChangeRole: (user: UserListItem) => void;
  onDeactivate: (user: UserListItem) => void;
  /** Rejects when the account could not be reactivated; the error is shown inside the detail. */
  onReactivate: (user: UserListItem) => Promise<void>;
}

export function UserDetailModal({
  user,
  refreshToken = 0,
  onClose,
  onChangeRole,
  onDeactivate,
  onReactivate,
}: UserDetailModalProps) {
  const [detail, setDetail] = useState<UserDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [actionError, setActionError] = useState<string | null>(null);
  const [reactivating, setReactivating] = useState(false);

  const userId = user?.id ?? null;

  useEffect(() => {
    if (!userId) {
      setDetail(null);
      setError(null);
      setActionError(null);
      return;
    }

    let cancelled = false;
    setLoading(true);
    setError(null);

    usersApi
      .detail(userId)
      .then((res) => {
        if (!cancelled) {
          setDetail(res.data.data ?? null);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setError(getErrorMessage(err, 'No se pudo cargar el detalle del usuario'));
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [userId, refreshToken, attempt]);

  async function handleReactivate() {
    if (!user) {
      return;
    }
    setActionError(null);
    setReactivating(true);
    try {
      await onReactivate(user);
    } catch (err) {
      setActionError(getErrorMessage(err, 'No se pudo reactivar la cuenta'));
    } finally {
      setReactivating(false);
    }
  }

  const title = user ? `${user.firstName} ${user.lastName}` : '';

  return (
    <Modal
      open={user !== null}
      onClose={onClose}
      title={title}
      subtitle={detail?.mail ?? user?.mail}
      size="lg"
    >
      {loading && !detail ? (
        <p className="py-6 text-center text-sm text-ink-500">Cargando detalle…</p>
      ) : error ? (
        <div className="flex flex-col items-start gap-3">
          <Alert tone="error">{error}</Alert>
          <Button variant="secondary" onClick={() => setAttempt((n) => n + 1)}>
            Reintentar
          </Button>
        </div>
      ) : detail ? (
        <div className="space-y-6">
          <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field label="Puesto">{detail.jobTitle ?? '—'}</Field>
            <Field label="Rol y estado">
              <div className="flex flex-wrap items-center gap-2">
                <RoleBadge role={detail.platformRole} />
                <Badge tone={detail.active ? 'brand' : 'neutral'}>
                  {userStatusLabel(detail.active)}
                </Badge>
              </div>
            </Field>
          </dl>

          <section>
            <SectionHeading label="Organizaciones" count={detail.organizations.length} />
            <ul className="divide-y divide-ink-100">
              {detail.organizations.length === 0 && (
                <EmptyRow>No pertenece a ninguna organización.</EmptyRow>
              )}
              {detail.organizations.map((membership) => (
                <li
                  key={membership.organizationId}
                  className="flex items-center justify-between gap-3 py-2 text-sm"
                >
                  <span className="flex min-w-0 flex-wrap items-center gap-2 font-semibold text-ink-800">
                    {membership.organizationName}
                    {!membership.organizationActive && <Badge tone="neutral">Inactiva</Badge>}
                  </span>
                  <Badge tone="brand">{organizationRoleLabel(membership.memberType)}</Badge>
                </li>
              ))}
            </ul>
          </section>

          <section>
            <SectionHeading label="Proyectos" count={detail.projects.length} />
            <ul className="divide-y divide-ink-100">
              {detail.projects.length === 0 && (
                <EmptyRow>No participa de ningún proyecto.</EmptyRow>
              )}
              {detail.projects.map((membership) => (
                <li
                  key={membership.projectId}
                  className="flex items-center justify-between gap-3 py-2 text-sm"
                >
                  <div className="min-w-0">
                    <span className="flex flex-wrap items-center gap-2 font-semibold text-ink-800">
                      {membership.projectName}
                      {!membership.organizationActive && <Badge tone="neutral">Inactiva</Badge>}
                    </span>
                    <span className="block text-xs text-ink-500">
                      {membership.organizationName}
                    </span>
                  </div>
                  <Badge tone="brand">{projectRoleLabel(membership.memberType)}</Badge>
                </li>
              ))}
            </ul>
          </section>

          {actionError && <Alert tone="error">{actionError}</Alert>}

          {user && (
            <ModalFooter>
              {userDetailActions(user).map((action) =>
                action === 'changeRole' ? (
                  <Button key={action} variant="secondary" onClick={() => onChangeRole(user)}>
                    Cambiar rol
                  </Button>
                ) : action === 'deactivate' ? (
                  <Button key={action} variant="danger" onClick={() => onDeactivate(user)}>
                    Dar de baja
                  </Button>
                ) : (
                  <Button
                    key={action}
                    variant="secondary"
                    loading={reactivating}
                    onClick={() => void handleReactivate()}
                  >
                    Reactivar cuenta
                  </Button>
                ),
              )}
            </ModalFooter>
          )}
        </div>
      ) : null}
    </Modal>
  );
}

const BADGE_TONES = {
  brand: 'bg-brand-100 text-brand-900',
  neutral: 'bg-ink-100 text-ink-600',
};

function Badge({ tone, children }: { tone: keyof typeof BADGE_TONES; children: ReactNode }) {
  return (
    <span
      className={`inline-flex shrink-0 items-center rounded-full px-2 py-1 text-xs font-semibold ${BADGE_TONES[tone]}`}
    >
      {children}
    </span>
  );
}

function SectionHeading({ label, count }: { label: string; count: number }) {
  return (
    <h3 className="border-b border-ink-100 pb-2 text-xs font-medium uppercase tracking-wide text-ink-500">
      {label} · {count}
    </h3>
  );
}

function EmptyRow({ children }: { children: ReactNode }) {
  return <li className="py-2 text-sm text-ink-500">{children}</li>;
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-xs font-medium uppercase tracking-wide text-ink-500">{label}</dt>
      <dd className="mt-1 text-sm text-ink-700">{children}</dd>
    </div>
  );
}
