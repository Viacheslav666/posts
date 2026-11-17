export type PlanId = 'free' | 'start' | 'pro' | 'agency';

export type Plan = {
  id: PlanId;
  nameKey: string;
  monthlyPrice: number;
  channels: string;
  seats: string;
  posts: string;
  aiCredits: string;
  highlighted?: boolean;
  noteKey?: string;
};

/** Prices and limits from docs/product/PRD-MVP.md §7 */
export const PLANS: Plan[] = [
  {
    id: 'free',
    nameKey: 'free',
    monthlyPrice: 0,
    channels: '3',
    seats: '1',
    posts: '30/мес',
    aiCredits: '20',
  },
  {
    id: 'start',
    nameKey: 'start',
    monthlyPrice: 390,
    channels: '10',
    seats: '1',
    posts: '∞',
    aiCredits: '200',
  },
  {
    id: 'pro',
    nameKey: 'pro',
    monthlyPrice: 990,
    channels: '30',
    seats: '3',
    posts: '∞',
    aiCredits: '1 000',
    highlighted: true,
  },
  {
    id: 'agency',
    nameKey: 'agency',
    monthlyPrice: 2990,
    channels: '100',
    seats: '10',
    posts: '∞',
    aiCredits: '5 000',
    noteKey: 'agencyNote',
  },
];

export const YEARLY_DISCOUNT = 0.2;

export function priceForPeriod(monthlyPrice: number, yearly: boolean): number {
  if (monthlyPrice === 0) {
    return 0;
  }
  if (!yearly) {
    return monthlyPrice;
  }
  return Math.round(monthlyPrice * 12 * (1 - YEARLY_DISCOUNT));
}

export function formatRub(value: number): string {
  return new Intl.NumberFormat('ru-RU').format(value);
}
