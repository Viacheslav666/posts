export type IngestRuleType = 'grabber' | 'rss' | 'website';
export type PublishMode = 'moderate' | 'auto' | 'draft';
export type TargetMode = 'wall_now' | 'vk_postponed_post' | 'wall_repost' | 'vk_postponed_repost';

export const TARGET_MODES: TargetMode[] = [
  'wall_now',
  'vk_postponed_post',
  'wall_repost',
  'vk_postponed_repost',
];

export function isPostponedTarget(mode: TargetMode | undefined): boolean {
  return mode === 'vk_postponed_post' || mode === 'vk_postponed_repost';
}

export function isRepostTarget(mode: TargetMode | undefined): boolean {
  return mode === 'wall_repost' || mode === 'vk_postponed_repost';
}
export type ScheduleMode = 'immediate' | 'delay' | 'slot';
export type RuleStatus = 'draft' | 'active' | 'paused' | 'auto_paused' | 'archived';
export type ItemStatus =
  | 'discovered'
  | 'filtered_out'
  | 'suppressed_dedup'
  | 'pending_moderation'
  | 'rejected'
  | 'approved'
  | 'scheduled'
  | 'published'
  | 'publish_failed'
  | 'failed_validation'
  | 'failed_ingest';

export const SCAN_INTERVALS = [15, 30, 60, 120, 180, 360, 720, 1440] as const;

/** BE-27: max 20 sources per rule. */
export const MAX_SOURCES_PER_RULE = 20;

export const DEFAULT_ATTRIBUTION_TEMPLATE = 'Источник: {source_name} — {source_url}';

/** ISO day numbers Monday=1 … Sunday=7 (matches backend WorkingWindow). */
export const DAYS_OF_WEEK = [1, 2, 3, 4, 5, 6, 7] as const;

export const SKIP_IF_NO_ATTACHMENT_FLAGS = [
  'skipIfNoMusic',
  'skipIfNoPhoto',
  'skipIfNoVideo',
  'skipIfNoClip',
  'skipIfNoProduct',
  'skipIfNoFile',
  'skipIfNoPoll',
  'skipIfNoArticle',
] as const;

export const SKIP_IF_HAS_ATTACHMENT_FLAGS = [
  'skipIfHasArticle',
  'skipIfHasVideo',
  'skipIfHasClip',
  'skipIfHasPhoto',
  'skipIfHasMusic',
  'skipIfHasFile',
  'skipIfHasProduct',
  'skipIfHasPoll',
] as const;

export const STRIP_FLAGS = [
  'stripPhoto',
  'stripVideo',
  'stripClip',
  'stripMusic',
  'stripPlaylist',
  'stripFile',
  'stripPoll',
  'stripAlbum',
  'stripProduct',
  'stripMarketAlbum',
  'stripWiki',
  'stripArticle',
  'stripAllLinks',
  'stripHashtags',
  'stripMentions',
  'stripAllText',
  'stripPhones',
] as const;

export const TEXT_MACROS = [
  '[name_of_the_source]',
  '[id_of_the_source]',
  '[post_id_of_the_source]',
  '[rand]вариант1|вариант2[/rand]',
  '[keyText]#юмор|прикол,приколы[/keyText]',
  '[keyTextEntry]скидка,акция[/keyTextEntry]',
  '[keyTextEntryNotFound]реклама,без рекламы[/keyTextEntryNotFound]',
] as const;

/** True only when the public flag is exactly "true". The server flag defaults to off. */
export function isGrabberActionButtonEnabled(): boolean {
  return process.env.NEXT_PUBLIC_GRABBER_ACTION_BUTTON_ENABLED === 'true';
}

export function isWebsiteSourceEnabled(): boolean {
  if (process.env.NEXT_PUBLIC_GRABBER_WEBSITE_ENABLED === 'true') {
    return true;
  }
  if (process.env.NEXT_PUBLIC_GRABBER_WEBSITE_ENABLED === 'false') {
    return false;
  }
  // MSW / local default: BE-17b is on main; keep website selectable for demos.
  return process.env.NEXT_PUBLIC_API_MOCKING === 'true' || process.env.NODE_ENV === 'development';
}
