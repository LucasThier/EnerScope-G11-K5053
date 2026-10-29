import { useId } from 'react';

export interface FieldMessages {
  hint?: string;
  error?: string;
}

export interface FieldWiring {
  fieldId: string;
  hintId: string;
  errorId: string;
  controlProps: {
    id: string;
    'aria-invalid': true | undefined;
    'aria-describedby': string | undefined;
  };
}

export function useField(id: string | undefined, { hint, error }: FieldMessages): FieldWiring {
  const generated = useId();
  const fieldId = id ?? generated;
  const hintId = `${fieldId}-hint`;
  const errorId = `${fieldId}-error`;
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ');
  return {
    fieldId,
    hintId,
    errorId,
    controlProps: {
      id: fieldId,
      'aria-invalid': error ? true : undefined,
      'aria-describedby': describedBy === '' ? undefined : describedBy,
    },
  };
}
