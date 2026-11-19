import { useState } from 'react';
import type { Finding } from '../../types/comparison';
import { findingTone, type Tone } from '../../utils/comparison';
import { Card } from '../ui/Card';
import { AlertIcon, CheckIcon, InfoIcon } from '../ui/icons';

const SHOWN = 5;

const ICON: Record<Tone, { icon: typeof InfoIcon; box: string; label: string }> = {
  good: { icon: CheckIcon, box: 'bg-brand-50 text-brand-800', label: 'A favor' },
  bad: { icon: AlertIcon, box: 'bg-danger-50 text-danger-700', label: 'Atención' },
  neutral: { icon: InfoIcon, box: 'bg-warn-50 text-warn-800', label: 'Diferencia' },
  muted: { icon: InfoIcon, box: 'bg-ink-50 text-ink-600', label: 'Dato' },
};

/** The relevant differences in plain words, most important first. */
export function KeyFindings({ findings }: { findings: Finding[] }) {
  const [all, setAll] = useState(false);
  const shown = all ? findings : findings.slice(0, SHOWN);
  return <Card className="flex flex-col gap-4">
    <div>
      <h2 className="text-lg font-semibold text-ink-800">Lo más importante</h2>
      <p className="mt-1 text-sm text-ink-500">Las diferencias que más pesan entre los escenarios.</p>
    </div>
    {!findings.length ? <p className="text-sm text-ink-600">Los escenarios dan resultados muy parecidos: no hay diferencias relevantes.</p> : <>
      <ul className="flex flex-col gap-3">
        {shown.map((finding, index) => {
          const tone = ICON[findingTone(finding.type)];
          const Icon = tone.icon;
          return <li key={index} className="flex items-start gap-3">
            <span className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full ${tone.box}`}>
              <Icon className="h-4 w-4" /><span className="sr-only">{tone.label}:</span>
            </span>
            <span className="pt-0.5 text-sm text-ink-700">{finding.message}</span>
          </li>;
        })}
      </ul>
      {findings.length > SHOWN && <button type="button" onClick={() => setAll(value => !value)}
        className="self-start text-sm font-semibold text-brand-800 hover:underline">
        {all ? 'Ver menos' : `Ver las ${findings.length} diferencias`}
      </button>}
    </>}
  </Card>;
}
