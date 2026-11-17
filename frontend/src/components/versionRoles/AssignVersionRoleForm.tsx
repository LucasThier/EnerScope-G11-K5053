import { useId, useState, type FormEvent } from 'react';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { SelectField } from '../ui/SelectField';
import { versionAccessApi } from '../../api/versionAccess';
import { getVersionRoleErrorMessage } from '../../api/errors';
import {
  VERSION_ROLE_LABELS,
  VERSION_ROLE_SUMMARIES,
  verticalLabel,
  versionRoleLabel,
} from '../projects/versionRoleLabels';
import {
  availableChoices,
  availableRoleTypes,
  fullName,
  grantRequests,
} from '../../utils/versionRoles';
import type { Vertical } from '../../types/diagram';
import type { ProjectMember } from '../../types/project';
import type { VersionMember, VersionRoleType } from '../../types/versionAccess';

interface AssignVersionRoleFormProps {
  versionId: string;
  candidates: ProjectMember[];
  members: VersionMember[];
  onAssigned: () => Promise<void>;
}

export function AssignVersionRoleForm({
  versionId,
  candidates,
  members,
  onAssigned,
}: AssignVersionRoleFormProps) {
  const personId = useId();
  const roleId = useId();
  const [userId, setUserId] = useState('');
  const [roleType, setRoleType] = useState<VersionRoleType | ''>('');
  const [verticals, setVerticals] = useState<Vertical[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const held = members.find((member) => member.userId === userId)?.roles ?? [];
  const choices = availableChoices(held);
  const roleTypes = availableRoleTypes(choices);
  const effectiveRole: VersionRoleType | '' = roleType !== '' && roleTypes.includes(roleType) ? roleType : '';
  const effectiveVerticals = verticals.filter((vertical) => choices.editorVerticals.includes(vertical));
  const person = candidates.find((candidate) => candidate.userId === userId) ?? null;
  const ready =
    person !== null &&
    effectiveRole !== '' &&
    (effectiveRole !== 'EDITOR' || effectiveVerticals.length > 0);

  function toggleVertical(vertical: Vertical, checked: boolean) {
    setVerticals((current) =>
      checked ? [...current.filter((item) => item !== vertical), vertical] : current.filter((item) => item !== vertical),
    );
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!ready) {
      return;
    }
    setError(null);
    setNotice(null);
    setSubmitting(true);
    const requests = grantRequests(person.userId, effectiveRole, effectiveVerticals);
    const granted: string[] = [];
    let failure: string | null = null;
    for (const request of requests) {
      try {
        await versionAccessApi.addRole(versionId, request);
        granted.push(versionRoleLabel(request.memberType, request.vertical ?? null));
      } catch (err) {
        failure = getVersionRoleErrorMessage(err, 'No se pudo asignar el rol');
        break;
      }
    }
    if (granted.length > 0) {
      await onAssigned();
    }
    if (failure === null) {
      setNotice(`Se asignó a ${fullName(person)}: ${granted.join(', ')}.`);
      setUserId('');
      setRoleType('');
      setVerticals([]);
    } else {
      setError(
        granted.length > 0
          ? `Se asignó ${granted.join(', ')}, pero no se pudo completar: ${failure}`
          : failure,
      );
    }
    setSubmitting(false);
  }

  if (candidates.length === 0) {
    return (
      <Alert tone="info">
        No hay integrantes a quienes asignar roles de versión. Los Administradores del proyecto ya
        administran todas las versiones; sumá más personas desde la pestaña Integrantes.
      </Alert>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
      <div>
        <h3 className="text-base font-semibold text-ink-800">Asignar un rol</h3>
        <p className="mt-1 text-sm text-ink-500">
          Al asignar el primer rol de versión, la persona deja de usar su rol en el proyecto en esta
          versión: valen solo sus roles de versión. Las versiones hijas que ya existen no se
          modifican; las que se creen después copian los roles de esta.
        </p>
      </div>

      {notice && <Alert tone="success">{notice}</Alert>}
      {error && <Alert tone="error">{error}</Alert>}

      <SelectField
        id={personId}
        label="Persona"
        value={userId}
        onChange={(event) => {
          setUserId(event.target.value);
          setNotice(null);
        }}
      >
        <option value="">Elegí una persona ({candidates.length})</option>
        {candidates.map((candidate) => (
          <option key={candidate.userId} value={candidate.userId}>
            {fullName(candidate)} — {candidate.userMail}
          </option>
        ))}
      </SelectField>

      {person !== null && roleTypes.length === 0 && (
        <Alert tone="info">Esa persona ya tiene todos los roles posibles en esta versión.</Alert>
      )}

      {person !== null && roleTypes.length > 0 && (
        <div className="grid gap-4 sm:grid-cols-2 sm:items-start">
          <SelectField
            id={roleId}
            label="Rol en la versión"
            value={effectiveRole}
            onChange={(event) => setRoleType(event.target.value as VersionRoleType | '')}
          >
            <option value="">Elegí un rol</option>
            {roleTypes.map((type) => (
              <option key={type} value={type}>
                {VERSION_ROLE_LABELS[type]}
              </option>
            ))}
          </SelectField>
          <dl className="flex flex-col gap-2 rounded-control border border-ink-100 bg-ink-50 px-3 py-2.5 text-xs text-ink-500">
            {roleTypes.map((type) => (
              <div key={type} className={type === effectiveRole ? 'text-ink-800' : undefined}>
                <dt className="font-semibold">{VERSION_ROLE_LABELS[type]}</dt>
                <dd>{VERSION_ROLE_SUMMARIES[type]}</dd>
              </div>
            ))}
          </dl>
        </div>
      )}

      {effectiveRole === 'EDITOR' && (
        <fieldset className="flex flex-col gap-2">
          <legend className="text-sm font-medium text-ink-700">Verticales que puede editar</legend>
          {choices.editorVerticals.map((vertical) => (
            <label key={vertical} className="flex items-center gap-2 text-sm text-ink-700">
              <input
                type="checkbox"
                checked={effectiveVerticals.includes(vertical)}
                onChange={(event) => toggleVertical(vertical, event.target.checked)}
                className="h-4 w-4 accent-brand-800"
              />
              {verticalLabel(vertical)}
            </label>
          ))}
        </fieldset>
      )}

      <div className="flex justify-end">
        <Button type="submit" disabled={!ready} loading={submitting}>
          Asignar rol
        </Button>
      </div>
    </form>
  );
}
