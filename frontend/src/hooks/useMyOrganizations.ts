import { createContext, useContext } from 'react';
import type { OrganizationMemberType, OrganizationSummary } from '../types/auth';

export interface OrganizationMembership {
  id: string;
  name: string;
  role: OrganizationMemberType;
}

export interface MyOrganizationsContextValue {
  organizations: OrganizationSummary[];
  owned: OrganizationSummary[];
  memberships: OrganizationMembership[];
  belongsToOrganization: boolean;
  loading: boolean;
  error: string | null;
  reload: () => Promise<void>;
}

export const MyOrganizationsContext = createContext<MyOrganizationsContextValue | undefined>(
  undefined,
);

export function useMyOrganizations(): MyOrganizationsContextValue {
  const ctx = useContext(MyOrganizationsContext);
  if (!ctx) {
    throw new Error('useMyOrganizations must be used within a MyOrganizationsProvider');
  }
  return ctx;
}
