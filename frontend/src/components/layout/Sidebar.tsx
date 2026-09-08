import { NavLink } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuth } from '../../hooks/useAuth';
import { Logo } from '../ui/Logo';
import type { PlatformRole } from '../../types/auth';

interface NavItem {
  to: string;
  label: string;
  icon: ReactNode;
}

// Small inline icons (no icon dependency; brand-neutral, inherit currentColor).
const icons = {
  users: (
    <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
      <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" strokeLinecap="round" />
      <circle cx="9" cy="7" r="4" />
      <path d="M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" strokeLinecap="round" />
    </svg>
  ),
  org: (
    <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
      <path d="M3 21h18M6 21V7l6-4 6 4v14" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M10 12h4M10 16h4" strokeLinecap="round" />
    </svg>
  ),
  workspace: (
    <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
      <rect x="3" y="3" width="7" height="9" rx="1" />
      <rect x="14" y="3" width="7" height="5" rx="1" />
      <rect x="14" y="12" width="7" height="9" rx="1" />
      <rect x="3" y="16" width="7" height="5" rx="1" />
    </svg>
  ),
};

function navFor(role: PlatformRole): NavItem[] {
  if (role === 'ADMIN') {
    return [
      { to: '/admin/users', label: 'Users', icon: icons.users },
      { to: '/admin/organizations', label: 'Organizations', icon: icons.org },
    ];
  }
  return [{ to: '/app', label: 'Workspace', icon: icons.workspace }];
}

export function Sidebar() {
  const { user } = useAuth();
  const items = user ? navFor(user.platformRole) : [];

  return (
    <aside className="flex w-16 shrink-0 flex-col border-r border-ink-100 bg-white md:w-56">
      <div className="flex h-16 items-center border-b border-ink-100 px-3 md:px-5">
        {/* Mark only on narrow screens, full wordmark on desktop. */}
        <span className="md:hidden">
          <Logo withWordmark={false} />
        </span>
        <span className="hidden md:block">
          <Logo />
        </span>
      </div>
      <nav className="flex flex-1 flex-col gap-1 p-2 md:p-3">
        {items.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            className={({ isActive }) =>
              'flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors ' +
              (isActive
                ? 'bg-brand-50 text-brand-800'
                : 'text-ink-500 hover:bg-ink-50 hover:text-ink-700')
            }
          >
            <span className="shrink-0">{item.icon}</span>
            <span className="hidden md:inline">{item.label}</span>
          </NavLink>
        ))}
      </nav>
    </aside>
  );
}
