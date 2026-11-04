import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Card } from '../ui/Card';
import {
  BuildingIcon,
  ChevronRightIcon,
  PlusIcon,
  TeamIcon,
  UserIcon,
  UsersIcon,
} from '../ui/icons';
import { MY_ORGANIZATIONS } from '../layout/navItems';

interface QuickActionsProps {
  isAdmin: boolean;
  canCreateProject: boolean;
  belongsToOrganization: boolean;
  onNewProject: () => void;
}

const ROW_CLASSES =
  'flex min-h-control w-full items-center gap-3 rounded-control border border-ink-100 px-3 py-2 ' +
  'text-left transition-colors hover:bg-ink-50 focus:outline-none focus-visible:ring-2 ' +
  'focus-visible:ring-focus motion-reduce:transition-none';

export function QuickActions({ isAdmin, canCreateProject, belongsToOrganization, onNewProject }: QuickActionsProps) {
  return (
    <Card className="min-w-0">
      <h2 className="text-lg font-semibold text-ink-800">Accesos rápidos</h2>
      <ul className="mt-4 flex flex-col gap-3">
        {canCreateProject && (
          <li>
            <ActionRow
              icon={<PlusIcon className="h-5 w-5" />}
              title="Nuevo proyecto"
              description="Creá un proyecto en una de tus organizaciones."
              onClick={onNewProject}
            />
          </li>
        )}
        {isAdmin && (
          <>
            <li>
              <ActionRow
                icon={<UsersIcon className="h-5 w-5" />}
                title="Gestionar usuarios"
                description="Administrá las cuentas de la plataforma."
                to="/admin/users"
              />
            </li>
            <li>
              <ActionRow
                icon={<BuildingIcon className="h-5 w-5" />}
                title="Organizaciones"
                description="Gestioná las organizaciones."
                to="/admin/organizations"
              />
            </li>
          </>
        )}
        {belongsToOrganization && (
          <li>
            <ActionRow
              icon={<TeamIcon className="h-5 w-5" />}
              title={MY_ORGANIZATIONS.label}
              description={MY_ORGANIZATIONS.description}
              to={MY_ORGANIZATIONS.to}
            />
          </li>
        )}
        <li>
          <ActionRow
            icon={<UserIcon className="h-5 w-5" />}
            title="Mi perfil"
            description="Actualizá tus datos y tu contraseña."
            to="/profile"
          />
        </li>
      </ul>
    </Card>
  );
}

interface ActionRowProps {
  icon: ReactNode;
  title: string;
  description: string;
  to?: string;
  onClick?: () => void;
}

function ActionRow({ icon, title, description, to, onClick }: ActionRowProps) {
  const content = (
    <>
      <span
        className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-brand-50 text-brand-800"
        aria-hidden="true"
      >
        {icon}
      </span>
      <span className="min-w-0 flex-1">
        <span className="block text-sm font-semibold text-ink-800">{title}</span>
        <span className="block text-xs text-ink-500">{description}</span>
      </span>
      <ChevronRightIcon className="h-4 w-4 shrink-0 text-ink-400" />
    </>
  );

  return to ? (
    <Link to={to} className={ROW_CLASSES}>
      {content}
    </Link>
  ) : (
    <button type="button" onClick={onClick} className={ROW_CLASSES}>
      {content}
    </button>
  );
}
