'use client';

import { useQuery } from '@tanstack/react-query';

import { queryKeys } from '@/shared/api-client';

import { listCalendar, type ListCalendarParams } from '../api/calendar-api';

export type UseCalendarItemsParams = ListCalendarParams & {
  enabled?: boolean;
};

export function useCalendarItems(params: UseCalendarItemsParams) {
  const { workspaceId, from, to, enabled = true, ...filters } = params;

  return useQuery({
    queryKey: queryKeys.calendar.range(workspaceId, from, to, filters),
    queryFn: () => listCalendar({ workspaceId, from, to, ...filters }),
    enabled: enabled && Boolean(workspaceId && from && to),
  });
}
