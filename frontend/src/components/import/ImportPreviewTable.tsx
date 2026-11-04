import { MEMBER_TYPE_LABELS } from '../organizations/memberTypeLabels';
import type { ImportRow } from '../../utils/csvImport';

interface ImportPreviewTableProps {
  rows: ImportRow[];
}

const headerCell = 'px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-ink-500';
const bodyCell = 'px-3 py-2 align-top text-sm text-ink-700';

export function ImportPreviewTable({ rows }: ImportPreviewTableProps) {
  return (
    <div
      tabIndex={0}
      role="group"
      aria-label="Vista previa de las filas del archivo"
      className={
        'max-h-72 overflow-auto rounded-lg border border-ink-100 ' +
        'focus:outline-none focus-visible:ring-2 focus-visible:ring-focus'
      }
    >
      <table className="w-full border-collapse">
        <thead className="sticky top-0 bg-raised">
          <tr className="border-b border-ink-100">
            <th scope="col" className={headerCell}>
              Línea
            </th>
            <th scope="col" className={headerCell}>
              Email
            </th>
            <th scope="col" className={headerCell}>
              Nombre
            </th>
            <th scope="col" className={headerCell}>
              Rol
            </th>
            <th scope="col" className={headerCell}>
              Estado
            </th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr
              key={`${row.line}-${row.mail}`}
              className={`border-b border-ink-100 last:border-0 ${
                row.error ? 'bg-danger-50' : ''
              }`}
            >
              <td className={`${bodyCell} tabular-nums text-ink-500`}>{row.line}</td>
              <td className={`${bodyCell} break-all`}>{row.mail || '—'}</td>
              <td className={bodyCell}>
                {`${row.firstName} ${row.lastName}`.trim() || '—'}
              </td>
              <td className={`${bodyCell} whitespace-nowrap`}>
                {MEMBER_TYPE_LABELS[row.role ?? 'MEMBER']}
              </td>
              <td className={bodyCell}>
                {row.error ? (
                  <span className="font-medium text-danger-700">{row.error}</span>
                ) : (
                  <span className="text-ink-500">Se va a crear</span>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
