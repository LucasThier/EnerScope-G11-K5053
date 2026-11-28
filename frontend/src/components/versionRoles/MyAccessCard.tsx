import { Alert } from '../ui/Alert';
import { tableBodyCellCompact, tableHeaderCellCompact } from '../ui/tableClasses';
import { Card } from '../ui/Card';
import { ALL_VERSIONS_ADMIN_LABEL, versionRoleLabel } from '../projects/versionRoleLabels';
import {
  callerProjectRoleLabel,
  capabilityLabels,
  sortGrants,
  summarizeAccess,
} from '../../utils/versionRoles';
import type { ProjectAccess } from '../../types/versionAccess';

interface MyAccessCardProps {
  access: ProjectAccess;
  onOpenVersion: (versionId: string) => void;
}

const chip = 'rounded-full bg-ink-100 px-2 py-0.5 text-xs font-semibold text-ink-700';

export function MyAccessCard({ access, onOpenVersion }: MyAccessCardProps) {
  const summary = summarizeAccess(access);
  const projectRole = callerProjectRoleLabel(summary.projectRole, summary.platformAdmin);

  return (
    <Card>
      <h2 className="text-lg font-semibold text-ink-800">Mis accesos</h2>

      {summary.administersAll ? (
        <div className="mt-3 flex flex-col gap-3">
          <p className="text-sm text-ink-700">
            <span className="font-semibold text-ink-800">{ALL_VERSIONS_ADMIN_LABEL}</span>
            {projectRole ? ` · ${projectRole}` : ''}
          </p>
          {summary.withoutEffect.length > 0 && (
            <Alert tone="info">
              Tenés roles de versión asignados en {summary.withoutEffect.length}{' '}
              {summary.withoutEffect.length === 1 ? 'versión' : 'versiones'}, pero no tienen efecto
              porque administrás todas.
            </Alert>
          )}
        </div>
      ) : (
        <div className="mt-3 flex flex-col gap-3">
          {projectRole && (
            <p className="text-sm text-ink-700">
              Tu rol en el proyecto:{' '}
              <span className="font-semibold text-ink-800">{projectRole}</span>.
            </p>
          )}

          {summary.explicit.length === 0 ? (
            <p className="text-sm text-ink-500">
              No tenés roles propios en ninguna versión: en todas valen los permisos de tu rol en el
              proyecto.
            </p>
          ) : (
            <div className="max-h-80 overflow-auto rounded-control border border-ink-100">
              <table className="w-full border-collapse">
                <thead>
                  <tr className="border-b border-ink-100 bg-ink-50">
                    <th
                      scope="col"
                      className={tableHeaderCellCompact}
                    >
                      Versión
                    </th>
                    <th
                      scope="col"
                      className={tableHeaderCellCompact}
                    >
                      Mis roles
                    </th>
                    <th
                      scope="col"
                      className={tableHeaderCellCompact}
                    >
                      Puedo
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {summary.explicit.map((entry) => (
                    <tr key={entry.versionId} className="border-b border-ink-100 last:border-b-0">
                      <td className={`${tableBodyCellCompact} align-top`}>
                        <button
                          type="button"
                          onClick={() => onOpenVersion(entry.versionId)}
                          className="text-left font-semibold text-brand-800 hover:underline focus:outline-none focus-visible:ring-2 focus-visible:ring-focus"
                        >
                          {entry.versionName}
                        </button>
                      </td>
                      <td className="px-3 py-2 align-top">
                        <div className="flex flex-wrap gap-1">
                          {sortGrants(entry.roles).map((role) => (
                            <span key={`${role.type}-${role.vertical}`} className={chip}>
                              {versionRoleLabel(role.type, role.vertical)}
                            </span>
                          ))}
                        </div>
                      </td>
                      <td className="px-3 py-2 align-top text-sm text-ink-700">
                        {capabilityLabels(entry).join(' · ')}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {summary.fallbackCount > 0 && summary.explicit.length > 0 && (
            <p className="text-sm text-ink-500">
              En {summary.fallbackCount === 1 ? 'la otra versión' : `las otras ${summary.fallbackCount} versiones`}{' '}
              valen los permisos de tu rol en el proyecto
              {projectRole ? ` (${projectRole})` : ''}.
            </p>
          )}
        </div>
      )}
    </Card>
  );
}
