type Listener = (projectId?: string) => void;

const listeners = new Set<Listener>();

/** The provider of the cached project access subscribes here. */
export function onAccessInvalidated(listener: Listener): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/** Something that changes who can do what (roles, members, versions) just succeeded. */
export function notifyAccessInvalidated(projectId?: string): void {
  listeners.forEach((listener) => listener(projectId));
}

/** Runs `notifyAccessInvalidated` once `request` succeeds and hands the response through untouched. */
export function invalidatesAccess<T>(request: Promise<T>, projectId?: string): Promise<T> {
  return request.then((response) => {
    notifyAccessInvalidated(projectId);
    return response;
  });
}
