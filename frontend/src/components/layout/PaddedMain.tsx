import { Outlet } from 'react-router-dom';

/**
 * Layout for regular pages: a centred, padded, scrollable main area. Full-bleed
 * pages (like the editor) render directly under {@code AppLayout} instead.
 */
export function PaddedMain() {
  return (
    <main className="mx-auto w-full max-w-4xl flex-1 overflow-y-auto px-6 py-8">
      <Outlet />
    </main>
  );
}
