import { useId, useState, type ReactNode } from 'react';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { Card } from '../ui/Card';
import { controlClasses } from '../ui/controlClasses';
import { getErrorMessage } from '../../api/errors';

export interface SelectedUser {
  id: string;
  firstName: string;
  lastName: string;
  mail: string;
}

interface AddMemberCardProps<T extends string> {
  description: string;
  source: ReactNode;
  selected: SelectedUser | null;
  roleLabel: string;
  roleLabels: Record<T, string>;
  defaultRole: T;
  roleHint?: (role: T) => string | null;
  submitLabel: string;
  onAdd: (user: SelectedUser, role: T) => Promise<void>;
}

export function AddMemberCard<T extends string>({
  description,
  source,
  selected,
  roleLabel,
  roleLabels,
  defaultRole,
  roleHint,
  submitLabel,
  onAdd,
}: AddMemberCardProps<T>) {
  const roleId = useId();
  const [memberType, setMemberType] = useState<T>(defaultRole);
  const [error, setError] = useState<{ userId: string; message: string } | null>(null);
  const [added, setAdded] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);

  async function handleAdd() {
    if (!selected) {
      return;
    }
    setError(null);
    setAdded(null);
    setAdding(true);
    try {
      await onAdd(selected, memberType);
      setAdded(`Se agregó a ${selected.firstName} ${selected.lastName}.`);
      setMemberType(defaultRole);
    } catch (err) {
      setError({
        userId: selected.id,
        message: getErrorMessage(err, 'No se pudo agregar al integrante'),
      });
    } finally {
      setAdding(false);
    }
  }

  const hint = roleHint?.(memberType) ?? null;

  return (
    <Card className="mt-6 max-w-xl">
      <h2 className="text-lg font-semibold text-ink-800">Agregar integrante</h2>
      <p className="mt-1 text-sm text-ink-500">{description}</p>

      <div className="mt-4 flex flex-col gap-4">
        {added && !selected && <Alert tone="success">{added}</Alert>}
        {source}
      </div>

      {selected && (
        <div className="mt-4 flex flex-col gap-4 border-t border-ink-100 pt-4">
          {error?.userId === selected.id && <Alert tone="error">{error.message}</Alert>}

          <p className="text-sm text-ink-700">
            <span className="font-semibold text-ink-800">
              {selected.firstName} {selected.lastName}
            </span>{' '}
            <span className="text-ink-500">({selected.mail})</span>
          </p>

          <div className="flex flex-col gap-2">
            <label htmlFor={roleId} className="text-sm font-medium text-ink-600">
              {roleLabel}
            </label>
            <select
              id={roleId}
              value={memberType}
              onChange={(e) => setMemberType(e.target.value as T)}
              className={controlClasses}
            >
              {(Object.keys(roleLabels) as T[]).map((type) => (
                <option key={type} value={type}>
                  {roleLabels[type]}
                </option>
              ))}
            </select>
            {hint && <p className="text-xs text-ink-500">{hint}</p>}
          </div>

          <div className="flex justify-end">
            <Button type="button" onClick={handleAdd} loading={adding}>
              {submitLabel}
            </Button>
          </div>
        </div>
      )}
    </Card>
  );
}
