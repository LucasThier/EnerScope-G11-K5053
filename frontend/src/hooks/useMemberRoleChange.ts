import { useCallback, useState } from 'react';
import { getErrorMessage } from '../api/errors';
import { useAuth } from './useAuth';

export interface RoleChangeMember {
  id: string;
  userId: string;
  firstName: string;
  lastName: string;
  memberType: string;
}

export interface PendingRoleChange<M extends RoleChangeMember> {
  member: M;
  memberType: M['memberType'];
  isSelf: boolean;
  isLastAdmin: boolean;
}

export interface RoleChangeError {
  memberId: string;
  message: string;
}

interface UseMemberRoleChangeOptions<M extends RoleChangeMember> {
  members: M[];
  adminRole: M['memberType'];
  countsAsAdmin?: (member: M) => boolean;
  updateRole: (member: M, memberType: M['memberType']) => Promise<M>;
  onUpdated: (member: M) => void;
  onSelfDemoted?: () => Promise<void> | void;
}

export function useMemberRoleChange<M extends RoleChangeMember>({
  members,
  adminRole,
  countsAsAdmin,
  updateRole,
  onUpdated,
  onSelfDemoted,
}: UseMemberRoleChangeOptions<M>) {
  const { user: caller } = useAuth();
  const [pending, setPending] = useState<PendingRoleChange<M> | null>(null);
  const [busyMemberId, setBusyMemberId] = useState<string | null>(null);
  const [error, setError] = useState<RoleChangeError | null>(null);

  const apply = useCallback(
    async (member: M, memberType: M['memberType']) => {
      setError(null);
      setBusyMemberId(member.id);
      try {
        onUpdated(await updateRole(member, memberType));
        return true;
      } catch (err) {
        setError({
          memberId: member.id,
          message: getErrorMessage(err, 'No se pudo cambiar el rol del integrante'),
        });
        return false;
      } finally {
        setBusyMemberId(null);
      }
    },
    [updateRole, onUpdated],
  );

  const requestChange = useCallback(
    (member: M, memberType: M['memberType']) => {
      if (member.memberType === memberType) {
        return;
      }
      const isAdmin = countsAsAdmin ?? ((candidate: M) => candidate.memberType === adminRole);
      const demotingAdmin = member.memberType === adminRole && memberType !== adminRole;
      const isSelf = caller !== null && member.userId === caller.id;
      const isLastAdmin =
        demotingAdmin && isAdmin(member) && members.filter(isAdmin).length === 1;
      if (demotingAdmin && (isSelf || isLastAdmin)) {
        setError(null);
        setPending({ member, memberType, isSelf, isLastAdmin });
        return;
      }
      void apply(member, memberType);
    },
    [adminRole, apply, caller, countsAsAdmin, members],
  );

  const confirm = useCallback(async () => {
    if (!pending) {
      return;
    }
    const done = await apply(pending.member, pending.memberType);
    if (!done) {
      return;
    }
    const wasSelf = pending.isSelf;
    setPending(null);
    if (wasSelf) {
      await onSelfDemoted?.();
    }
  }, [apply, onSelfDemoted, pending]);

  const cancel = useCallback(() => {
    setPending(null);
    setError(null);
  }, []);

  return { pending, busyMemberId, error, requestChange, confirm, cancel };
}
