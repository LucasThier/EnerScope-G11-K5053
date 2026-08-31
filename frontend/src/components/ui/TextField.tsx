import { useId, type InputHTMLAttributes } from 'react';

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
}

export function TextField({ label, error, id, className = '', ...rest }: TextFieldProps) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={inputId} className="text-sm font-medium text-ink-600">
        {label}
      </label>
      <input
        id={inputId}
        className={
          'rounded-lg border bg-white px-3 py-2.5 text-sm text-ink-800 placeholder:text-ink-300 ' +
          'focus:outline-none focus:ring-2 focus:ring-brand-400/40 ' +
          (error ? 'border-red-400 focus:border-red-400 ' : 'border-ink-200 focus:border-brand-500 ') +
          className
        }
        aria-invalid={error ? true : undefined}
        {...rest}
      />
      {error && <span className="text-xs text-red-600">{error}</span>}
    </div>
  );
}
