import { useId, useState, type FormEvent } from 'react';
import { useAuth } from '../../hooks/useAuth';
import { useOrganizations } from '../../hooks/useOrganizations';
import { getErrorMessage } from '../../api/errors';
import { Button } from '../ui/Button';
import { TextField } from '../ui/TextField';
import { Alert } from '../ui/Alert';
import { OrganizationPicker } from '../organizations/OrganizationPicker';
import type { PlatformRole } from '../../types/auth';

interface RegisterResult {
  mail: string;
  organizationId: string | null;
}

interface RegisterFormProps {
  /** Show an ADMIN/USER selector for platform accounts (only when no org chosen). */
  allowRoleSelection?: boolean;
  /**
   * When set, the form always registers into this organization and hides the
   * picker (e.g. an organization-owner context). When omitted, the picker lets
   * the user optionally choose/create an organization.
   */
  lockedOrganizationId?: string;
  submitLabel?: string;
  onSuccess?: (result: RegisterResult) => void;
  className?: string;
}

const EMPTY = { firstName: '', lastName: '', mail: '', password: '' };

/**
 * Single "create a user" form. The organization is an optional selection (with
 * an inline create): pick one and the user is registered into that organization
 * as a member; leave it empty and a platform-level account is created instead.
 */
export function RegisterForm({
  allowRoleSelection = false,
  lockedOrganizationId,
  submitLabel = 'Create user',
  onSuccess,
  className = '',
}: RegisterFormProps) {
  const { register, registerInOrganization } = useAuth();
  const { organizations, loading, createOrganization } = useOrganizations();
  const roleSelectId = useId();

  const [fields, setFields] = useState(EMPTY);
  const [selectedOrgId, setSelectedOrgId] = useState<string | null>(null);
  const [role, setRole] = useState<PlatformRole>('USER');
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const organizationId = lockedOrganizationId ?? selectedOrgId;

  function update(key: keyof typeof EMPTY, value: string) {
    setFields((prev) => ({ ...prev, [key]: value }));
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSuccess(null);
    setSubmitting(true);
    try {
      if (organizationId) {
        await registerInOrganization(organizationId, fields);
      } else {
        await register({ ...fields, role: allowRoleSelection ? role : undefined });
      }
      const where = organizationId ? 'into the organization' : 'as a platform user';
      setSuccess(`${fields.mail} was registered ${where}.`);
      onSuccess?.({ mail: fields.mail, organizationId });
      setFields(EMPTY);
      setRole('USER');
    } catch (err) {
      setError(getErrorMessage(err, 'Could not register the user'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className={`flex flex-col gap-4 ${className}`} noValidate>
      {error && <Alert tone="error">{error}</Alert>}
      {success && <Alert tone="success">{success}</Alert>}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <TextField
          label="First name"
          autoComplete="given-name"
          required
          value={fields.firstName}
          onChange={(e) => update('firstName', e.target.value)}
        />
        <TextField
          label="Last name"
          autoComplete="family-name"
          required
          value={fields.lastName}
          onChange={(e) => update('lastName', e.target.value)}
        />
      </div>
      <TextField
        label="Email"
        type="email"
        autoComplete="off"
        required
        value={fields.mail}
        onChange={(e) => update('mail', e.target.value)}
        placeholder="new.user@enerscope.org"
      />
      <TextField
        label="Temporary password"
        type="password"
        autoComplete="new-password"
        required
        minLength={8}
        value={fields.password}
        onChange={(e) => update('password', e.target.value)}
        placeholder="At least 8 characters"
      />

      {!lockedOrganizationId && (
        <OrganizationPicker
          organizations={organizations}
          loading={loading}
          value={selectedOrgId}
          onChange={setSelectedOrgId}
          onCreate={createOrganization}
          emptyLabel="No organization (platform user)"
        />
      )}

      {allowRoleSelection && !organizationId && (
        <div className="flex flex-col gap-1.5">
          <label htmlFor={roleSelectId} className="text-sm font-medium text-ink-600">
            Platform role
          </label>
          <select
            id={roleSelectId}
            value={role}
            onChange={(e) => setRole(e.target.value as PlatformRole)}
            className="rounded-lg border border-ink-200 bg-white px-3 py-2.5 text-sm text-ink-800 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-400/40"
          >
            <option value="USER">User</option>
            <option value="ADMIN">Admin</option>
          </select>
        </div>
      )}

      <Button type="submit" loading={submitting} className="mt-1 w-full">
        {submitLabel}
      </Button>
    </form>
  );
}
