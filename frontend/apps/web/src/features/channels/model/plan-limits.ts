/** Channel quotas by plan code (PRD MVP / pricing). */
export const PLAN_CHANNEL_LIMITS: Record<string, number> = {
  free: 3,
  start: 10,
  pro: 30,
  agency: 100,
};

export function channelLimitForPlan(planCode: string | null | undefined): number {
  if (!planCode) {
    return PLAN_CHANNEL_LIMITS.free;
  }
  return PLAN_CHANNEL_LIMITS[planCode] ?? PLAN_CHANNEL_LIMITS.free;
}
