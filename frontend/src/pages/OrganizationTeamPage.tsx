import { useCallback, useEffect, useId, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Alert } from '../components/ui/Alert';
import { Card } from '../components/ui/Card';
import { Modal } from '../components/ui/Modal';
import { MembersTable } from '../components/members/MembersTable';
import { AddOrganizationMemberCard } from '../components/organizations/AddOrganizationMemberCard';
import { MEMBER_TYPE_LABELS } from '../components/organizations/memberTypeLabels';
import { OrganizationRoleChangeConfirmation } from '../components/organizations/OrganizationRoleChangeConfirmation';
import { RemoveOrganizationMemberDialog } from '../components/organizations/RemoveOrganizationMemberDialog';
import { organizationsApi } from '../api/organizations';
import { getErrorMessage } from '../api/errors';
import { useMemberRoleChange } from '../hooks/useMemberRoleChange';
import { useOwnedOrganizations } from '../hooks/useOwnedOrganizations';
import type { OrganizationMemberSummary, OrganizationMemberType } from '../types/auth';

const controlClasses =
  'rounded-lg border border-ink-200 bg-white px-3 py-2 text-sm text-ink-800 ' +
  'focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-400/40';

export function OrganizationTeamPage() {
  const { organizationId } = useParams();
  const navigate = useNavigate();
  const switcherId = useId();
  const { owned, reload: reloadOwned } = useOwnedOrganizations();
  const [members, setMembers] = useState<OrganizationMemberSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [removing, setRemoving] = useState<OrganizationMemberSummary | null>(null);

  const organization = owned.find((candidate) => candidate.id === organizationId);

  const reloadMembers = useCallback(async () => {
    if (!organizationId) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const res = await organizationsApi.members(organizationId);
      setMembers(res.data.data ?? []);
    } catch (err) {
      setError(getErrorMessage(err, 'No se pudieron cargar los integrantes'));
    } finally {
      setLoading(false);
    }
  }, [organizationId]);

  useEffect(() => {
    void reloadMembers();
  }, [reloadMembers]);

  async function handleRemoved(removedSelf: boolean) {
    if (removedSelf) {
      await reloadOwned();
      navigate('/app', { replace: true });
      return;
    }
    await reloadMembers();
  }

  const handleRoleUpdated = useCallback((updated: OrganizationMemberSummary) => {
    setMembers((current) =>
      current.map((member) => (member.id === updated.id ? updated : member)),
    );
  }, []);

  const handleSelfDemoted = useCallback(async () => {
    await reloadOwned();
    navigate('/app', { replace: true });
  }, [navigate, reloadOwned]);

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
    onSelfDemoted: handleSelfDemoted,
  });

  if (!organizationId) {
    return null;
  }

  return (
    <div>
      <header className="mb-6 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-ink-800">
            {organization?.name ?? 'Equipo'}
          </h1>
          <p className="mt-1 text-sm text-ink-500">
            Quiénes integran tu organización. Podés agregar y quitar personas; el nombre y el
            estado de la organización los administra la plataforma.
          </p>
        </div>
        {owned.length > 1 && (
          <div>
            <label htmlFor={switcherId} className="sr-only">
              Elegir organización
            </label>
            <select
              id={switcherId}
              value={organizationId}
              onChange={(e) => navigate(`/organizations/${e.target.value}`)}
              className={controlClasses}
            >
              {owned.map((candidate) => (
                <option key={candidate.id} value={candidate.id}>
                  {candidate.name}
                </option>
              ))}
            </select>
          </div>
        )}
      </header>

      <Card padded={false} className="overflow-hidden">
        {loading ? (
          <p className="px-4 py-8 text-center text-sm text-ink-500">Cargando integrantes…</p>
        ) : error ? (
          <div className="px-4 py-6">
            <Alert tone="error">{error}</Alert>
          </div>
        ) : members.length === 0 ? (
          <p className="px-4 py-8 text-center text-sm text-ink-500">
            Esta organización todavía no tiene integrantes.
          </p>
        ) : (
          <MembersTable
            members={members}
            roleLabels={MEMBER_TYPE_LABELS}
            onRemove={setRemoving}
            onChangeRole={roleChange.requestChange}
            busyMemberId={roleChange.busyMemberId}
            roleError={roleChange.pending ? null : roleChange.error}
          />
        )}
      </Card>

      <AddOrganizationMemberCard organizationId={organizationId} onAdded={reloadMembers} />

      <Modal
        open={roleChange.pending !== null}
        onClose={roleChange.cancel}
        title="Cambiar el rol"
      >
        {roleChange.pending && (
          <OrganizationRoleChangeConfirmation
            change={roleChange.pending}
            organizationName={organization?.name ?? ''}
            error={roleChange.error}
            submitting={roleChange.busyMemberId !== null}
            onCancel={roleChange.cancel}
            onConfirm={() => void roleChange.confirm()}
          />
        )}
      </Modal>

      <RemoveOrganizationMemberDialog
        organizationId={organizationId}
        organizationName={organization?.name ?? ''}
        member={removing}
        onClose={() => setRemoving(null)}
        onRemoved={handleRemoved}
      />
    </div>
  );
}
