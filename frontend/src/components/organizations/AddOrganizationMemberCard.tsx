import {
  useEffect,
  useId,
  useMemo,
  useRef,
  useState,
  type FormEvent,
  type KeyboardEvent,
} from 'react';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { TextField } from '../ui/TextField';
import { AddMemberCard, type SelectedUser } from '../members/AddMemberCard';
import { organizationsApi } from '../../api/organizations';
import { usersApi } from '../../api/users';
import { getErrorMessage } from '../../api/errors';
import { MEMBER_TYPE_LABELS } from './memberTypeLabels';
import type { OrganizationMemberType, UserListItem, UserSearchResult } from '../../types/auth';

const MAX_SUGGESTIONS = 8;

interface AddOrganizationMemberCardProps {
  organizationId: string;
  onAdded: () => Promise<void>;
  variant?: 'card' | 'plain';
  lookup?: 'email' | 'list';
  excludeUserIds?: string[];
}

export function AddOrganizationMemberCard({
  organizationId,
  onAdded,
  variant = 'card',
  lookup = 'email',
  excludeUserIds = [],
}: AddOrganizationMemberCardProps) {
  const listId = useId();
  const [mail, setMail] = useState('');
  const [found, setFound] = useState<SelectedUser | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [searching, setSearching] = useState(false);
  const [candidates, setCandidates] = useState<UserListItem[]>([]);
  const [listFailed, setListFailed] = useState(false);
  const [query, setQuery] = useState('');
  const [open, setOpen] = useState(false);
  const [highlighted, setHighlighted] = useState(0);
  const [justAdded, setJustAdded] = useState(false);
  const sourceRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (lookup !== 'list') {
      return;
    }
    let cancelled = false;
    usersApi
      .list()
      .then((res) => {
        if (!cancelled) {
          setCandidates(res.data.data ?? []);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setListFailed(true);
          setError(getErrorMessage(err, 'No se pudo cargar el listado de cuentas'));
        }
      });
    return () => {
      cancelled = true;
    };
  }, [lookup]);

  useEffect(() => {
    if (justAdded && found === null) {
      sourceRef.current?.querySelector('input')?.focus();
      setJustAdded(false);
    }
  }, [found, justAdded]);

  const excluded = useMemo(() => new Set(excludeUserIds), [excludeUserIds]);

  const suggestions = useMemo(() => {
    const term = query.trim().toLowerCase();
    return candidates
      .filter((user) => user.active && !excluded.has(user.id))
      .filter((user) => {
        if (!term) {
          return true;
        }
        return (
          user.firstName.toLowerCase().includes(term) ||
          user.lastName.toLowerCase().includes(term) ||
          user.mail.toLowerCase().includes(term)
        );
      })
      .slice(0, MAX_SUGGESTIONS);
  }, [candidates, excluded, query]);

  const usesList = lookup === 'list' && !listFailed;

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
      const result: UserSearchResult | null = res.data.data ?? null;
      setFound(result);
    } catch (err) {
      setError(getErrorMessage(err, 'No se encontró ninguna cuenta con ese email'));
    } finally {
      setSearching(false);
    }
  }

  function selectCandidate(user: UserListItem) {
    setError(null);
    setFound({
      id: user.id,
      firstName: user.firstName,
      lastName: user.lastName,
      mail: user.mail,
    });
    setQuery('');
    setOpen(false);
    setHighlighted(0);
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setOpen(true);
      setHighlighted((current) => Math.min(current + 1, suggestions.length - 1));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setHighlighted((current) => Math.max(current - 1, 0));
    } else if (event.key === 'Enter' && open && suggestions[highlighted]) {
      event.preventDefault();
      selectCandidate(suggestions[highlighted]);
    } else if (event.key === 'Escape' && open) {
      event.preventDefault();
      setOpen(false);
    }
  }

  async function handleAdd(user: SelectedUser, memberType: OrganizationMemberType) {
    await organizationsApi.addMember(organizationId, { userId: user.id, memberType });
    setFound(null);
    setMail('');
    setQuery('');
    setJustAdded(true);
    await onAdded();
  }

  const emailSource = (
    <div ref={sourceRef}>
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
    </div>
  );

  const listSource = (
    <div ref={sourceRef} className="flex flex-col gap-4">
      {error && <Alert tone="error">{error}</Alert>}
      <div>
        <TextField
          label="Buscar una cuenta"
          type="search"
          autoComplete="off"
          value={query}
          placeholder="Nombre, apellido o email"
          onChange={(e) => {
            setQuery(e.target.value);
            setOpen(true);
            setHighlighted(0);
          }}
          onFocus={() => setOpen(true)}
          onBlur={() => setOpen(false)}
          onKeyDown={handleKeyDown}
          role="combobox"
          aria-expanded={open}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={
            open && suggestions[highlighted] ? `${listId}-${suggestions[highlighted].id}` : undefined
          }
        />
        {open && (
          <ul
            id={listId}
            role="listbox"
            aria-label="Cuentas que podés agregar"
            className={
              'mt-2 max-h-56 w-full overflow-auto rounded-control border border-ink-100 bg-raised shadow-raise'
            }
          >
            {suggestions.length === 0 ? (
              <li className="px-3 py-2 text-sm text-ink-500">
                {candidates.length === 0
                  ? 'Cargando cuentas…'
                  : 'Ninguna cuenta disponible coincide con la búsqueda.'}
              </li>
            ) : (
              suggestions.map((user, index) => (
                <li
                  key={user.id}
                  id={`${listId}-${user.id}`}
                  role="option"
                  aria-selected={index === highlighted}
                  onMouseDown={(event) => {
                    event.preventDefault();
                    selectCandidate(user);
                  }}
                  onMouseEnter={() => setHighlighted(index)}
                  className={`cursor-pointer px-3 py-2 text-sm ${
                    index === highlighted ? 'bg-ink-50 text-ink-800' : 'text-ink-700'
                  }`}
                >
                  <span className="font-medium">
                    {user.firstName} {user.lastName}
                  </span>
                  <span className="block text-xs text-ink-500">{user.mail}</span>
                </li>
              ))
            )}
          </ul>
        )}
      </div>
    </div>
  );

  return (
    <AddMemberCard
      variant={variant}
      description={
        usesList
          ? 'Elegí una cuenta que ya exista en la plataforma. No aparecen las personas que ya integran la organización.'
          : 'Buscá por el email exacto de una cuenta que ya exista en la plataforma.'
      }
      selected={found}
      roleLabel="Rol en la organización"
      roleLabels={MEMBER_TYPE_LABELS}
      defaultRole="MEMBER"
      submitLabel="Agregar a la organización"
      onAdd={handleAdd}
      source={usesList ? listSource : emailSource}
    />
  );
}
