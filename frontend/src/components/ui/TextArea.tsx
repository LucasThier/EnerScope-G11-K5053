import type { TextareaHTMLAttributes } from 'react';
import { Field } from './Field';
import { useField, type FieldMessages } from './useField';
import { controlTextArea } from './controlClasses';

interface TextAreaProps extends TextareaHTMLAttributes<HTMLTextAreaElement>, FieldMessages {
  label: string;
}

export function TextArea({
  label,
  hint,
  error,
  id,
  className = '',
  rows = 4,
  ...rest
}: TextAreaProps) {
  const { fieldId, hintId, errorId, controlProps } = useField(id, { hint, error });
  return (
    <Field label={label} hint={hint} error={error} fieldId={fieldId} hintId={hintId} errorId={errorId}>
      <textarea {...controlProps} rows={rows} className={`${controlTextArea} w-full ${className}`} {...rest} />
    </Field>
  );
}
