/** Shared module matching keeps economics separate from version management. */
export const CONFIGURATION_PATTERN = /^\/projects\/[^/]+\/(?:scenario-configurations\/?$|versions\/[^/]+\/economics(?:\/configuration)?\/?$)/;
export const SIMULATION_PATTERN = /^\/projects\/[^/]+\/(?:simulations\/?$|versions\/[^/]+\/economics\/(?:history|results)(?:\/[^/]+)?\/?$)/;
export const VERSION_MANAGEMENT_PATTERN = /^\/projects\/[^/]+\/versions\/?$/;
export const COMPARISON_PATTERN = /^\/projects\/[^/]+\/comparisons(?:\/[^/]+)?\/?$/;

export function projectDestination(projectId: string, previous: string): string {
  const path = '/projects/' + encodeURIComponent(projectId);
  const oldPath = '/projects/previous/' + previous;
  if (CONFIGURATION_PATTERN.test(oldPath)) return path + '/scenario-configurations';
  if (SIMULATION_PATTERN.test(oldPath)) return path + '/simulations';
  // A saved comparison belongs to its project: switching projects opens the new project's list.
  if (COMPARISON_PATTERN.test(oldPath)) return path + '/comparisons';
  if (previous.startsWith('versions')) return path + '/versions';
  return path + (previous ? '/' + previous : '');
}
