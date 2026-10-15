import type { AxiosResponse } from 'axios';
import { client } from './client';
import type { ApiResponse } from '../types/auth';
import type { VersionConflict, VersionSummary } from '../types/diagram';

/**
 * Version (scenario) management on `/version/**`. Creating a version lives on
 * `projectsApi.createVersion`, because a version is created inside a project.
 */
export const versionsApi = {
  rename: (versionId: string, name: string): Promise<AxiosResponse<ApiResponse<VersionSummary>>> =>
    client.patch(`/version/${versionId}`, { name }),

  /** Deletes the version and every version branched from it. */
  remove: (versionId: string): Promise<AxiosResponse<ApiResponse<null>>> =>
    client.delete(`/version/${versionId}`),

  /** Applies the version's changes onto its parent; siblings may get conflicts. */
  mergeIntoParent: (versionId: string): Promise<AxiosResponse<ApiResponse<VersionSummary>>> =>
    client.patch(`/version/merge/${versionId}`),

  conflicts: (versionId: string): Promise<AxiosResponse<ApiResponse<VersionConflict[]>>> =>
    client.get(`/version/${versionId}/conflicts`),

  /** ACCEPT takes the parent's data into this version; REJECT keeps this version's own. */
  resolveConflict: (
    conflictId: string,
    decision: 'ACCEPT' | 'REJECT',
  ): Promise<AxiosResponse<ApiResponse<VersionConflict>>> =>
    client.patch(`/version/conflicts/${conflictId}/resolve/${decision}`),
};
