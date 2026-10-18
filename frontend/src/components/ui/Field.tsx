import type { ReactNode } from 'react';
import { AlertIcon } from './icons';
import type { FieldMessages, FieldWiring } from './useField';

interface FieldProps extends FieldMessages, Omit<FieldWiring, 'controlProps'> {
  label: string;
  className?: string;
  children: ReactNode;
}

export function Field({
  label,
  hint,
  error,
  fieldId,
  hintId,
  errorId,
  className = '',
  children,
}: FieldProps) {
  return (
    <div className={`flex flex-col gap-2 ${className}`}>
      <label htmlFor={fieldId} className="text-sm font-medium text-ink-600">
        {label}
      </label>
      {children}
      {hint && (
        <p id={hintId} className="text-xs text-ink-500">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="flex items-start gap-1.5 text-xs font-medium text-danger-600">
          <AlertIcon className="mt-px h-3.5 w-3.5 shrink-0" />
          <span>{error}</span>
        </p>
      )}
    </div>
  );
}
