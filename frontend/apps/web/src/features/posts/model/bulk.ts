export type BulkAction = 'pause' | 'resume' | 'delete' | 'reschedule' | 'duplicate';

export type BulkSelection = {
  selectedIds: Set<string>;
};

export function toggleSelection(selected: Set<string>, id: string): Set<string> {
  const next = new Set(selected);
  if (next.has(id)) next.delete(id);
  else next.add(id);
  return next;
}

export function selectAll(ids: string[]): Set<string> {
  return new Set(ids);
}

export function clearSelection(): Set<string> {
  return new Set();
}

export function selectionCount(selected: Set<string>): number {
  return selected.size;
}

export function canRunBulkAction(
  action: BulkAction,
  selectedCount: number,
  permissions: { bulk: boolean; delete: boolean },
): boolean {
  if (selectedCount === 0) return false;
  if (action === 'delete') return permissions.delete || permissions.bulk;
  return permissions.bulk;
}
