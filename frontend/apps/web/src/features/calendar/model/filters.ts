export type CalendarFiltersState = {
  projectId?: string;
  channelId?: string;
  authorId?: string;
  network?: string;
  status?: string;
  contentKind?: 'post' | 'story';
  q?: string;
};

export function emptyCalendarFilters(): CalendarFiltersState {
  return {};
}

export function calendarFiltersActive(filters: CalendarFiltersState): number {
  return Object.values(filters).filter((v) => v !== undefined && v !== '').length;
}

export function applyClientSearch<T extends { title?: string }>(
  items: T[],
  q?: string,
): T[] {
  if (!q?.trim()) return items;
  const needle = q.trim().toLowerCase();
  return items.filter((item) => (item.title ?? '').toLowerCase().includes(needle));
}
