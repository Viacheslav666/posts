export type Weekday = 0 | 1 | 2 | 3 | 4 | 5 | 6;

export type TimeInterval = {
  start: string;
  end: string;
};

export type ScheduleWindowValue = {
  /** 0 = Sunday … 6 = Saturday (JS Date#getDay). */
  days: Weekday[];
  intervals: TimeInterval[];
  timezone: string;
};

export const WEEKDAYS: Weekday[] = [1, 2, 3, 4, 5, 6, 0];

const TIME_RE = /^([01]\d|2[0-3]):([0-5]\d)$/;

export function isValidTime(value: string): boolean {
  return TIME_RE.test(value);
}

export function toggleWeekday(days: Weekday[], day: Weekday): Weekday[] {
  if (days.includes(day)) {
    return days.filter((item) => item !== day);
  }
  return [...days, day].sort((a, b) => a - b) as Weekday[];
}
