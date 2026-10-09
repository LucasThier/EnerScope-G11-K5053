import { useState, type FormEvent } from 'react';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { TextField } from '../ui/TextField';
import { AddMemberCard, type SelectedUser } from '../members/AddMemberCard';
import { organizationsApi } from '../../api/organizations';
import { usersApi } from '../../api/users';
import { getErrorMessage } from '../../api/errors';
import { MEMBER_TYPE_LABELS } from './memberTypeLabels';
import type { OrganizationMemberType, UserSearchResult } from '../../types/auth';

interface AddOrganizationMemberCardProps {
  organizationId: string;
  onAdded: () => Promise<void>;
}

export function AddOrganizationMemberCard({
  organizationId,
  onAdded,
}: AddOrganizationMemberCardProps) {
  const [mail, setMail] = useState('');
  const [found, setFound] = useState<UserSearchResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [searching, setSearching] = useState(false);

  async function handleSearch(event: FormEvent) {
    event.preventDefault();
    const trimmed = mail.trim();
    if (!trimmed) {
      return;
    }
    setError(null);
    setFound(null);
    setSearching(true);
    try {
      const res = await usersApi.searchByMail(trimmed);
      setFound(res.data.data ?? null);
    } catch (err) {
      setError(getErrorMessage(err, 'No se encontró ninguna cuenta con ese email'));
    } finally {
      setSearching(false);
    }
  }

  async function handleAdd(user: SelectedUser, memberType: OrganizationMemberType) {
    await organizationsApi.addMember(organizationId, { userId: user.id, memberType });
    setFound(null);
    setMail('');
    await onAdded();
  }

  return (
    <AddMemberCard
      description="Buscá por el email exacto de una cuenta que ya exista en la plataforma."
      selected={found}
      roleLabel="Rol en la organización"
      roleLabels={MEMBER_TYPE_LABELS}
      defaultRole="MEMBER"
      submitLabel="Agregar a la organización"
      onAdd={handleAdd}
      source={
        <form onSubmit={handleSearch} className="flex flex-col gap-4" noValidate>
          {error && <Alert tone="error">{error}</Alert>}
          <div className="flex flex-wrap items-end gap-3">
            <div className="min-w-56 flex-1">
              <TextField
                label="Email"
                type="email"
                value={mail}
                onChange={(e) => setMail(e.target.value)}
                placeholder="persona@enerscope.org"
              />
            </div>
            <Button type="submit" variant="secondary" loading={searching}>
              Buscar
            </Button>
          </div>
        </form>
      }
    />
  );
}
