import { MergeIcon, TrashIcon } from '../ui/icons';
import { RowButton } from '../ui/RowButton';
import { formatDate } from '../../utils/date';
import { isMergeBlocked, MERGE_BLOCKED_MESSAGE } from '../../utils/versionMerge';
import type { VersionSummary } from '../../types/diagram';
import type { VersionTreeNode } from '../../utils/versionTree';

interface VersionTreeProps {
  nodes: VersionTreeNode[];
  canEdit: boolean;
  unresolvedConflicts: Record<string, number>;
  onDelete: (version: VersionSummary, isRoot: boolean) => void;
  onMerge: (version: VersionSummary) => void;
  onResolveConflicts: (version: VersionSummary) => void;
}

interface VersionTreeRowProps extends Omit<VersionTreeProps, 'nodes'> {
  node: VersionTreeNode;
  depth: number;
  parentName: string | null;
}

export function VersionTree({
  nodes,
  canEdit,
  unresolvedConflicts,
  onDelete,
  onMerge,
  onResolveConflicts,
}: VersionTreeProps) {
  return (
    <ul className="divide-y divide-ink-100">
      {nodes.map((node) => (
        <VersionTreeRow
          key={node.version.id}
          node={node}
          depth={0}
          parentName={null}
          canEdit={canEdit}
          unresolvedConflicts={unresolvedConflicts}
          onDelete={onDelete}
          onMerge={onMerge}
          onResolveConflicts={onResolveConflicts}
        />
      ))}
    </ul>
  );
}

function VersionTreeRow({
  node,
  depth,
  parentName,
  canEdit,
  unresolvedConflicts,
  onDelete,
  onMerge,
  onResolveConflicts,
}: VersionTreeRowProps) {
  const isRoot = depth === 0;
  const conflictCount = unresolvedConflicts[node.version.id] ?? 0;
  const mergeBlocked = isMergeBlocked(conflictCount);

  return (
    <li>
      <div
        className="flex items-center gap-3 px-4 py-3"
        style={{ paddingLeft: `${1 + depth * 1.5}rem` }}
      >
        <span className="text-sm font-medium text-ink-800">{node.version.name}</span>
        {isRoot && (
          <span className="inline-flex items-center rounded-full bg-brand-100 px-2 py-0.5 text-xs font-semibold text-brand-800">
            Base
          </span>
        )}
        {conflictCount > 0 && (
          <button
            type="button"
            aria-label={`Resolver conflictos de ${node.version.name}`}
            title="Resolver conflictos"
            onClick={() => onResolveConflicts(node.version)}
            className={
              'inline-flex items-center rounded-full bg-danger-50 px-2 py-0.5 text-xs font-semibold ' +
              'text-danger-700 transition-colors hover:bg-danger-200 focus:outline-none ' +
              'focus-visible:ring-2 focus-visible:ring-danger-400'
            }
          >
            {conflictCount === 1 ? '1 conflicto' : `${conflictCount} conflictos`}
          </button>
        )}
        <span className="ml-auto whitespace-nowrap text-sm text-ink-500">
          {formatDate(node.version.createdAt)}
        </span>
        {canEdit && !isRoot && parentName !== null && (
          <RowButton
            label={
              mergeBlocked
                ? `${MERGE_BLOCKED_MESSAGE} en ${node.version.name}`
                : `Mergear ${node.version.name} con ${parentName}`
            }
            title={mergeBlocked ? MERGE_BLOCKED_MESSAGE : 'Mergear'}
            disabled={mergeBlocked}
            onClick={() => onMerge(node.version)}
          >
            <MergeIcon className="h-4 w-4" />
          </RowButton>
        )}
        {canEdit && (
          <RowButton
            label={`Eliminar versión ${node.version.name}`}
            title="Eliminar"
            onClick={() => onDelete(node.version, isRoot)}
          >
            <TrashIcon className="h-4 w-4" />
          </RowButton>
        )}
      </div>
      {node.children.length > 0 && (
        <ul className="divide-y divide-ink-100">
          {node.children.map((child) => (
            <VersionTreeRow
              key={child.version.id}
              node={child}
              depth={depth + 1}
              parentName={node.version.name}
              canEdit={canEdit}
              unresolvedConflicts={unresolvedConflicts}
              onDelete={onDelete}
              onMerge={onMerge}
              onResolveConflicts={onResolveConflicts}
            />
          ))}
        </ul>
      )}
    </li>
  );
}
