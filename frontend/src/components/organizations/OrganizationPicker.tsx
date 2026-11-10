import { useState } from 'react';
import type { OrganizationSummary } from '../../types/auth';
import { getErrorMessage } from '../../api/errors';
import { Button } from '../ui/Button';
import { Field } from '../ui/Field';
import { useField } from '../ui/useField';
import { controlField } from '../ui/controlClasses';

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
  emptyLabel = 'Sin organización',
  className = '',
}: OrganizationPickerProps) {
  const [creating, setCreating] = useState(false);
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const { fieldId, hintId, errorId, controlProps } = useField(undefined, {
    error: error ?? undefined,
  });

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
      setError(getErrorMessage(err, 'No se pudo crear la organización'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Field
      label="Organización (opcional)"
      error={error ?? undefined}
      fieldId={fieldId}
      hintId={hintId}
      errorId={errorId}
      className={className}
    >
      {creating ? (
        // No nested <form> here: this picker is rendered inside the create-user
        // form, and nested forms are invalid HTML (the inner submit would submit
        // the outer form and reload the page). Create runs via an onClick button
        // and an explicit Enter handler instead.
        <div className="flex gap-2">
          <input
            {...controlProps}
            autoFocus
            value={name}
            onChange={(e) => setName(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault();
                void handleCreate();
              }
            }}
            placeholder="Nombre de la nueva organización"
            className={`${controlField} flex-1`}
          />
          <Button type="button" loading={submitting} onClick={() => void handleCreate()} className="shrink-0">
            Crear
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
            Cancelar
          </Button>
        </div>
      ) : (
        <div className="flex gap-2">
          <select
            {...controlProps}
            value={value ?? ''}
            disabled={loading}
            onChange={(e) => onChange(e.target.value === '' ? null : e.target.value)}
            className={`${controlField} flex-1`}
          >
            <option value="">{loading ? 'Cargando organizaciones…' : emptyLabel}</option>
            {organizations.map((org) => (
              <option key={org.id} value={org.id}>
                {org.name}
              </option>
            ))}
          </select>
          <Button type="button" variant="ghost" onClick={() => setCreating(true)} className="shrink-0">
            + Nueva
          </Button>
        </div>
      )}
    </Field>
  );
}
