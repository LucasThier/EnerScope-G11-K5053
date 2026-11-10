import { useId, type InputHTMLAttributes } from 'react';
import { controlCompact } from './controlClasses';
import { SearchIcon } from './icons';

interface SearchFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label: string;
  wrapperClassName?: string;
}

export function SearchField({
  label,
  id,
  className = '',
  wrapperClassName = '',
  ...rest
}: SearchFieldProps) {
  const generated = useId();
  const inputId = id ?? generated;
  return (
    <div className={`relative ${wrapperClassName}`}>
      <SearchIcon className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-400" />
      <label htmlFor={inputId} className="sr-only">
        {label}
      </label>
      <input id={inputId} type="search" className={`${controlCompact} w-full pl-9 ${className}`} {...rest} />
    </div>
  );
}
