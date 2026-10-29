import { useEffect, useId, useState } from 'react';
import { Alert } from '../ui/Alert';
import { SearchField } from '../ui/SearchField';
import { SelectField } from '../ui/SelectField';
import { AddMemberCard, type SelectedUser } from '../members/AddMemberCard';
import { projectsApi } from '../../api/projects';
import { getErrorMessage } from '../../api/errors';
import { EDITOR_LIMITS, PROJECT_MEMBER_TYPE_LABELS } from './projectMemberTypeLabels';
import type { ProjectMemberCandidate, ProjectMemberType } from '../../types/project';

interface AddProjectMemberCardProps {
  projectId: string;
  onAdded: () => Promise<void>;
}

const SEARCH_DELAY_MS = 300;

export function AddProjectMemberCard({ projectId, onAdded }: AddProjectMemberCardProps) {
  const searchId = useId();
  const candidateId = useId();
  const [query, setQuery] = useState('');
  const [candidates, setCandidates] = useState<ProjectMemberCandidate[]>([]);
  const [selectedId, setSelectedId] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    const timer = window.setTimeout(() => {
      setLoading(true);
      setError(null);
      projectsApi
        .memberCandidates(projectId, query.trim() || undefined)
        .then((res) => {
          if (!cancelled) {
            setCandidates(res.data.data ?? []);
          }
        })
        .catch((err) => {
          if (!cancelled) {
            setCandidates([]);
            setError(getErrorMessage(err, 'No se pudieron cargar las personas disponibles'));
          }
        })
        .finally(() => {
          if (!cancelled) {
            setLoading(false);
          }
        });
    }, SEARCH_DELAY_MS);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [projectId, query, reloadKey]);

  const selected: SelectedUser | null =
    candidates.find((candidate) => candidate.id === selectedId) ?? null;

  async function handleAdd(user: SelectedUser, memberType: ProjectMemberType) {
    await projectsApi.addMember(projectId, { userId: user.id, memberType });
    setSelectedId('');
    setQuery('');
    setReloadKey((key) => key + 1);
    await onAdded();
  }

  const searching = query.trim().length > 0;

  return (
    <AddMemberCard
      description="Elegí a una persona de la organización del proyecto que todavía no lo integre."
      selected={selected}
      roleLabel="Rol en el proyecto"
      roleLabels={PROJECT_MEMBER_TYPE_LABELS}
      defaultRole="EDITOR"
      roleHint={(role) => (role === 'EDITOR' ? EDITOR_LIMITS : null)}
      submitLabel="Agregar al proyecto"
      onAdd={handleAdd}
      source={
        <>
          {error && <Alert tone="error">{error}</Alert>}

          <SearchField
            id={searchId}
            label="Buscar por nombre, apellido o email"
            value={query}
            onChange={(e) => {
              setQuery(e.target.value);
              setSelectedId('');
            }}
            placeholder="Buscar por nombre, apellido o email"
          />

          {!loading && !error && candidates.length === 0 ? (
            <Alert tone="info">
              {searching
                ? 'Ninguna persona disponible coincide con la búsqueda.'
                : 'Todas las personas de la organización ya integran este proyecto.'}
            </Alert>
          ) : (
            <SelectField
              id={candidateId}
              label="Persona"
              value={selectedId}
              onChange={(e) => setSelectedId(e.target.value)}
              disabled={loading || error !== null}
            >
              <option value="">
                {loading ? 'Cargando personas…' : `Elegí una persona (${candidates.length})`}
              </option>
              {candidates.map((candidate) => (
                <option key={candidate.id} value={candidate.id}>
                  {candidate.firstName} {candidate.lastName} — {candidate.mail}
                </option>
              ))}
            </SelectField>
          )}
        </>
      }
    />
  );
}
