import { useCallback, useEffect, useState } from 'react';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { Modal, ModalFooter } from '../ui/Modal';
import { PlusIcon, UploadIcon } from '../ui/icons';
import { ImportUsersCsvFlow } from '../import/ImportUsersCsvFlow';
import { MembersTable } from '../members/MembersTable';
import { AddOrganizationMemberCard } from './AddOrganizationMemberCard';
import { MEMBER_TYPE_LABELS } from './memberTypeLabels';
import { OrganizationRoleChangeConfirmation } from './OrganizationRoleChangeConfirmation';
import { organizationsApi } from '../../api/organizations';
import { getErrorMessage } from '../../api/errors';
import { useAuth } from '../../hooks/useAuth';
import { useCsvUserImport } from '../../hooks/useCsvUserImport';
import { useMemberRoleChange } from '../../hooks/useMemberRoleChange';
import { canManageOrganizationUsers, lastOwnerReason } from '../../utils/membershipGuards';
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
  const [importing, setImporting] = useState(false);
  const [adding, setAdding] = useState(false);

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

  const reloadMembers = useCallback(async () => {
    if (!organizationId) {
      return;
    }
    try {
      const res = await organizationsApi.members(organizationId);
      setMembers(res.data.data ?? []);
    } catch (err) {
      setError(getErrorMessage(err, 'No se pudieron cargar los integrantes'));
    }
  }, [organizationId]);

  const handleImported = useCallback(async () => {
    await reloadMembers();
    await onChanged();
  }, [onChanged, reloadMembers]);

  const importFlow = useCsvUserImport({
    organizationId: organizationId ?? '',
    onImported: handleImported,
    onClose: () => setImporting(false),
  });

  useEffect(() => {
    cancelRoleChange();
  }, [organizationId, cancelRoleChange]);

  useEffect(() => {
    setImporting(false);
    setAdding(false);
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
      onClose={importing ? importFlow.requestClose : adding ? () => setAdding(false) : onClose}
      title={
        importing
          ? 'Importar usuarios por CSV'
          : adding
            ? 'Agregar integrante'
            : roleChange.pending
              ? 'Cambiar el rol'
              : confirming
                ? 'Quitar de la organización'
                : (organization?.name ?? '')
      }
      subtitle={importing || adding ? (organization?.name ?? undefined) : undefined}
      size="lg"
    >
      {importing ? (
        <ImportUsersCsvFlow flow={importFlow} organizationName={organization?.name ?? ''} />
      ) : adding ? (
        <div className="flex flex-col gap-4">
          <AddOrganizationMemberCard
            organizationId={organizationId ?? ''}
            onAdded={handleImported}
            variant="plain"
            lookup="list"
            excludeUserIds={members.map((member) => member.userId)}
          />
          <p className="text-sm text-ink-500">
            La persona tiene que tener una cuenta en la plataforma. Si todavía no la tiene,
            creala desde «Gestión de usuarios» o sumá varias de una vez con «Importar CSV».
          </p>
          <div className="flex justify-end border-t border-ink-100 pt-4">
            <Button variant="ghost" onClick={() => setAdding(false)}>
              Volver a los integrantes
            </Button>
          </div>
        </div>
      ) : roleChange.pending ? (
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
            También se le quitan sus membresías en los proyectos de esta organización. Si es la
            única persona con rol Administrador en alguno de ellos, la operación se rechaza y te
            lo avisamos.
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

          <ModalFooter>
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
          </ModalFooter>
        </div>
      ) : (
        <div>
          {error && <Alert tone="error">{error}</Alert>}

          {canManageOrganizationUsers(caller, members) && (
            <div className="mb-4 flex flex-wrap justify-end gap-2">
              <Button variant="secondary" onClick={() => setAdding(true)}>
                <PlusIcon className="h-4 w-4" />
                Agregar integrante
              </Button>
              <Button
                variant="secondary"
                onClick={() => {
                  importFlow.reset();
                  setImporting(true);
                }}
              >
                <UploadIcon className="h-4 w-4" />
                Importar CSV
              </Button>
            </div>
          )}

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
                disabledReason={(member) => lastOwnerReason(members, member)}
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
