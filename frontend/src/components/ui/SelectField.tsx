import type { ReactNode, SelectHTMLAttributes } from 'react';
import { Field } from './Field';
import { useField, type FieldMessages } from './useField';
import { controlCompact, controlField } from './controlClasses';

interface SelectFieldProps
  extends Omit<SelectHTMLAttributes<HTMLSelectElement>, 'size'>,
    FieldMessages {
  label: string;
  density?: 'md' | 'compact';
  children: ReactNode;
}

export function SelectField({
  label,
  hint,
  error,
  id,
  className = '',
  density = 'md',
  children,
  ...rest
}: SelectFieldProps) {
  const { fieldId, hintId, errorId, controlProps } = useField(id, { hint, error });
  const box = density === 'compact' ? controlCompact : controlField;
  return (
    <Field label={label} hint={hint} error={error} fieldId={fieldId} hintId={hintId} errorId={errorId}>
      <select {...controlProps} className={`${box} w-full ${className}`} {...rest}>
        {children}
      </select>
    </Field>
  );
}
