export const MIN_USER_REFRESH_INTERVAL_MS = 5000;

interface AuthHandlers {
  refreshUser: () => Promise<unknown>;
  sessionLost: () => void;
}

let handlers: AuthHandlers | null = null;
let lastRefreshAt = Number.NEGATIVE_INFINITY;

export function registerAuthHandlers(next: AuthHandlers | null): void {
  handlers = next;
}

export function requestUserRefresh(now: number = Date.now()): boolean {
  if (!handlers || now - lastRefreshAt < MIN_USER_REFRESH_INTERVAL_MS) {
    return false;
  }
  lastRefreshAt = now;
  void handlers.refreshUser().catch(() => undefined);
  return true;
}

export function notifySessionLost(): void {
  handlers?.sessionLost();
}

export function resetAuthEventsForTests(): void {
  handlers = null;
  lastRefreshAt = Number.NEGATIVE_INFINITY;
}
