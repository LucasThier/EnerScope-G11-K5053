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

export interface Project {
  id: string;
  name: string;
  description: string;
  organizationId: string;
}

export type ProjectMemberType = 'ADMIN' | 'EDITOR';

export type ProjectMemberPermission = 'MANAGE_PROJECT' | 'EDIT_PROJECT' | 'VIEW_PROJECT';

/** A member of a project. Mirrors backend ProjectMemberDTO. */
export interface ProjectMember {
  id: string;
  userId: string;
  userMail: string;
  firstName: string;
  lastName: string;
  jobTitle: string | null;
  active: boolean;
  memberType: ProjectMemberType;
  permissions: ProjectMemberPermission[];
}

export interface CreateProjectRequest {
  name: string;
  description: string;
  organizationId: string;
}
