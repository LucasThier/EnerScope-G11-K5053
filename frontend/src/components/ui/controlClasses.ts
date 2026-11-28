const shared =
  'rounded-control border border-control-border bg-surface text-sm text-ink-800 ' +
  'transition-colors placeholder:text-ink-400 ' +
  'hover:border-ink-500 ' +
  'focus:outline-none focus-visible:border-focus focus-visible:ring-2 focus-visible:ring-focus/60 ' +
  'aria-invalid:border-danger-400 aria-invalid:hover:border-danger-400 ' +
  'aria-invalid:focus-visible:border-danger-400 aria-invalid:focus-visible:ring-danger-400/50 ' +
  'disabled:cursor-not-allowed disabled:border-control-border disabled:bg-ink-50 ' +
  'disabled:text-ink-400 disabled:hover:border-control-border ' +
  'motion-reduce:transition-none';

export const controlField = `h-control px-3 ${shared}`;

export const controlCompact = `h-control-compact px-3 ${shared}`;

export const controlTextArea = `min-h-control resize-y px-3 py-2.5 ${shared}`;

export const controlFile =
  `h-control pr-3 ${shared} ` +
  'file:mr-3 file:h-full file:cursor-pointer file:rounded-l-control file:border-0 ' +
  'file:bg-ink-50 file:px-3 file:text-sm file:font-semibold file:text-ink-700 ' +
  'file:disabled:cursor-not-allowed';

/** The visible box of `FilePicker`; it mirrors the state of the hidden input that precedes it (`peer`). */
export const controlFilePicker =
  'flex h-control items-center gap-3 rounded-control border border-control-border bg-surface pr-3 text-sm ' +
  'text-ink-800 transition-colors hover:border-ink-500 ' +
  'peer-focus-visible:border-focus peer-focus-visible:ring-2 peer-focus-visible:ring-focus/60 ' +
  'peer-disabled:bg-ink-50 peer-disabled:text-ink-400 peer-disabled:hover:border-control-border ' +
  'peer-disabled:[&>label]:cursor-not-allowed motion-reduce:transition-none';

export const controlFilePickerAction =
  'flex h-full shrink-0 cursor-pointer items-center rounded-l-control bg-ink-50 px-3 text-sm font-semibold text-ink-700';
