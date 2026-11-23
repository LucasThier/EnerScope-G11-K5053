/*
 * The cell styles every data table shares, in the same spirit as
 * controlClasses.ts: written once, so a table asks for the role and a header
 * cannot drift to another size or colour.
 *
 * Two densities, on purpose. Pick by where the table lives:
 * - standard (px-4 py-3): a table that is the content of a page or card —
 *   projects, users, organizations, scenarios, saved evaluations.
 * - compact (px-3 py-2): a table inside a modal or a side panel, or a dense one
 *   with many numeric columns — team members, the CSV import preview, the
 *   version-role panels, the cash-flow table.
 *
 * The header is the small uppercase label of the system (text-xs uppercase
 * tracking-wide text-ink-500, ink-500 being the floor for text). Vertical
 * alignment (`align-top`, `align-middle`), `whitespace-nowrap`, `text-right`
 * and the like are per table: append them where the table is written.
 */
export const tableHeaderCell =
  'px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-ink-500';

export const tableBodyCell = 'px-4 py-3 text-sm text-ink-700';

export const tableHeaderCellCompact =
  'px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-ink-500';

export const tableBodyCellCompact = 'px-3 py-2 text-sm text-ink-700';
