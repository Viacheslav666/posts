export type SuggestMode = 'auto' | 'manual' | 'hybrid';

export type SettingsStatus = 'draft' | 'active' | 'paused' | 'auto_paused';

export type ItemStatus =
  | 'filtered_out'
  | 'pending_moderation'
  | 'approved'
  | 'scheduled'
  | 'publishing'
  | 'published'
  | 'rejected'
  | 'deleted_remote'
  | 'removed_externally'
  | 'failed';

export type RejectPolicy = 'delete' | 'keep' | 'delete_after';

export type PublishEditMode = 'wall_post_edit' | 'fallback_new_post';

export type KeywordMatchApi = 'contains' | 'whole_word';

/** Soft plan limit for connected suggest channels (UI CTA; not a hard API metric yet). */
export const SUGGEST_SOFT_CHANNEL_LIMIT = 20;

/** Cursor page size. Sync-last-100 imports more than one page, so lists must follow `nextCursor`. */
export const SUGGEST_PAGE_SIZE = 50;

export const QUEUE_FILTERS: Array<ItemStatus | 'all'> = [
  'pending_moderation',
  'all',
  'approved',
  'scheduled',
  'published',
  'rejected',
  'filtered_out',
  'failed',
];

export const HISTORY_FILTERS: Array<ItemStatus | 'all'> = [
  'published',
  'rejected',
  'deleted_remote',
  'removed_externally',
  'failed',
  'all',
];

/** Terminal outcomes included when the history filter is «all». */
export const HISTORY_STATUSES: ItemStatus[] = [
  'published',
  'rejected',
  'deleted_remote',
  'removed_externally',
  'failed',
];

export const FORBID_ATTACHMENT_TYPES = ['photo', 'video', 'audio', 'doc', 'link', 'poll'] as const;

/** Reason codes emitted by BE SuggestFilterEngine (for i18n + chips). */
export const SUGGEST_REASON_CODES = [
  'stop_words',
  'keyword_exclude',
  'keyword_include',
  'require_text',
  'require_image',
  'forbid_attachments',
  'require_messages_allowed',
  'forbid_closed_profile',
  'author_blacklist',
  'ads_pause',
  'duplicate_content',
  'near_duplicate',
  'author_limit_hour',
  'author_limit_day',
  'author_limit_week',
  'global_limit_hour',
  'global_limit_day',
  'global_limit_week',
] as const;
