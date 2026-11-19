import type {
  ScheduleWindowValue,
  TimeInterval,
  Weekday,
} from '@/features/automations-common/model/schedule';

/** One window in suggest_settings.config.schedule.windows (ISO Mon=1…Sun=7). */
export type SuggestScheduleWindow = {
  days_of_week: number[];
  from: string;
  to: string;
};

export type SuggestBackendSchedule = {
  enabled: boolean;
  timezone: string;
  windows: SuggestScheduleWindow[];
  min_interval_minutes: number;
  pause_after_ads_minutes: number;
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

/**
 * Map ScheduleWindowEditor value → suggest schedule.windows.
 * Each interval becomes a window sharing the selected days.
 */
export function scheduleWindowToSuggest(
  value: ScheduleWindowValue,
  enabled: boolean,
  extras?: { minIntervalMinutes?: number; pauseAfterAdsMinutes?: number },
): SuggestBackendSchedule {
  const days =
    value.days.length > 0
      ? [...new Set(value.days.map(jsDayToIso))].sort((a, b) => a - b)
      : [1, 2, 3, 4, 5];

  const intervals: TimeInterval[] =
    value.intervals.length > 0 ? value.intervals : [{ start: '09:00', end: '21:00' }];

  return {
    enabled,
    timezone: value.timezone || 'Europe/Moscow',
    windows: intervals.map((interval) => ({
      days_of_week: days,
      from: interval.start,
      to: interval.end,
    })),
    min_interval_minutes: extras?.minIntervalMinutes ?? 0,
    pause_after_ads_minutes: extras?.pauseAfterAdsMinutes ?? 0,
  };
}

/** Map suggest schedule.windows → ScheduleWindowEditor value. */
export function suggestScheduleToWindow(schedule: Partial<SuggestBackendSchedule> | undefined): {
  window: ScheduleWindowValue;
  enabled: boolean;
  minIntervalMinutes: number;
  pauseAfterAdsMinutes: number;
} {
  const enabled = Boolean(schedule?.enabled);
  const timezone =
    typeof schedule?.timezone === 'string' && schedule.timezone
      ? schedule.timezone
      : 'Europe/Moscow';

  const windows = Array.isArray(schedule?.windows) ? schedule!.windows! : [];
  const daySet = new Set<Weekday>();
  const intervals: TimeInterval[] = [];

  for (const win of windows) {
    const rawDays = Array.isArray(win?.days_of_week) ? win.days_of_week : [];
    for (const d of rawDays) {
      daySet.add(isoDayToJs(Number(d)));
    }
    const from = typeof win?.from === 'string' && win.from ? win.from : '09:00';
    const to = typeof win?.to === 'string' && win.to ? win.to : '21:00';
    intervals.push({ start: from, end: to });
  }

  const days =
    daySet.size > 0
      ? ([...daySet].sort((a, b) => a - b) as Weekday[])
      : ([1, 2, 3, 4, 5] as Weekday[]);

  return {
    enabled,
    window: {
      days,
      intervals: intervals.length > 0 ? intervals : [{ start: '09:00', end: '21:00' }],
      timezone,
    },
    minIntervalMinutes:
      typeof schedule?.min_interval_minutes === 'number' ? schedule.min_interval_minutes : 0,
    pauseAfterAdsMinutes:
      typeof schedule?.pause_after_ads_minutes === 'number'
        ? schedule.pause_after_ads_minutes
        : 0,
  };
}
