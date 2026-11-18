import type {
  AdRevenue,
  PostsAnalyticsMonth,
  PostsAnalyticsResponse,
  TopPublicationError,
} from '@posts/api-client';

import { formatRubAmount } from '@/features/billing/model/money';

export const ANALYTICS_NETWORKS = ['vk', 'ok', 'tg', 'max'] as const;
export type AnalyticsNetwork = (typeof ANALYTICS_NETWORKS)[number];

export const MONTH_LABELS_RU = [
  'Янв',
  'Фев',
  'Мар',
  'Апр',
  'Май',
  'Июн',
  'Июл',
  'Авг',
  'Сен',
  'Окт',
  'Ноя',
  'Дек',
] as const;

export const MONTH_FULL_LABELS_RU = [
  'Январь',
  'Февраль',
  'Март',
  'Апрель',
  'Май',
  'Июнь',
  'Июль',
  'Август',
  'Сентябрь',
  'Октябрь',
  'Ноябрь',
  'Декабрь',
] as const;

export type VolumePoint = {
  month: number;
  label: string;
  regular: number;
  ad: number;
  repost: number;
};

export type NetworkOutcomePoint = {
  month: number;
  label: string;
} & Record<`${AnalyticsNetwork}_succeeded` | `${AnalyticsNetwork}_failed`, number>;

export type AggregatedTopError = {
  key: string;
  errorCode: string;
  errorMessage?: string;
  network?: string;
  count: number;
  postId?: string;
  publicationId?: string;
  retryPath?: string;
};

export type RevenueTotals = AdRevenue;

const EMPTY_REVENUE: AdRevenue = {
  amountWithVat: 0,
  amountWithoutVat: 0,
  vatAmount: 0,
  fee3Pct: 0,
};

export function ensureTwelveMonths(months: PostsAnalyticsMonth[] | undefined): PostsAnalyticsMonth[] {
  const byMonth = new Map((months ?? []).map((item) => [item.month, item]));
  return Array.from({ length: 12 }, (_, index) => {
    const month = index + 1;
    return (
      byMonth.get(month) ?? {
        month,
        regularCount: 0,
        adCount: 0,
        repostCount: 0,
        networks: [],
        topErrors: [],
        revenue: { ...EMPTY_REVENUE },
      }
    );
  });
}

export function isAnalyticsYearEmpty(data: PostsAnalyticsResponse | undefined): boolean {
  if (!data?.months?.length) {
    return true;
  }
  return ensureTwelveMonths(data.months).every(
    (month) =>
      Number(month.regularCount ?? 0) + Number(month.adCount ?? 0) + Number(month.repostCount ?? 0) ===
      0,
  );
}

export function toVolumeSeries(months: PostsAnalyticsMonth[]): VolumePoint[] {
  return ensureTwelveMonths(months).map((month) => ({
    month: month.month!,
    label: MONTH_LABELS_RU[month.month! - 1]!,
    regular: Number(month.regularCount ?? 0),
    ad: Number(month.adCount ?? 0),
    repost: Number(month.repostCount ?? 0),
  }));
}

function emptyNetworkOutcome(month: number): NetworkOutcomePoint {
  const point = {
    month,
    label: MONTH_LABELS_RU[month - 1]!,
  } as NetworkOutcomePoint;
  for (const network of ANALYTICS_NETWORKS) {
    point[`${network}_succeeded`] = 0;
    point[`${network}_failed`] = 0;
  }
  return point;
}

export function toNetworkOutcomeSeries(months: PostsAnalyticsMonth[]): NetworkOutcomePoint[] {
  return ensureTwelveMonths(months).map((month) => {
    const point = emptyNetworkOutcome(month.month!);
    for (const stats of month.networks ?? []) {
      const network = stats.network as AnalyticsNetwork | undefined;
      if (!network || !ANALYTICS_NETWORKS.includes(network)) {
        continue;
      }
      point[`${network}_succeeded`] = Number(stats.succeeded ?? 0);
      point[`${network}_failed`] = Number(stats.failed ?? 0);
    }
    return point;
  });
}

export function sumRevenue(months: PostsAnalyticsMonth[]): RevenueTotals {
  return ensureTwelveMonths(months).reduce<RevenueTotals>(
    (acc, month) => ({
      amountWithVat: acc.amountWithVat + Number(month.revenue?.amountWithVat ?? 0),
      amountWithoutVat: acc.amountWithoutVat + Number(month.revenue?.amountWithoutVat ?? 0),
      vatAmount: acc.vatAmount + Number(month.revenue?.vatAmount ?? 0),
      fee3Pct: acc.fee3Pct + Number(month.revenue?.fee3Pct ?? 0),
    }),
    { ...EMPTY_REVENUE },
  );
}

export function formatAnalyticsRub(amount: number | undefined | null): string {
  if (amount == null || Number.isNaN(Number(amount))) {
    return formatRubAmount(0);
  }
  return formatRubAmount(Number(amount));
}

export function aggregateTopErrors(months: PostsAnalyticsMonth[]): AggregatedTopError[] {
  const map = new Map<string, AggregatedTopError>();
  for (const month of ensureTwelveMonths(months)) {
    for (const error of month.topErrors ?? []) {
      mergeTopError(map, error);
    }
  }
  return Array.from(map.values()).sort((a, b) => b.count - a.count || a.errorCode.localeCompare(b.errorCode));
}

function mergeTopError(map: Map<string, AggregatedTopError>, error: TopPublicationError) {
  const network = error.network ?? '';
  const key = `${error.errorCode}|${network}`;
  const existing = map.get(key);
  const count = Number(error.count ?? 0);
  if (!existing) {
    map.set(key, {
      key,
      errorCode: error.errorCode,
      errorMessage: error.errorMessage,
      network: error.network,
      count,
      postId: error.postId,
      publicationId: error.publicationId,
      retryPath: error.retryPath,
    });
    return;
  }
  existing.count += count;
  if (!existing.errorMessage && error.errorMessage) {
    existing.errorMessage = error.errorMessage;
  }
  if (!existing.publicationId && error.publicationId) {
    existing.publicationId = error.publicationId;
  }
  if (!existing.postId && error.postId) {
    existing.postId = error.postId;
  }
  if (!existing.retryPath && error.retryPath) {
    existing.retryPath = error.retryPath;
  }
}

/** Extract publication UUID from `/api/v1/publications/{id}/retry` when id is missing. */
export function publicationIdFromRetryPath(retryPath?: string | null): string | undefined {
  if (!retryPath) {
    return undefined;
  }
  const match = /\/publications\/([0-9a-fA-F-]{36})\/retry/.exec(retryPath);
  return match?.[1];
}

export function resolveRetryPublicationId(error: AggregatedTopError): string | undefined {
  return error.publicationId ?? publicationIdFromRetryPath(error.retryPath);
}

export function postsListHrefForError(
  workspaceId: string,
  error: AggregatedTopError,
): string {
  if (error.postId) {
    return `/app/${workspaceId}/posts/${error.postId}`;
  }
  const params = new URLSearchParams({ status: 'failed' });
  if (error.network) {
    params.set('network', error.network);
  }
  if (error.errorCode) {
    params.set('q', error.errorCode);
  }
  return `/app/${workspaceId}/posts?${params.toString()}`;
}
