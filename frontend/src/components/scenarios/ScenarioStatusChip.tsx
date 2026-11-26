import type { StatusTone } from '../../utils/scenarioRules';

/** The ring, fill and ink of each tone; the one place the scenario status colours are decided. */
const STATUS_TONES: Record<StatusTone, string> = {
  good: 'bg-brand-50 text-brand-800 ring-brand-200',
  warn: 'bg-warn-50 text-warn-800 ring-warn-200',
  info: 'bg-info-50 text-info-800 ring-info-200',
  neutral: 'bg-ink-50 text-ink-700 ring-ink-200',
};

interface ScenarioStatusChipProps {
  label: string;
  tone: StatusTone;
  /** One-line meaning: the tooltip, and part of the accessible name. */
  hint?: string;
  /** A coloured dot before the label (Inicio); the version lists keep the plain pill. */
  dot?: boolean;
  className?: string;
}

/**
 * A scenario's status (or its draft flag) as a pill. There is no tooltip component in the app, so the meaning rides
 * on `title` plus an `aria-label` that reads label and meaning together.
 */
export function ScenarioStatusChip({ label, tone, hint, dot = false, className = '' }: ScenarioStatusChipProps) {
  return (
    <span
      role={hint ? 'img' : undefined}
      aria-label={hint ? `${label}. ${hint}` : undefined}
      title={hint}
      className={
        `${dot ? 'inline-flex items-center gap-1.5' : 'inline-block'} whitespace-nowrap rounded-full px-2.5 py-0.5 ` +
        `text-xs font-semibold ring-1 ring-inset ${STATUS_TONES[tone]} ${className}`
      }
    >
      {dot && <span aria-hidden="true" className="h-1.5 w-1.5 shrink-0 rounded-full bg-current" />}
      {label}
    </span>
  );
}
