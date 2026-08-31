import type { ReactNode } from 'react';

type Tone = 'error' | 'success' | 'info';

interface AlertProps {
  tone?: Tone;
  children: ReactNode;
  className?: string;
}

const tones: Record<Tone, string> = {
  error: 'bg-red-50 text-red-700 border-red-200',
  success: 'bg-brand-50 text-brand-800 border-brand-200',
  info: 'bg-ink-50 text-ink-700 border-ink-200',
};

export function Alert({ tone = 'info', children, className = '' }: AlertProps) {
  return (
    <div role="alert" className={`rounded-lg border px-3 py-2.5 text-sm ${tones[tone]} ${className}`}>
      {children}
    </div>
  );
}
