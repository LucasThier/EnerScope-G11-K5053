import { useId } from 'react';
import { SearchField } from '../ui/SearchField';
import { versionBadge, type VersionRow } from '../../utils/versionRoles';

interface VersionPickerProps {
  rows: VersionRow[];
  selectedId: string | null;
  totalCount: number;
  query: string;
  onlyManageable: boolean;
  canFilterManageable: boolean;
  onSelect: (versionId: string) => void;
  onQueryChange: (value: string) => void;
  onOnlyManageableChange: (value: boolean) => void;
}

const INDENT_PX = 14;

export function VersionPicker({
  rows,
  selectedId,
  totalCount,
  query,
  onlyManageable,
  canFilterManageable,
  onSelect,
  onQueryChange,
  onOnlyManageableChange,
}: VersionPickerProps) {
  const searchId = useId();
  const toggleId = useId();

  return (
    <div className="flex flex-col gap-3">
      <SearchField
        id={searchId}
        label="Buscar versión"
        placeholder="Buscar versión"
        value={query}
        onChange={(event) => onQueryChange(event.target.value)}
      />
      {canFilterManageable && (
        <div className="flex items-center gap-2">
          <input
            id={toggleId}
            type="checkbox"
            checked={onlyManageable}
            onChange={(event) => onOnlyManageableChange(event.target.checked)}
            className="h-4 w-4 accent-brand-800"
          />
          <label htmlFor={toggleId} className="text-sm text-ink-700">
            Solo las que administro
          </label>
        </div>
      )}

      <p className="text-xs text-ink-500">
        {rows.length === totalCount
          ? `${totalCount} ${totalCount === 1 ? 'versión' : 'versiones'}`
          : `${rows.length} de ${totalCount} versiones`}
      </p>

      {rows.length === 0 ? (
        <p className="rounded-control border border-ink-100 px-3 py-3 text-sm text-ink-500">
          Ninguna versión coincide con el filtro.
        </p>
      ) : (
        <ul className="max-h-96 overflow-y-auto rounded-control border border-ink-100">
          {rows.map((row) => {
            const selected = row.id === selectedId;
            const badge = versionBadge(row.access);
            return (
              <li key={row.id} className="border-b border-ink-100 last:border-b-0">
                <button
                  type="button"
                  onClick={() => onSelect(row.id)}
                  aria-current={selected ? 'true' : undefined}
                  style={{ paddingLeft: `${12 + row.depth * INDENT_PX}px` }}
                  className={
                    'flex w-full items-center justify-between gap-2 py-2 pr-3 text-left text-sm ' +
                    'focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-focus ' +
                    (selected
                      ? 'bg-brand-50 font-semibold text-brand-900'
                      : 'text-ink-700 hover:bg-ink-50')
                  }
                >
                  <span className="min-w-0 truncate">{row.name}</span>
                  {badge && (
                    <span className="shrink-0 rounded-full bg-ink-100 px-2 py-0.5 text-xs font-semibold text-ink-600">
                      {badge}
                    </span>
                  )}
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
