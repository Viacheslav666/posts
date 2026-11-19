import type { CrosspostRule } from '@posts/api-client';

import type { ScheduleWindowValue } from '@/features/automations-common/model/schedule';

import {
  backendScheduleToWindow,
  scheduleWindowToBackend,
  type BackendSchedule,
} from './schedule-map';
import type { CrosspostRuleFormValues } from './schemas';
import type { AutoPauseReason, LongTextMode } from './types';

export type CrosspostFiltersConfig = {
  exclude_links: boolean;
  exclude_snippets: boolean;
  exclude_articles: boolean;
  skip_if_no_text: boolean;
  skip_if_no_images: boolean;
  exclude_video: boolean;
  exclude_clips: boolean;
  exclude_poll: boolean;
  exclude_geo: boolean;
  exclude_audio: boolean;
  exclude_ads: boolean;
  exclude_reposts: boolean;
  include_reposts: boolean;
  only_group_posts_in_tg_groups: boolean;
  keyword_include: string[];
  keyword_exclude: string[];
};

export type CrosspostTransformConfig = {
  remove_links: boolean;
  remove_hashtags: boolean;
  add_source_link: boolean;
  preserve_formatting: boolean;
  close_comments: boolean;
  publish_as_vk_repost: boolean;
  long_text_mode: LongTextMode;
  vk_video_as_link: boolean;
};

/** Typed config matching CrosspostConfigSupport defaults. */
export type CrosspostConfig = {
  filters: CrosspostFiltersConfig;
  transform: CrosspostTransformConfig;
  delay_minutes: number;
  include_posts_published_via_posts: boolean;
  include_suggest_published: boolean;
  circuit_max_items?: number;
  circuit_max_loop_skips?: number;
  schedule: BackendSchedule;
  auto_pause_reason?: AutoPauseReason | string;
  auto_paused_at?: string;
};

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {};
}

function asBool(value: unknown, fallback: boolean): boolean {
  if (typeof value === 'boolean') return value;
  if (value === 'true') return true;
  if (value === 'false') return false;
  return fallback;
}

function asNumber(value: unknown, fallback: number): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback;
}

function asStringList(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value.map((item) => String(item)).filter(Boolean);
}

export function defaultFilters(): CrosspostFiltersConfig {
  return {
    exclude_links: false,
    exclude_snippets: true,
    exclude_articles: true,
    skip_if_no_text: false,
    skip_if_no_images: false,
    exclude_video: false,
    exclude_clips: true,
    exclude_poll: true,
    exclude_geo: true,
    exclude_audio: true,
    exclude_ads: true,
    exclude_reposts: false,
    include_reposts: true,
    only_group_posts_in_tg_groups: true,
    keyword_include: [],
    keyword_exclude: [],
  };
}

export function defaultTransform(): CrosspostTransformConfig {
  return {
    remove_links: false,
    remove_hashtags: false,
    add_source_link: false,
    preserve_formatting: true,
    close_comments: false,
    publish_as_vk_repost: false,
    long_text_mode: 'split',
    vk_video_as_link: true,
  };
}

export function withConfigDefaults(raw: unknown): CrosspostConfig {
  const config = asRecord(raw);
  const filtersRaw = asRecord(config.filters);
  const transformRaw = asRecord(config.transform);
  const scheduleRaw = asRecord(config.schedule);
  const filters = defaultFilters();
  const transform = defaultTransform();

  for (const key of Object.keys(filters) as (keyof CrosspostFiltersConfig)[]) {
    if (key === 'keyword_include' || key === 'keyword_exclude') {
      filters[key] = asStringList(filtersRaw[key]);
    } else if (key in filtersRaw) {
      filters[key] = asBool(filtersRaw[key], filters[key]) as never;
    }
  }

  for (const key of Object.keys(transform) as (keyof CrosspostTransformConfig)[]) {
    if (key === 'long_text_mode') {
      transform.long_text_mode =
        transformRaw.long_text_mode === 'truncate_read_more' ? 'truncate_read_more' : 'split';
    } else if (key in transformRaw) {
      transform[key] = asBool(transformRaw[key], transform[key] as boolean) as never;
    }
  }

  const schedule: BackendSchedule = {
    enabled: asBool(scheduleRaw.enabled, false),
    timezone:
      typeof scheduleRaw.timezone === 'string' && scheduleRaw.timezone
        ? scheduleRaw.timezone
        : 'Europe/Moscow',
    days_of_week: (() => {
      const days = Array.isArray(scheduleRaw.days_of_week)
        ? scheduleRaw.days_of_week
            .map((d) => Number(d))
            .filter((n) => Number.isInteger(n) && n >= 1 && n <= 7)
        : [];
      return days.length > 0 ? days : [1, 2, 3, 4, 5, 6, 7];
    })(),
    hours_from: asNumber(scheduleRaw.hours_from, 0),
    hours_to: asNumber(scheduleRaw.hours_to, 24),
  };

  return {
    filters,
    transform,
    delay_minutes: asNumber(config.delay_minutes, 0),
    include_posts_published_via_posts: asBool(config.include_posts_published_via_posts, false),
    include_suggest_published: asBool(config.include_suggest_published, false),
    circuit_max_items: asNumber(config.circuit_max_items, 60),
    circuit_max_loop_skips: asNumber(config.circuit_max_loop_skips, 5),
    schedule,
    auto_pause_reason:
      typeof config.auto_pause_reason === 'string' ? config.auto_pause_reason : undefined,
    auto_paused_at: typeof config.auto_paused_at === 'string' ? config.auto_paused_at : undefined,
  };
}

export function defaultFormValues(sourceChannelId?: string): CrosspostRuleFormValues {
  const filters = defaultFilters();
  const transform = defaultTransform();
  return {
    name: '',
    sourceChannelId: sourceChannelId ?? '',
    targetChannelIds: [],
    keywordInclude: [],
    keywordExclude: [],
    excludeLinks: filters.exclude_links,
    excludeSnippets: filters.exclude_snippets,
    excludeArticles: filters.exclude_articles,
    skipIfNoText: filters.skip_if_no_text,
    skipIfNoImages: filters.skip_if_no_images,
    excludeVideo: filters.exclude_video,
    excludeClips: filters.exclude_clips,
    excludePoll: filters.exclude_poll,
    excludeGeo: filters.exclude_geo,
    excludeAudio: filters.exclude_audio,
    excludeAds: filters.exclude_ads,
    excludeReposts: filters.exclude_reposts,
    includeReposts: filters.include_reposts,
    onlyGroupPostsInTgGroups: filters.only_group_posts_in_tg_groups,
    includePostsPublishedViaPosts: false,
    includeSuggestPublished: false,
    removeLinks: transform.remove_links,
    removeHashtags: transform.remove_hashtags,
    addSourceLink: transform.add_source_link,
    preserveFormatting: transform.preserve_formatting,
    closeComments: transform.close_comments,
    publishAsVkRepost: transform.publish_as_vk_repost,
    longTextMode: transform.long_text_mode,
    vkVideoAsLink: transform.vk_video_as_link,
    delayMinutes: 0,
    scheduleEnabled: false,
    scheduleTimezone: 'Europe/Moscow',
    scheduleDays: [1, 2, 3, 4, 5, 6, 0],
    scheduleIntervals: [],
    activate: true,
  };
}

export function buildCrosspostConfig(values: CrosspostRuleFormValues): CrosspostConfig {
  const window: ScheduleWindowValue = {
    days: values.scheduleDays as ScheduleWindowValue['days'],
    intervals: values.scheduleIntervals,
    timezone: values.scheduleTimezone,
  };
  const schedule = scheduleWindowToBackend(window, values.scheduleEnabled);

  return {
    filters: {
      exclude_links: values.excludeLinks,
      exclude_snippets: values.excludeSnippets,
      exclude_articles: values.excludeArticles,
      skip_if_no_text: values.skipIfNoText,
      skip_if_no_images: values.skipIfNoImages,
      exclude_video: values.excludeVideo,
      exclude_clips: values.excludeClips,
      exclude_poll: values.excludePoll,
      exclude_geo: values.excludeGeo,
      exclude_audio: values.excludeAudio,
      exclude_ads: values.excludeAds,
      exclude_reposts: values.excludeReposts,
      include_reposts: values.includeReposts,
      only_group_posts_in_tg_groups: values.onlyGroupPostsInTgGroups,
      keyword_include: values.keywordInclude,
      keyword_exclude: values.keywordExclude,
    },
    transform: {
      remove_links: values.removeLinks,
      remove_hashtags: values.removeHashtags,
      add_source_link: values.addSourceLink,
      preserve_formatting: values.preserveFormatting,
      close_comments: values.closeComments,
      publish_as_vk_repost: values.publishAsVkRepost,
      long_text_mode: values.longTextMode,
      vk_video_as_link: values.vkVideoAsLink,
    },
    delay_minutes: values.delayMinutes,
    include_posts_published_via_posts: values.includePostsPublishedViaPosts,
    include_suggest_published: values.includeSuggestPublished,
    schedule,
  };
}

export function formValuesFromRule(rule: CrosspostRule): CrosspostRuleFormValues {
  const config = withConfigDefaults(rule.config);
  const { window, enabled } = backendScheduleToWindow(config.schedule);
  return {
    name: rule.name ?? '',
    sourceChannelId: rule.sourceChannelId ?? '',
    targetChannelIds: (rule.targets ?? [])
      .map((t) => t.targetChannelId)
      .filter((id): id is string => Boolean(id)),
    keywordInclude: config.filters.keyword_include,
    keywordExclude: config.filters.keyword_exclude,
    excludeLinks: config.filters.exclude_links,
    excludeSnippets: config.filters.exclude_snippets,
    excludeArticles: config.filters.exclude_articles,
    skipIfNoText: config.filters.skip_if_no_text,
    skipIfNoImages: config.filters.skip_if_no_images,
    excludeVideo: config.filters.exclude_video,
    excludeClips: config.filters.exclude_clips,
    excludePoll: config.filters.exclude_poll,
    excludeGeo: config.filters.exclude_geo,
    excludeAudio: config.filters.exclude_audio,
    excludeAds: config.filters.exclude_ads,
    excludeReposts: config.filters.exclude_reposts,
    includeReposts: config.filters.include_reposts,
    onlyGroupPostsInTgGroups: config.filters.only_group_posts_in_tg_groups,
    includePostsPublishedViaPosts: config.include_posts_published_via_posts,
    includeSuggestPublished: config.include_suggest_published,
    removeLinks: config.transform.remove_links,
    removeHashtags: config.transform.remove_hashtags,
    addSourceLink: config.transform.add_source_link,
    preserveFormatting: config.transform.preserve_formatting,
    closeComments: config.transform.close_comments,
    publishAsVkRepost: config.transform.publish_as_vk_repost,
    longTextMode: config.transform.long_text_mode,
    vkVideoAsLink: config.transform.vk_video_as_link,
    delayMinutes: config.delay_minutes,
    scheduleEnabled: enabled,
    scheduleTimezone: window.timezone,
    scheduleDays: window.days,
    scheduleIntervals: window.intervals,
    activate: rule.status === 'active',
  };
}

export function readAutoPauseReason(rule: CrosspostRule): AutoPauseReason | string | undefined {
  const config = withConfigDefaults(rule.config);
  return config.auto_pause_reason;
}

/** Config as OpenAPI `additionalProperties` blob. */
export function configAsApiBlob(config: CrosspostConfig): Record<string, never> {
  return config as unknown as Record<string, never>;
}
