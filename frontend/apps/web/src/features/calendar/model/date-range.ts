import {
  addDays,
  addMonths,
  endOfDay,
  endOfMonth,
  endOfWeek,
  format,
  startOfDay,
  startOfMonth,
  startOfWeek,
  subMonths,
} from 'date-fns';
import { ru } from 'date-fns/locale';

export type CalendarViewMode = 'month' | 'week' | 'day';

export type CalendarRange = {
  from: string;
  to: string;
  anchor: Date;
};

export function rangeForView(anchor: Date, mode: CalendarViewMode): CalendarRange {
  if (mode === 'day') {
    return {
      anchor,
      from: startOfDay(anchor).toISOString(),
      to: endOfDay(anchor).toISOString(),
    };
  }
  if (mode === 'week') {
    const start = startOfWeek(anchor, { weekStartsOn: 1 });
    const end = endOfWeek(anchor, { weekStartsOn: 1 });
    return { anchor, from: start.toISOString(), to: end.toISOString() };
  }
  const start = startOfWeek(startOfMonth(anchor), { weekStartsOn: 1 });
  const end = endOfWeek(endOfMonth(anchor), { weekStartsOn: 1 });
  return { anchor, from: start.toISOString(), to: end.toISOString() };
}

export function shiftAnchor(anchor: Date, mode: CalendarViewMode, direction: -1 | 1): Date {
  if (mode === 'day') return addDays(anchor, direction);
  if (mode === 'week') return addDays(anchor, direction * 7);
  return direction === 1 ? addMonths(anchor, 1) : subMonths(anchor, 1);
}

export function formatRangeLabel(anchor: Date, mode: CalendarViewMode): string {
  if (mode === 'day') {
    return format(anchor, 'd MMMM yyyy', { locale: ru });
  }
  if (mode === 'week') {
    const start = startOfWeek(anchor, { weekStartsOn: 1 });
    const end = endOfWeek(anchor, { weekStartsOn: 1 });
    return `${format(start, 'd MMM', { locale: ru })} – ${format(end, 'd MMM yyyy', { locale: ru })}`;
  }
  return format(anchor, 'LLLL yyyy', { locale: ru });
}

export function buildMonthGrid(anchor: Date): Date[] {
  const start = startOfWeek(startOfMonth(anchor), { weekStartsOn: 1 });
  const end = endOfWeek(endOfMonth(anchor), { weekStartsOn: 1 });
  const days: Date[] = [];
  let cursor = start;
  while (cursor <= end) {
    days.push(cursor);
    cursor = addDays(cursor, 1);
  }
  return days;
}

export function buildWeekDays(anchor: Date): Date[] {
  const start = startOfWeek(anchor, { weekStartsOn: 1 });
  return Array.from({ length: 7 }, (_, i) => addDays(start, i));
}

export function dayKey(date: Date | string): string {
  const d = typeof date === 'string' ? new Date(date) : date;
  return format(d, 'yyyy-MM-dd');
}

export function hourSlots(): number[] {
  return Array.from({ length: 24 }, (_, i) => i);
}
