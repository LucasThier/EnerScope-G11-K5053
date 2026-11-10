import type { InputHTMLAttributes, ReactNode } from 'react';
import { Field } from './Field';
import { useField, type FieldMessages } from './useField';
import { controlField } from './controlClasses';

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement>, FieldMessages {
  label: string;
  endAdornment?: ReactNode;
}

export function TextField({
  label,
  hint,
  error,
  id,
  className = '',
  endAdornment,
  ...rest
}: TextFieldProps) {
  const { fieldId, hintId, errorId, controlProps } = useField(id, { hint, error });
  return (
    <Field label={label} hint={hint} error={error} fieldId={fieldId} hintId={hintId} errorId={errorId}>
      <div className="relative">
        <input
          {...controlProps}
          className={`${controlField} w-full ${endAdornment ? 'pr-12' : ''} ${className}`}
          {...rest}
        />
        {endAdornment && (
          <span className="absolute inset-y-0 right-2 flex items-center">{endAdornment}</span>
        )}
      </div>
    </Field>
  );
}
