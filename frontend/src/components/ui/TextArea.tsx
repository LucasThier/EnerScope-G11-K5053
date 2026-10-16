import { useId, type TextareaHTMLAttributes } from 'react';

interface TextAreaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label: string;
  error?: string;
}

export function TextArea({ label, error, id, className = '', rows = 4, ...rest }: TextAreaProps) {
  const generatedId = useId();
  const textAreaId = id ?? generatedId;
  return (
    <div className="flex flex-col gap-2">
      <label htmlFor={textAreaId} className="text-sm font-medium text-ink-600">
        {label}
      </label>
      <textarea
        id={textAreaId}
        rows={rows}
        className={
          'resize-y rounded-lg border bg-surface px-3 py-2 text-sm text-ink-800 placeholder:text-ink-500 ' +
          'focus:outline-none focus:ring-2 focus:ring-focus/60 ' +
          (error ? 'border-danger-400 focus:border-danger-400 ' : 'border-ink-200 focus:border-focus ') +
          className
        }
        aria-invalid={error ? true : undefined}
        {...rest}
      />
      {error && <span className="text-xs text-danger-600">{error}</span>}
    </div>
  );
}
