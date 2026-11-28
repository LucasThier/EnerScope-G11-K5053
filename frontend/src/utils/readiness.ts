import { translateBackendMessage } from '../api/errors';

/** Reasons about the version's diagram: they are fixed in the editor, not in the scenario configuration. */
const DIAGRAM_REASONS = new Set([
  'The value chain graph has a connection the simulator cannot process',
  'An LNG carrier has no seaport terminal or FLNG unit connected before it',
  'The simulator could not process the value chain graph; review its nodes and connections',
  'Simulation graph contains a cycle',
  'Invalid or duplicate connection',
]);

/** The scenario could not be checked at all: there is nothing the person can fix from a link. */
const NOT_CHECKED = 'The scenario could not be checked; review the server log';

export type ReadinessFix = 'diagram' | 'configuration' | null;

export interface ReadinessView {
  /** Why the scenario cannot be simulated, in the screen's language. */
  reason: string;
  /** Where it is fixed. */
  fix: ReadinessFix;
}

/** What to say about a scenario that is not ready, from the backend's `readinessErrors`. */
export function readinessView(errors: string[]): ReadinessView {
  const reason = errors.map(translateBackendMessage).join(' ') || 'la configuración está incompleta.';
  if (errors.some((error) => DIAGRAM_REASONS.has(error))) {
    return { reason, fix: 'diagram' };
  }
  return { reason, fix: errors.includes(NOT_CHECKED) ? null : 'configuration' };
}
