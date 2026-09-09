/** A project as returned by the list endpoint. Mirrors backend ProjectSummaryDTO. */
export interface ProjectSummary {
  id: string;
  name: string;
  description: string;
  organizationId: string;
  organizationName: string;
  memberCount: number;
  lastModified: string;
}
