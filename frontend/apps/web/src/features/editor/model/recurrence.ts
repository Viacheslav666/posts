import type { Recurrence } from '@posts/api-client';

/**
 * vposter `post_day` → Posts `Recurrence` (TC-AD-02 / feature spec §3.2).
 */
export const VPOSTER_POST_DAY = [
  'oneraz',
  'everyday',
  'budday',
  'weekends',
  'everyNday',
  'everyweek',
  'every2week',
  'every3week',
  'everymonth',
  'every2month',
  'every3month',
  'every6month',
  'every12month',
] as const;

export type VposterPostDay = (typeof VPOSTER_POST_DAY)[number];

export type RecurrencePresetId =
  | 'once'
  | 'daily'
  | 'weekdays'
  | 'weekends'
  | 'every_n_days'
  | 'weekly'
  | 'every_2_weeks'
  | 'every_3_weeks'
  | 'monthly'
  | 'every_2_months'
  | 'every_3_months'
  | 'every_6_months'
  | 'every_12_months';

const PRESET_TO_RECURRENCE: Record<RecurrencePresetId, Recurrence> = {
  once: { freq: 'once', interval: 1 },
  daily: { freq: 'daily', interval: 1 },
  weekdays: { freq: 'weekdays', interval: 1, byWeekday: ['MO', 'TU', 'WE', 'TH', 'FR'] },
  weekends: { freq: 'weekends', interval: 1, byWeekday: ['SA', 'SU'] },
  every_n_days: { freq: 'every_n_days', interval: 2 },
  weekly: { freq: 'weekly', interval: 1 },
  every_2_weeks: { freq: 'every_n_weeks', interval: 2 },
  every_3_weeks: { freq: 'every_n_weeks', interval: 3 },
  monthly: { freq: 'monthly', interval: 1 },
  every_2_months: { freq: 'every_n_months', interval: 2 },
  every_3_months: { freq: 'every_n_months', interval: 3 },
  every_6_months: { freq: 'every_n_months', interval: 6 },
  every_12_months: { freq: 'every_n_months', interval: 12 },
};

const VPOSTER_TO_PRESET: Record<VposterPostDay, RecurrencePresetId> = {
  oneraz: 'once',
  everyday: 'daily',
  budday: 'weekdays',
  weekends: 'weekends',
  everyNday: 'every_n_days',
  everyweek: 'weekly',
  every2week: 'every_2_weeks',
  every3week: 'every_3_weeks',
  everymonth: 'monthly',
  every2month: 'every_2_months',
  every3month: 'every_3_months',
  every6month: 'every_6_months',
  every12month: 'every_12_months',
};

export function recurrenceFromPreset(
  preset: RecurrencePresetId,
  interval?: number,
  until?: string | null,
  times?: string[] | null,
): Recurrence {
  const base = { ...PRESET_TO_RECURRENCE[preset] };
  if (preset === 'every_n_days' && interval != null) {
    base.interval = Math.min(12, Math.max(2, interval));
  }
  if (until) {
    base.until = until;
  }
  if (times?.length) {
    base.times = [...times];
  }
  return base;
}

export function mapVposterPostDay(postDay: VposterPostDay, everyN?: number): Recurrence {
  const preset = VPOSTER_TO_PRESET[postDay];
  return recurrenceFromPreset(preset, everyN);
}

export function presetFromRecurrence(recurrence: Recurrence): RecurrencePresetId {
  const freq = recurrence.freq ?? 'once';
  const interval = recurrence.interval ?? 1;
  if (freq === 'once') return 'once';
  if (freq === 'daily' && interval === 1) return 'daily';
  if (freq === 'weekdays') return 'weekdays';
  if (freq === 'weekends') return 'weekends';
  if (freq === 'every_n_days' || (freq === 'daily' && interval > 1)) return 'every_n_days';
  if (freq === 'weekly' && interval === 1) return 'weekly';
  if (freq === 'every_n_weeks' || freq === 'weekly') {
    if (interval === 2) return 'every_2_weeks';
    if (interval === 3) return 'every_3_weeks';
    return 'weekly';
  }
  if (freq === 'monthly' && interval === 1) return 'monthly';
  if (freq === 'every_n_months' || freq === 'monthly') {
    if (interval === 2) return 'every_2_months';
    if (interval === 3) return 'every_3_months';
    if (interval === 6) return 'every_6_months';
    if (interval === 12) return 'every_12_months';
    return 'monthly';
  }
  return 'once';
}

/** Materialize occurrence timestamps for the next `days` (default 14). */
export function materializeOccurrences(
  startIso: string,
  recurrence: Recurrence,
  days = 14,
): string[] {
  const start = new Date(startIso);
  if (Number.isNaN(start.getTime())) {
    return [];
  }
  const untilLimit = new Date(start.getTime() + days * 24 * 60 * 60 * 1000);
  const seriesUntil = recurrence.until ? new Date(recurrence.until) : untilLimit;
  const hardStop = seriesUntil < untilLimit ? seriesUntil : untilLimit;
  const freq = recurrence.freq ?? 'once';
  const interval = Math.max(1, recurrence.interval ?? 1);
  const out: string[] = [start.toISOString()];

  if (freq === 'once') {
    return out;
  }

  let cursor = new Date(start);
  const max = 60;
  for (let i = 0; i < max; i += 1) {
    cursor = nextOccurrence(cursor, freq, interval, recurrence.byWeekday);
    if (cursor > hardStop) {
      break;
    }
    out.push(cursor.toISOString());
  }
  return out;
}

function nextOccurrence(
  from: Date,
  freq: NonNullable<Recurrence['freq']>,
  interval: number,
  byWeekday?: string[] | null,
): Date {
  const next = new Date(from);
  switch (freq) {
    case 'daily':
    case 'every_n_days':
      next.setDate(next.getDate() + interval);
      return next;
    case 'weekdays':
      do {
        next.setDate(next.getDate() + 1);
      } while (next.getDay() === 0 || next.getDay() === 6);
      return next;
    case 'weekends':
      do {
        next.setDate(next.getDate() + 1);
      } while (next.getDay() !== 0 && next.getDay() !== 6);
      return next;
    case 'weekly':
    case 'every_n_weeks':
      next.setDate(next.getDate() + 7 * interval);
      return next;
    case 'monthly':
    case 'every_n_months': {
      const day = next.getDate();
      next.setMonth(next.getMonth() + interval);
      // «31st of month» → last day of month when shorter
      if (next.getDate() < day) {
        next.setDate(0);
      }
      return next;
    }
    default:
      next.setDate(next.getDate() + 1);
      if (byWeekday?.length) {
        // keep advancing until weekday matches (best-effort)
        const map: Record<string, number> = {
          SU: 0,
          MO: 1,
          TU: 2,
          WE: 3,
          TH: 4,
          FR: 5,
          SA: 6,
        };
        const wanted = new Set(byWeekday.map((d) => map[d] ?? -1));
        while (!wanted.has(next.getDay())) {
          next.setDate(next.getDate() + 1);
        }
      }
      return next;
  }
}
