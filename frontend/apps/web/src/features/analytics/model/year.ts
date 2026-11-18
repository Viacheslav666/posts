const MIN_YEAR = 2000;
const MAX_YEAR = 2100;

export function currentAnalyticsYear(now = new Date()): number {
  return now.getFullYear();
}

export function parseAnalyticsYear(
  raw: string | null | undefined,
  fallback = currentAnalyticsYear(),
): number {
  if (raw == null || raw === '') {
    return fallback;
  }
  const year = Number.parseInt(raw, 10);
  if (!Number.isFinite(year) || year < MIN_YEAR || year > MAX_YEAR) {
    return fallback;
  }
  return year;
}

export function analyticsYearOptions(now = new Date()): number[] {
  const current = currentAnalyticsYear(now);
  const years: number[] = [];
  for (let year = current; year >= current - 5; year -= 1) {
    years.push(year);
  }
  return years;
}

export { MIN_YEAR, MAX_YEAR };
