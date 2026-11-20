import type { AdMarking } from '@posts/api-client';

/** ORD default display period (days) — keep in sync with {@code AdMarkingDefaults.DEFAULT_DISPLAY_PERIOD_DAYS}. */
export const AD_DEFAULT_DISPLAY_PERIOD_DAYS = 30;

const MS_PER_DAY = 86_400_000;

export function displayPeriodDays(ad: AdMarking): number {
  const days = ad.displayPeriodDays ?? AD_DEFAULT_DISPLAY_PERIOD_DAYS;
  return days > 0 ? days : AD_DEFAULT_DISPLAY_PERIOD_DAYS;
}

export function defaultAdDeleteAfterIso(publishAtIso: string, ad: AdMarking): string | undefined {
  if (ad.autoDeleteAfterPeriod === false) {
    return undefined;
  }
  const publishAt = new Date(publishAtIso);
  if (Number.isNaN(publishAt.getTime())) {
    return undefined;
  }
  return new Date(publishAt.getTime() + displayPeriodDays(ad) * MS_PER_DAY).toISOString();
}