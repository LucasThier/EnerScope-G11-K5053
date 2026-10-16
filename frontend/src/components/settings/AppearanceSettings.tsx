import { useId } from 'react';
import { useTheme, type ThemePreference } from '../../hooks/useTheme';

interface ThemeOption {
  value: ThemePreference;
  label: string;
  description: string;
  sample: 'light' | 'dark' | 'system';
}

const THEME_OPTIONS: ThemeOption[] = [
  {
    value: 'light',
    label: 'Claro',
    description: 'La paleta original, pensada para ambientes luminosos.',
    sample: 'light',
  },
  {
    value: 'dark',
    label: 'Oscuro',
    description: 'Superficies oscuras con la misma jerarquía, para sesiones largas.',
    sample: 'dark',
  },
  {
    value: 'system',
    label: 'Según el sistema',
    description: 'Sigue la preferencia de apariencia de tu sistema operativo.',
    sample: 'system',
  },
];

const RESOLVED_LABELS = { light: 'claro', dark: 'oscuro' };

/**
 * The appearance preference: a theme radiogroup backed by `useTheme`. Reused
 * wherever the user can tweak how EnerScope looks, with no save step of its
 * own since the preference applies as soon as it is picked.
 */
export function AppearanceSettings() {
  const { preference, resolved, setPreference } = useTheme();
  const groupName = useId();

  return (
    <fieldset className="mt-4">
      <legend className="text-sm font-medium text-ink-600">Tema</legend>
      <p className="mt-1 text-sm text-ink-500">
        Elegí cómo se ve EnerScope. La preferencia queda guardada en este navegador.
      </p>

      <div className="mt-4 flex flex-col gap-3">
        {THEME_OPTIONS.map((option) => {
          const isSelected = preference === option.value;
          return (
            <label
              key={option.value}
              className={
                'flex cursor-pointer items-center gap-4 rounded-xl border px-4 py-3 transition-colors ' +
                (isSelected
                  ? 'border-brand-800 bg-brand-50'
                  : 'border-ink-200 hover:bg-ink-50')
              }
            >
              <input
                type="radio"
                name={groupName}
                value={option.value}
                checked={isSelected}
                onChange={() => setPreference(option.value)}
                className="h-4 w-4 shrink-0 accent-brand-800 focus:outline-none focus-visible:ring-2 focus-visible:ring-focus"
              />
              <span className="min-w-0 flex-1">
                <span className="block text-sm font-semibold text-ink-800">
                  {option.label}
                </span>
                <span className="mt-0.5 block text-sm text-ink-500">
                  {option.description}
                </span>
                {option.value === 'system' && isSelected && (
                  <span className="mt-1 block text-xs font-medium uppercase tracking-wide text-ink-500">
                    Ahora: {RESOLVED_LABELS[resolved]}
                  </span>
                )}
              </span>
              <ThemeSample kind={option.sample} />
            </label>
          );
        })}
      </div>
    </fieldset>
  );
}

function ThemeSample({ kind }: { kind: 'light' | 'dark' | 'system' }) {
  if (kind === 'system') {
    return (
      <span
        aria-hidden="true"
        className="flex h-12 w-20 shrink-0 overflow-hidden rounded-lg border border-ink-200"
      >
        <span className="flex-1 bg-sample-light-page p-1.5">
          <span className="block h-3 rounded-sm bg-sample-light-surface" />
          <span className="mt-1.5 block h-1.5 w-1.5 rounded-full bg-sample-light-accent" />
        </span>
        <span className="flex-1 bg-sample-dark-page p-1.5">
          <span className="block h-3 rounded-sm bg-sample-dark-surface" />
          <span className="mt-1.5 block h-1.5 w-1.5 rounded-full bg-sample-dark-accent" />
        </span>
      </span>
    );
  }

  const page = kind === 'light' ? 'bg-sample-light-page' : 'bg-sample-dark-page';
  const surface = kind === 'light' ? 'bg-sample-light-surface' : 'bg-sample-dark-surface';
  const accent = kind === 'light' ? 'bg-sample-light-accent' : 'bg-sample-dark-accent';

  return (
    <span
      aria-hidden="true"
      className={`h-12 w-20 shrink-0 overflow-hidden rounded-lg border border-ink-200 p-2 ${page}`}
    >
      <span className={`block h-4 rounded-sm ${surface}`} />
      <span className={`mt-2 block h-2 w-8 rounded-full ${accent}`} />
    </span>
  );
}
