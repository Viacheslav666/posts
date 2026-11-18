/**
 * Recurrence.times[] helpers — HH:mm in post timezone (OpenAPI pattern).
 */

export const RECURRENCE_TIME_PATTERN = /^([01]\d|2[0-3]):[0-5]\d$/;

/** Soft UI limit (backend has no maxItems; keep materialization sane). */
export const RECURRENCE_TIMES_MAX = 12;

export type RecurrenceTimesValidation = {
  ok: boolean;
  duplicates: string[];
  invalid: string[];
  overLimit: boolean;
};

export function isValidRecurrenceTime(value: string): boolean {
  return RECURRENCE_TIME_PATTERN.test(value.trim());
}

export function normalizeRecurrenceTimes(times: string[] | null | undefined): string[] {
  if (!times?.length) return [];
  const seen = new Set<string>();
  const out: string[] = [];
  for (const raw of times) {
    const value = (raw ?? '').trim();
    if (!value || !isValidRecurrenceTime(value) || seen.has(value)) continue;
    seen.add(value);
    out.push(value);
    if (out.length >= RECURRENCE_TIMES_MAX) break;
  }
  return out;
}

export function validateRecurrenceTimes(times: string[] | null | undefined): RecurrenceTimesValidation {
  const list = (times ?? []).map((t) => (t ?? '').trim()).filter(Boolean);
  const invalid = list.filter((t) => !isValidRecurrenceTime(t));
  const seen = new Set<string>();
  const duplicates: string[] = [];
  for (const t of list) {
    if (!isValidRecurrenceTime(t)) continue;
    if (seen.has(t)) {
      if (!duplicates.includes(t)) duplicates.push(t);
    } else {
      seen.add(t);
    }
  }
  const overLimit = list.length > RECURRENCE_TIMES_MAX;
  return {
    ok: invalid.length === 0 && duplicates.length === 0 && !overLimit,
    duplicates,
    invalid,
    overLimit,
  };
}

export function addRecurrenceTime(times: string[] | null | undefined, next: string): string[] {
  const current = [...(times ?? [])];
  const value = next.trim() || '09:00';
  if (current.length >= RECURRENCE_TIMES_MAX) return current;
  if (current.includes(value)) return current;
  return [...current, value];
}

export function removeRecurrenceTime(times: string[] | null | undefined, index: number): string[] {
  return (times ?? []).filter((_, i) => i !== index);
}

export function timeFromIsoInTimezone(iso: string, timeZone: string): string {
  try {
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return '09:00';
    const parts = new Intl.DateTimeFormat('en-GB', {
      timeZone,
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    }).formatToParts(d);
    const hour = parts.find((p) => p.type === 'hour')?.value ?? '09';
    const minute = parts.find((p) => p.type === 'minute')?.value ?? '00';
    return `${hour.padStart(2, '0')}:${minute.padStart(2, '0')}`;
  } catch {
    return '09:00';
  }
}
