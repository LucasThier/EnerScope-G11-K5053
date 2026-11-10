import type { ReactNode } from 'react';
import { AlertIcon, CheckIcon, InfoIcon } from './icons';

type Tone = 'info' | 'success' | 'warning' | 'error';

interface AlertProps {
  tone?: Tone;
  live?: boolean;
  children: ReactNode;
  className?: string;
}

const tones: Record<Tone, { box: string; role: 'status' | 'alert'; icon: typeof InfoIcon }> = {
  info: { box: 'border-ink-200 bg-ink-50 text-ink-700', role: 'status', icon: InfoIcon },
  success: { box: 'border-brand-200 bg-brand-50 text-brand-800', role: 'status', icon: CheckIcon },
  warning: { box: 'border-warn-200 bg-warn-50 text-warn-800', role: 'alert', icon: AlertIcon },
  error: { box: 'border-danger-200 bg-danger-50 text-danger-700', role: 'alert', icon: AlertIcon },
};

export function Alert({ tone = 'info', live = true, children, className = '' }: AlertProps) {
  const { box, role, icon: Icon } = tones[tone];
  return (
    <div
      role={live ? role : undefined}
      className={`flex items-start gap-2 rounded-control border px-3 py-2 text-sm ${box} ${className}`}
    >
      <Icon className="mt-0.5 h-4 w-4 shrink-0" />
      <div className="min-w-0 flex-1">{children}</div>
    </div>
  );
}
