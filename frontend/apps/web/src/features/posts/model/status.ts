import type { BadgeProps } from '@posts/ui';

export type PostStatus =
  | 'draft'
  | 'pending_approval'
  | 'approved'
  | 'scheduled'
  | 'publishing'
  | 'published'
  | 'partially_published'
  | 'failed'
  | 'paused'
  | 'archived'
  | 'deleted_from_network';

export type PublicationState =
  | 'pending'
  | 'queued'
  | 'in_progress'
  | 'succeeded'
  | 'failed_retryable'
  | 'failed_permanent'
  | 'cancelled'
  | 'waiting_for_channel';

const POST_STATUS_VARIANT: Record<PostStatus, NonNullable<BadgeProps['variant']>> = {
  draft: 'draft',
  pending_approval: 'review',
  approved: 'review',
  scheduled: 'scheduled',
  publishing: 'publishing',
  published: 'published',
  partially_published: 'publishing',
  failed: 'failed',
  paused: 'paused',
  archived: 'draft',
  deleted_from_network: 'paused',
};

const PUBLICATION_STATE_VARIANT: Record<PublicationState, NonNullable<BadgeProps['variant']>> = {
  pending: 'scheduled',
  queued: 'scheduled',
  in_progress: 'publishing',
  succeeded: 'published',
  failed_retryable: 'failed',
  failed_permanent: 'failed',
  cancelled: 'paused',
  waiting_for_channel: 'review',
};

export function postStatusVariant(status?: string | null): NonNullable<BadgeProps['variant']> {
  if (!status) return 'draft';
  return POST_STATUS_VARIANT[status as PostStatus] ?? 'draft';
}

export function publicationStateVariant(
  state?: string | null,
): NonNullable<BadgeProps['variant']> {
  if (!state) return 'draft';
  return PUBLICATION_STATE_VARIANT[state as PublicationState] ?? 'draft';
}

export function isRetryablePublication(state?: string | null): boolean {
  return state === 'failed_retryable' || state === 'failed_permanent';
}
