import type { ScheduleWindowValue, TimeInterval, Weekday } from '@/features/automations-common/model/schedule';

/** Backend schedule block (ISO days Mon=1…Sun=7, hours_from/hours_to). */
export type BackendSchedule = {
  enabled: boolean;
  timezone: string;
  days_of_week: number[];
  hours_from: number;
  hours_to: number;
};

/** ISO weekday (1=Mon … 7=Sun) → JS Date#getDay (0=Sun … 6=Sat). */
export function isoDayToJs(day: number): Weekday {
  if (day === 7) return 0;
  if (day >= 1 && day <= 6) return day as Weekday;
  return 1;
}

/** JS Date#getDay (0=Sun … 6=Sat) → ISO weekday (1=Mon … 7=Sun). */
export function jsDayToIso(day: Weekday): number {
  return day === 0 ? 7 : day;
}

function pad2(n: number): string {
  return String(n).padStart(2, '0');
}

function parseHour(time: string): number {
  const match = /^([01]?\d|2[0-3]):([0-5]\d)$/.exec(time.trim());
  if (!match) return 0;
  return Number(match[1]);
}

function parseEndHour(time: string): number {
  const match = /^([01]?\d|2[0-4]):([0-5]\d)$/.exec(time.trim());
  if (!match) return 24;
  const hour = Number(match[1]);
  const minute = Number(match[2]);
  // 23:59 and 24:00 both map to exclusive end 24 for backend.
  if (hour === 24) return 24;
  if (hour === 23 && minute === 59) return 24;
  return minute > 0 ? Math.min(24, hour + 1) : hour;
}

/**
 * Map ScheduleWindowEditor value → backend schedule.
 * Backend supports a single hours_from/hours_to window; multiple intervals
 * collapse to the min start hour and max end hour.
 */
export function scheduleWindowToBackend(
  value: ScheduleWindowValue,
  enabled: boolean,
): BackendSchedule {
  const days =
    value.days.length > 0
      ? [...new Set(value.days.map(jsDayToIso))].sort((a, b) => a - b)
      : [1, 2, 3, 4, 5, 6, 7];

  let hoursFrom = 0;
  let hoursTo = 24;
  if (value.intervals.length > 0) {
    hoursFrom = Math.min(...value.intervals.map((i) => parseHour(i.start)));
    hoursTo = Math.max(...value.intervals.map((i) => parseEndHour(i.end)));
    if (hoursFrom >= hoursTo && hoursTo !== 24) {
      hoursTo = Math.min(24, hoursFrom + 1);
    }
  }

  return {
    enabled,
    timezone: value.timezone || 'Europe/Moscow',
    days_of_week: days,
    hours_from: hoursFrom,
    hours_to: hoursTo,
  };
}

/** Map backend schedule → ScheduleWindowEditor value. */
export function backendScheduleToWindow(schedule: Partial<BackendSchedule> | undefined): {
  window: ScheduleWindowValue;
  enabled: boolean;
} {
  const enabled = Boolean(schedule?.enabled);
  const timezone =
    typeof schedule?.timezone === 'string' && schedule.timezone
      ? schedule.timezone
      : 'Europe/Moscow';
  const rawDays = Array.isArray(schedule?.days_of_week) ? schedule!.days_of_week! : [1, 2, 3, 4, 5, 6, 7];
  const days = [...new Set(rawDays.map((d) => isoDayToJs(Number(d))))].sort(
    (a, b) => a - b,
  ) as Weekday[];

  const hoursFrom =
    typeof schedule?.hours_from === 'number' && Number.isFinite(schedule.hours_from)
      ? Math.max(0, Math.min(23, Math.floor(schedule.hours_from)))
      : 0;
  const hoursTo =
    typeof schedule?.hours_to === 'number' && Number.isFinite(schedule.hours_to)
      ? Math.max(0, Math.min(24, Math.floor(schedule.hours_to)))
      : 24;

  const intervals: TimeInterval[] =
    hoursFrom === 0 && hoursTo === 24
      ? []
      : [
          {
            start: `${pad2(hoursFrom)}:00`,
            end: hoursTo >= 24 ? '23:59' : `${pad2(hoursTo)}:00`,
          },
        ];

  return {
    enabled,
    window: { days, intervals, timezone },
  };
}
