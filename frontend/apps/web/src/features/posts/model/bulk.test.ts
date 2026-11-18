import { describe, expect, it } from 'vitest';

import {
  canRunBulkAction,
  clearSelection,
  selectAll,
  selectionCount,
  toggleSelection,
} from './bulk';

describe('bulk selection', () => {
  it('toggles and clears selection', () => {
    let selected = clearSelection();
    selected = toggleSelection(selected, 'a');
    selected = toggleSelection(selected, 'b');
    expect(selectionCount(selected)).toBe(2);
    selected = toggleSelection(selected, 'a');
    expect(selected.has('a')).toBe(false);
    expect(selectAll(['x', 'y']).size).toBe(2);
  });

  it('gates actions by permissions', () => {
    expect(canRunBulkAction('pause', 0, { bulk: true, delete: true })).toBe(false);
    expect(canRunBulkAction('pause', 2, { bulk: true, delete: false })).toBe(true);
    expect(canRunBulkAction('pause', 2, { bulk: false, delete: true })).toBe(false);
    expect(canRunBulkAction('delete', 1, { bulk: false, delete: true })).toBe(true);
  });
});
