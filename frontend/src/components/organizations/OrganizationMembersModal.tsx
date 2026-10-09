import { useCallback, useEffect, useState } from 'react';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { Modal } from '../ui/Modal';
import { MembersTable } from '../members/MembersTable';
import { MEMBER_TYPE_LABELS } from './memberTypeLabels';
import { OrganizationRoleChangeConfirmation } from './OrganizationRoleChangeConfirmation';
import { organizationsApi } from '../../api/organizations';
import { getErrorMessage } from '../../api/errors';
import { useAuth } from '../../hooks/useAuth';
import { useMemberRoleChange } from '../../hooks/useMemberRoleChange';
import type {
  OrganizationMemberSummary,
  OrganizationMemberType,
  OrganizationSummary,
} from '../../types/auth';

interface OrganizationMembersModalProps {
  organization: OrganizationSummary | null;
  onClose: () => void;
  onChanged: () => Promise<void>;
}

export function OrganizationMembersModal({
  organization,
  onClose,
  onChanged,
}: OrganizationMembersModalProps) {
  const { user: caller } = useAuth();
  const [members, setMembers] = useState<OrganizationMemberSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState<OrganizationMemberSummary | null>(null);
  const [removing, setRemoving] = useState(false);

  const organizationId = organization?.id ?? null;

  const handleRoleUpdated = useCallback(
    (updated: OrganizationMemberSummary) => {
      setMembers((current) =>
        current.map((member) => (member.id === updated.id ? updated : member)),
      );
      void onChanged();
    },
    [onChanged],
  );

  const updateRole = useCallback(
    async (member: OrganizationMemberSummary, memberType: OrganizationMemberType) => {
      const res = await organizationsApi.changeMemberRole(organizationId ?? '', member.id, {
        memberType,
      });
      return res.data.data ?? { ...member, memberType };
    },
    [organizationId],
  );

  const roleChange = useMemberRoleChange({
    members,
    adminRole: 'OWNER',
    updateRole,
    onUpdated: handleRoleUpdated,
  });
  const cancelRoleChange = roleChange.cancel;

  useEffect(() => {
    cancelRoleChange();
  }, [organizationId, cancelRoleChange]);

  useEffect(() => {
    if (!organizationId) {
      setMembers([]);
      setError(null);
      setConfirming(null);
      return;
    }

    let cancelled = false;
    setLoading(true);
    setError(null);
    setConfirming(null);

    organizationsApi
      .members(organizationId)
      .then((res) => {
        if (!cancelled) {
          setMembers(res.data.data ?? []);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setError(getErrorMessage(err, 'No se pudieron cargar los integrantes'));
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
  }, [organizationId]);

  async function reloadMembers() {
    if (!organizationId) {
      return;
    }
    try {
      const res = await organizationsApi.members(organizationId);
      setMembers(res.data.data ?? []);
    } catch (err) {
      setError(getErrorMessage(err, 'No se pudieron cargar los integrantes'));
    }
  }

  async function handleRemove() {
    if (!organizationId || !confirming) {
      return;
    }
    setError(null);
    setRemoving(true);
    try {
      await organizationsApi.removeMember(organizationId, confirming.id);
      setConfirming(null);
      await reloadMembers();
      await onChanged();
    } catch (err) {
      setError(getErrorMessage(err, 'No se pudo quitar al integrante'));
    } finally {
      setRemoving(false);
    }
  }

  const isSelf = confirming !== null && caller !== null && confirming.userId === caller.id;

  return (
    <Modal
      open={organization !== null}
      onClose={onClose}
      title={
        roleChange.pending
          ? 'Cambiar el rol'
          : confirming
            ? 'Quitar de la organización'
            : (organization?.name ?? '')
      }
      size="lg"
    >
      {roleChange.pending ? (
        <OrganizationRoleChangeConfirmation
          change={roleChange.pending}
          organizationName={organization?.name ?? ''}
          error={roleChange.error}
          submitting={roleChange.busyMemberId !== null}
          onCancel={roleChange.cancel}
          onConfirm={() => void roleChange.confirm()}
        />
      ) : confirming ? (
        <div className="flex flex-col gap-4">
          {error && <Alert tone="error">{error}</Alert>}

          <p className="text-sm text-ink-700">
            Se va a quitar a{' '}
            <span className="font-semibold text-ink-800">
              {confirming.firstName} {confirming.lastName}
            </span>{' '}
            de <span className="font-semibold text-ink-800">{organization?.name}</span>.
          </p>
          <p className="text-sm text-ink-700">
            También se le quitan sus membresías en los proyectos de esta organización. Si era
            el único administrador de alguno de ellos, ese proyecto queda sin administrador.
          </p>
          <p className="text-sm text-ink-500">
            La cuenta de plataforma no se toca: sigue pudiendo iniciar sesión y conserva sus
            otras organizaciones.
          </p>

          {isSelf && (
            <Alert tone="error">
              Estás quitando tu propia membresía. Dejás de figurar como integrante de esta
              organización; tu acceso continúa por tu rol de administrador de plataforma, no
              por esta membresía.
            </Alert>
          )}

          <div className="mt-2 flex justify-end gap-2">
            <Button
              type="button"
              variant="ghost"
              onClick={() => setConfirming(null)}
              disabled={removing}
            >
              Volver
            </Button>
            <Button type="button" variant="danger" onClick={handleRemove} loading={removing}>
              Quitar integrante
            </Button>
          </div>
        </div>
      ) : (
        <div>
          {error && <Alert tone="error">{error}</Alert>}

          <div className="mt-1">
            {loading ? (
              <p className="py-6 text-center text-sm text-ink-500">Cargando integrantes…</p>
            ) : members.length === 0 ? (
              <p className="py-6 text-center text-sm text-ink-500">
                Esta organización todavía no tiene integrantes.
              </p>
            ) : (
              <MembersTable
                members={members}
                roleLabels={MEMBER_TYPE_LABELS}
                onRemove={setConfirming}
                onChangeRole={roleChange.requestChange}
                busyMemberId={roleChange.busyMemberId}
                roleError={roleChange.error}
              />
            )}
          </div>
        </div>
      )}
    </Modal>
  );
}
