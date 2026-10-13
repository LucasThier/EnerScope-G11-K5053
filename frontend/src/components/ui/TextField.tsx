import { useId, type InputHTMLAttributes, type ReactNode } from 'react';

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
  endAdornment?: ReactNode;
}

export function TextField({
  label,
  error,
  id,
  className = '',
  endAdornment,
  ...rest
}: TextFieldProps) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  return (
    <div className="flex flex-col gap-2">
      <label htmlFor={inputId} className="text-sm font-medium text-ink-600">
        {label}
      </label>
      <div className="relative">
        <input
          id={inputId}
          className={
            'w-full rounded-lg border bg-surface px-3 py-2 text-sm text-ink-800 placeholder:text-ink-500 ' +
            'focus:outline-none focus:ring-2 focus:ring-focus/60 ' +
            (endAdornment ? 'pr-10 ' : '') +
            (error ? 'border-danger-400 focus:border-danger-400 ' : 'border-ink-400 focus:border-focus ') +
            className
          }
          aria-invalid={error ? true : undefined}
          {...rest}
        />
        {endAdornment && (
          <span className="absolute inset-y-0 right-1 flex items-center">{endAdornment}</span>
        )}
      </div>
      {error && <span className="text-xs text-danger-600">{error}</span>}
    </div>
  );
}
