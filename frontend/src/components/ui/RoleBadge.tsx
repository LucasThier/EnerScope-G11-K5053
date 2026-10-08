import type { PlatformRole } from '../../types/auth';

interface RoleBadgeProps {
  role: PlatformRole;
  className?: string;
}

const styles: Record<PlatformRole, string> = {
  ADMIN: 'bg-brand-100 text-brand-800',
  USER: 'bg-ink-100 text-ink-600',
};

const labels: Record<PlatformRole, string> = {
  ADMIN: 'Administrador',
  USER: 'Usuario',
};

export function RoleBadge({ role, className = '' }: RoleBadgeProps) {
  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-1 text-xs font-semibold ${styles[role]} ${className}`}
    >
      {labels[role]}
    </span>
  );
}
