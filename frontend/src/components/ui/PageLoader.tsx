import { Spinner } from './Spinner';

/** Full-viewport centered loader, used while the session bootstraps. */
export function PageLoader() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-page text-brand-800">
      <Spinner className="h-8 w-8" />
    </div>
  );
}
