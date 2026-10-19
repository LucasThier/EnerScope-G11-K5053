import { useMemo } from 'react';
import { NAV_GROUPS, type NavGroup, type NavItem } from '../components/layout/navItems';
import { canManageProject } from '../utils/projectPermissions';
import { useActiveProject } from './useActiveProject';
import { useAuth } from './useAuth';
import { useMyOrganizations } from './useMyOrganizations';

interface NavContext {
  isPlatformAdmin: boolean;
  belongsToOrganization: boolean;
  teamProjectId: string | null;
  versionsProjectId: string | null;
  projectName: string | null;
  pending: boolean;
}

export function useNavigationTree(): NavGroup[] {
  const { user } = useAuth();
  const { belongsToOrganization: isMember, loading: loadingOwned } = useMyOrganizations();
  const { activeProject, isLoading: loadingProjects } = useActiveProject();

  const isPlatformAdmin = user?.platformRole === 'ADMIN';
  const project = loadingProjects ? null : activeProject;

  const belongsToOrganization = !loadingOwned && isMember;
  const teamProjectId =
    project !== null && canManageProject(project.myRole, isPlatformAdmin) ? project.id : null;
  const versionsProjectId = project?.id ?? null;
  const projectName = project?.name ?? null;
  const pending = loadingOwned || loadingProjects;

  return useMemo(
    () =>
      resolveGroups({
        isPlatformAdmin,
        belongsToOrganization,
        teamProjectId,
        versionsProjectId,
        projectName,
        pending,
      }),
    [
      isPlatformAdmin,
      belongsToOrganization,
      teamProjectId,
      versionsProjectId,
      projectName,
      pending,
    ],
  );
}

function resolveGroups(context: NavContext): NavGroup[] {
  return NAV_GROUPS.map((group) => ({
    ...group,
    items: group.items
      .map((item) => resolveItem(item, context))
      .filter((item): item is NavItem => item !== null),
  })).filter((group) => group.items.length > 0);
}

function resolveItem(item: NavItem, context: NavContext): NavItem | null {
  if (!item.children) {
    return resolveLeaf(item, context);
  }

  const children = item.children
    .map((child) => resolveLeaf(child, context))
    .filter((child): child is NavItem => child !== null);

  if (children.length === 0) {
    return null;
  }
  if (children.length === 1 && !context.pending) {
    const [only] = children;
    const takesChildName = only.keepsOwnName === true;
    return {
      ...item,
      children: undefined,
      to: only.to,
      matchPattern: only.matchPattern,
      label: takesChildName ? only.label : item.label,
      fullLabel: takesChildName ? only.fullLabel : item.fullLabel,
    };
  }
  return { ...item, children };
}

function resolveLeaf(item: NavItem, context: NavContext): NavItem | null {
  switch (item.requires) {
    case 'platformAdmin':
      return context.isPlatformAdmin ? item : null;
    case 'organizationMember':
      return context.belongsToOrganization ? item : null;
    case 'manageableProject':
      return context.teamProjectId === null
        ? null
        : {
            ...item,
            to: `/projects/${context.teamProjectId}/team`,
            fullLabel: withProjectName(item.label, context.projectName),
          };
    case 'activeProject':
      return context.versionsProjectId === null
        ? null
        : {
            ...item,
            to: `/projects/${context.versionsProjectId}/versions`,
            fullLabel: withProjectName(item.label, context.projectName),
          };
    default:
      return item;
  }
}

function withProjectName(label: string, projectName: string | null): string | undefined {
  return projectName === null ? undefined : `${label}: ${projectName}`;
}
