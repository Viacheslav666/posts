import type { Plan } from '@posts/api-client';

import { getApiClient, unwrapApi } from '@/shared/api-client';

/** BE-12: catalog of plans (limits). Balance top-up / daily charge — FE-10b + BE-31. */
export async function listBillingPlans(): Promise<Plan[]> {
  const client = getApiClient();
  return unwrapApi(await client.GET('/api/v1/billing/plans'));
}
