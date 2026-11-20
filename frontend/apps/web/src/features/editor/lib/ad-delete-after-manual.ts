export type AdAutoDeleteSnapshot = {
  enabled: boolean;
  displayPeriodDays: number;
};

/** Reset manual {@code deleteAfter} only when auto-delete is re-enabled or display period changes. */
export function shouldResetDeleteAfterManual(
  prev: AdAutoDeleteSnapshot,
  next: AdAutoDeleteSnapshot,
): boolean {
  return (!prev.enabled && next.enabled) || prev.displayPeriodDays !== next.displayPeriodDays;
}