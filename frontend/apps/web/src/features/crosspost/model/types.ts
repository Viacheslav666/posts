import type { CrosspostItemStatus } from '@posts/api-client';

export type RuleStatus = 'draft' | 'active' | 'paused' | 'auto_paused';

export type ItemStatus = CrosspostItemStatus;

export type AutoPauseReason = 'circuit_volume' | 'circuit_loop_skips';

export type LongTextMode = 'split' | 'truncate_read_more';

export type SourceNetwork = 'vk' | 'tg' | 'max';

export type TargetNetwork = 'vk' | 'ok' | 'tg' | 'max';

export const SOURCE_NETWORKS: SourceNetwork[] = ['vk', 'tg', 'max'];

export const TARGET_NETWORKS: TargetNetwork[] = ['vk', 'ok', 'tg', 'max'];

export const ITEM_STATUSES: ItemStatus[] = [
  'received',
  'filtered_out',
  'skipped_own',
  'skipped_loop',
  'skipped_duplicate',
  'skipped_unsupported_media',
  'queued',
  'published',
  'partially_published',
  'failed',
];

export const FORM_STEPS = [
  'source',
  'targets',
  'filters',
  'transform',
  'schedule',
  'review',
] as const;

export type FormStep = (typeof FORM_STEPS)[number];

/** ISO day numbers Monday=1 … Sunday=7 (matches backend schedule.days_of_week). */
export const ISO_DAYS_OF_WEEK = [1, 2, 3, 4, 5, 6, 7] as const;
