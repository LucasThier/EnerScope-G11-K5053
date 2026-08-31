import { useId, useState } from 'react';
import type { OrganizationSummary } from '../../types/auth';
import { getErrorMessage } from '../../api/errors';
import { Button } from '../ui/Button';

interface OrganizationPickerProps {
  organizations: OrganizationSummary[];
  loading?: boolean;
  /** Selected organization id, or null for "no organization". */
  value: string | null;
  onChange: (id: string | null) => void;
  onCreate: (name: string) => Promise<OrganizationSummary>;
  /** Label for the empty option (e.g. "No organization (platform user)"). */
  emptyLabel?: string;
  className?: string;
}

const selectClasses =
  'rounded-lg border border-ink-200 bg-white px-3 py-2.5 text-sm text-ink-800 ' +
  'focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-400/40';

/**
 * Chooses an organization from the existing ones, with an inline "create a new
 * organization" affordance that selects the new org once created.
 */
export function OrganizationPicker({
  organizations,
  loading = false,
  value,
  onChange,
  onCreate,
  emptyLabel = 'No organization',
  className = '',
}: OrganizationPickerProps) {
  const selectId = useId();
  const [creating, setCreating] = useState(false);
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleCreate() {
    if (!name.trim() || submitting) return;
    setError(null);
    setSubmitting(true);
    try {
      const created = await onCreate(name.trim());
      onChange(created.id);
      setName('');
      setCreating(false);
    } catch (err) {
      setError(getErrorMessage(err, 'Could not create the organization'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className={`flex flex-col gap-1.5 ${className}`}>
      <label htmlFor={selectId} className="text-sm font-medium text-ink-600">
        Organization <span className="font-normal text-ink-400">(optional)</span>
      </label>

      {creating ? (
        // No nested <form> here: this picker is rendered inside the create-user
        // form, and nested forms are invalid HTML (the inner submit would submit
        // the outer form and reload the page). Create runs via an onClick button
        // and an explicit Enter handler instead.
        <div className="flex flex-col gap-2">
          <div className="flex gap-2">
            <input
              autoFocus
              value={name}
              onChange={(e) => setName(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault();
                  void handleCreate();
                }
              }}
              placeholder="New organization name"
              className={`flex-1 ${selectClasses}`}
            />
            <Button type="button" loading={submitting} onClick={() => void handleCreate()} className="shrink-0">
              Create
            </Button>
            <Button
              type="button"
              variant="ghost"
              onClick={() => {
                setCreating(false);
                setError(null);
                setName('');
              }}
            >
              Cancel
            </Button>
          </div>
          {error && <span className="text-xs text-red-600">{error}</span>}
        </div>
      ) : (
        <div className="flex gap-2">
          <select
            id={selectId}
            value={value ?? ''}
            disabled={loading}
            onChange={(e) => onChange(e.target.value === '' ? null : e.target.value)}
            className={`flex-1 ${selectClasses} disabled:opacity-60`}
          >
            <option value="">{loading ? 'Loading organizations…' : emptyLabel}</option>
            {organizations.map((org) => (
              <option key={org.id} value={org.id}>
                {org.name}
              </option>
            ))}
          </select>
          <Button type="button" variant="ghost" onClick={() => setCreating(true)} className="shrink-0">
            + New
          </Button>
        </div>
      )}
    </div>
  );
}
