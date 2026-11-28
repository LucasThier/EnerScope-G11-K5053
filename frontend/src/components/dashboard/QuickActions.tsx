import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Card } from '../ui/Card';
import { BuildingIcon, CompareIcon, PlusIcon, TeamIcon, UserIcon, UsersIcon } from '../ui/icons';
import type { QuickAction, QuickActionId } from '../../utils/homeScenarios';

const ICONS: Record<QuickActionId, ReactNode> = {
  new: <PlusIcon className="h-3.5 w-3.5" />,
  compare: <CompareIcon className="h-3.5 w-3.5" />,
  users: <UsersIcon className="h-3.5 w-3.5" />,
  organizations: <BuildingIcon className="h-3.5 w-3.5" />,
  myOrganizations: <TeamIcon className="h-3.5 w-3.5" />,
  profile: <UserIcon className="h-3.5 w-3.5" />,
};

const CHIP_CLASSES =
  'flex h-11 w-full min-w-0 items-center gap-2 rounded-control border border-ink-100 px-2 text-left ' +
  'transition-colors hover:bg-ink-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-focus ' +
  'motion-reduce:transition-none [@media(max-height:760px)]:h-10';

interface QuickActionsProps {
  actions: QuickAction[];
  onNewProject: () => void;
  onCompare?: () => void;
  className?: string;
}

/** Up to four chips, an icon and a title each; the description lives in the tooltip and the accessible name. */
export function QuickActions({ actions, onNewProject, onCompare, className = '' }: QuickActionsProps) {
  return (
    <Card padded={false} className={`min-w-0 px-5 pb-4 pt-4 [@media(max-height:760px)]:pt-3 ${className}`}>
      <h2 className="text-lg font-semibold text-ink-800">Accesos rápidos</h2>
      <ul className="mt-3 grid grid-cols-2 gap-2 [@media(max-height:760px)]:mt-2">
        {actions.map((action) => {
          const content = (
            <>
              <span
                className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-brand-50 text-brand-800"
                aria-hidden="true"
              >
                {ICONS[action.id]}
              </span>
              <span className="min-w-0 line-clamp-2 text-sm font-semibold leading-tight text-ink-800">{action.label}</span>
            </>
          );
          const label = `${action.label}. ${action.hint}`;
          return (
            <li key={action.id} className="min-w-0">
              {action.to ? (
                <Link
                  to={action.to}
                  title={action.hint}
                  aria-label={label}
                  onClick={action.id === 'compare' ? onCompare : undefined}
                  className={CHIP_CLASSES}
                >
                  {content}
                </Link>
              ) : (
                <button type="button" title={action.hint} aria-label={label} onClick={onNewProject} className={CHIP_CLASSES}>
                  {content}
                </button>
              )}
            </li>
          );
        })}
      </ul>
    </Card>
  );
}
