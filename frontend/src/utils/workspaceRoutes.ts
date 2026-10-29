/** Shared module matching keeps economics separate from version management. */
export const CONFIGURATION_PATTERN = /^\/projects\/[^/]+\/(?:scenario-configurations\/?$|versions\/[^/]+\/economics(?:\/configuration)?\/?$)/;
export const SIMULATION_PATTERN = /^\/projects\/[^/]+\/(?:simulations\/?$|versions\/[^/]+\/economics\/(?:history|results)(?:\/[^/]+)?\/?$)/;
export const VERSION_MANAGEMENT_PATTERN = /^\/projects\/[^/]+\/versions\/?$/;

export function projectDestination(projectId: string, previous: string): string {
  const path = '/projects/' + encodeURIComponent(projectId);
  const oldPath = '/projects/previous/' + previous;
  if (CONFIGURATION_PATTERN.test(oldPath)) return path + '/scenario-configurations';
  if (SIMULATION_PATTERN.test(oldPath)) return path + '/simulations';
  if (previous.startsWith('versions')) return path + '/versions';
  return path + (previous ? '/' + previous : '');
}
