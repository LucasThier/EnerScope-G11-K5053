import { useId } from 'react';
import { controlFilePicker, controlFilePickerAction } from './controlClasses';
import type { FieldWiring } from './useField';

interface FilePickerProps {
  /** From `useField`; the surrounding `Field` provides the visible label. */
  controlProps: FieldWiring['controlProps'];
  accept: string;
  /** The chosen file's name, or null while there is none. */
  fileName: string | null;
  disabled?: boolean;
  onSelect: (file: File | null) => void;
}

/**
 * A file input with its own Spanish texts: the browser writes the native button and "no file" text in its own
 * language. The native input stays in the page, visually hidden, so it keeps the keyboard focus, the label and the
 * Enter / Space behaviour; the box beside it shows that focus and forwards the click.
 */
export function FilePicker({ controlProps, accept, fileName, disabled = false, onSelect }: FilePickerProps) {
  const statusId = useId();
  return (
    <div className="relative">
      <input
        {...controlProps}
        type="file"
        accept={accept}
        disabled={disabled}
        aria-describedby={[statusId, controlProps['aria-describedby']].filter(Boolean).join(' ')}
        // Clearing the value lets the same file be chosen again after it was fixed on disk.
        onClick={(event) => {
          event.currentTarget.value = '';
        }}
        onChange={(event) => onSelect(event.target.files?.[0] ?? null)}
        className="peer sr-only"
      />
      <div className={controlFilePicker}>
        <label htmlFor={controlProps.id} aria-hidden="true" className={controlFilePickerAction}>
          Elegir archivo
        </label>
        <span id={statusId} aria-live="polite" title={fileName ?? undefined} className="min-w-0 flex-1 truncate">
          {fileName ?? 'Ningún archivo seleccionado'}
        </span>
      </div>
    </div>
  );
}
