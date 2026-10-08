import type { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card } from '../ui/Card';
import { PlusIcon, TeamIcon, UsersIcon } from '../ui/icons';
import { useOwnedOrganizations } from '../../hooks/useOwnedOrganizations';

interface QuickActionsProps {
  onNewProject: () => void;
}

export function QuickActions({ onNewProject }: QuickActionsProps) {
  const navigate = useNavigate();
  const { owned } = useOwnedOrganizations();

  return (
    <Card>
      <h2 className="text-lg font-semibold text-ink-800">Accesos rápidos</h2>
      <div className="mt-4 flex flex-col gap-2">
        <ActionButton icon={<PlusIcon className="h-4 w-4" />} onClick={onNewProject}>
          Nuevo proyecto
        </ActionButton>
        <ActionButton icon={<UsersIcon className="h-4 w-4" />} onClick={() => navigate('/profile')}>
          Mi perfil
        </ActionButton>
        {owned.length > 0 && (
          <ActionButton
            icon={<TeamIcon className="h-4 w-4" />}
            onClick={() => navigate(`/organizations/${owned[0].id}`)}
          >
            Gestión de organización
          </ActionButton>
        )}
      </div>
    </Card>
  );
}

function ActionButton({
  icon,
  onClick,
  children,
}: {
  icon: ReactNode;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={
        'flex items-center gap-3 rounded-lg px-3 py-2 text-left text-sm font-medium text-ink-700 ' +
        'transition-colors hover:bg-ink-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-400'
      }
    >
      <span className="text-ink-500">{icon}</span>
      {children}
    </button>
  );
}
