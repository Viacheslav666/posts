import type { Plan } from '@posts/api-client';

export type PlanLimits = {
  channels: number | null;
  seats: number | null;
  posts_month: number | null;
  ai_credits: number | null;
  grabber_rules: number | null;
  stories_month: number | null;
};

const PLAN_DEFS: Array<{
  code: string;
  name: string;
  priceMonth: number;
  priceYear: number;
  limits: PlanLimits;
}> = [
  {
    code: 'free',
    name: 'Free',
    priceMonth: 0,
    priceYear: 0,
    limits: {
      channels: 3,
      seats: 1,
      posts_month: 30,
      ai_credits: 20,
      grabber_rules: 0,
      stories_month: 0,
    },
  },
  {
    code: 'start',
    name: 'Старт',
    priceMonth: 390,
    priceYear: 3744,
    limits: {
      channels: 10,
      seats: 1,
      posts_month: null,
      ai_credits: 200,
      grabber_rules: 1,
      stories_month: 30,
    },
  },
  {
    code: 'pro',
    name: 'Про',
    priceMonth: 990,
    priceYear: 9504,
    limits: {
      channels: 30,
      seats: 3,
      posts_month: null,
      ai_credits: 1000,
      grabber_rules: 5,
      stories_month: 100,
    },
  },
  {
    code: 'agency',
    name: 'Агентство',
    priceMonth: 2990,
    priceYear: 28704,
    limits: {
      channels: 100,
      seats: 10,
      posts_month: null,
      ai_credits: 5000,
      grabber_rules: 20,
      stories_month: 500,
    },
  },
];

function nextId(suffix: string): string {
  return `019a0000-bill-7000-8000-${suffix.padStart(12, '0')}`;
}

type BillingState = {
  plans: Plan[];
};

function createInitialState(): BillingState {
  return {
    plans: PLAN_DEFS.map((def, index) => ({
      id: nextId(String(index + 1)),
      code: def.code,
      name: def.name,
      priceMonth: def.priceMonth,
      priceYear: def.priceYear,
      limits: { ...def.limits } as unknown as Plan['limits'],
    })),
  };
}

let state = createInitialState();

export function resetBillingStore(): void {
  state = createInitialState();
}

export function listPlans(): Plan[] {
  return state.plans.map((plan) => ({
    ...plan,
    limits: { ...(plan.limits ?? {}) } as Plan['limits'],
  }));
}

export function getPlanByCode(code: string): Plan | undefined {
  return state.plans.find((plan) => plan.code === code);
}

export function getPlanLimits(code: string): PlanLimits {
  const found = PLAN_DEFS.find((plan) => plan.code === code);
  return found?.limits ?? PLAN_DEFS[0]!.limits;
}
