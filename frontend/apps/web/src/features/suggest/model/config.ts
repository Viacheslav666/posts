import type { SuggestSettings } from '@posts/api-client';

import type { KeywordMatchMode } from '@/features/automations-common/model/keywords';
import type { ScheduleWindowValue } from '@/features/automations-common/model/schedule';

import {
  scheduleWindowToSuggest,
  suggestScheduleToWindow,
  type SuggestBackendSchedule,
} from './schedule-map';
import type { SuggestSettingsFormValues } from './schemas';
import type { KeywordMatchApi, PublishEditMode, RejectPolicy, SuggestMode } from './types';

/**
 * Typed suggest_settings.config jsonb (docs/api/schemas/suggest-config.schema.json).
 *
 * OpenAPI gap: no max text length / link-domain whitelist fields exist in the
 * published schema or SuggestConfigSupport — do not invent them on the client.
 */
export type SuggestConfig = {
  config_version: 1;
  filters: {
    keyword_include: string[];
    keyword_exclude: string[];
    stop_words: string[];
    keyword_match: KeywordMatchApi;
    require_text: boolean;
    require_image: boolean;
    forbid_attachments: string[];
    require_messages_allowed: boolean;
    forbid_closed_profile: boolean;
    author_blacklist: string[];
    author_whitelist: string[];
  };
  limits: {
    per_author: { hour: number; day: number; week: number };
    global: { hour: number; day: number; week: number };
  };
  schedule: SuggestBackendSchedule;
  dedup: {
    enabled: boolean;
    lookback_days: number;
    similarity_threshold: number;
  };
  reject_policy: RejectPolicy;
  delete_after_hours: number;
  notify_author: boolean;
  first_comment: { enabled: boolean; text: string };
  signed: boolean;
  publish_edit_mode: PublishEditMode;
  enrichment: {
    hashtags: string[];
    append_text: string;
    media_asset_ids: string[];
    keyword_signatures: string[];
  };
  ads_pause: {
    enabled: boolean;
    detect_marked_as_ads: boolean;
    detect_erid: boolean;
    pause_after_ads_minutes: number;
  };
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

export function matchModeToApi(mode: KeywordMatchMode): KeywordMatchApi {
  return mode === 'whole_word' ? 'whole_word' : 'contains';
}

export function matchModeFromApi(mode: unknown): KeywordMatchMode {
  return mode === 'whole_word' ? 'whole_word' : 'substring';
}

export function withConfigDefaults(raw: unknown): SuggestConfig {
  const config = asRecord(raw);
  const filters = asRecord(config.filters);
  const limits = asRecord(config.limits);
  const perAuthor = asRecord(limits.per_author);
  const global = asRecord(limits.global);
  const scheduleRaw = asRecord(config.schedule);
  const dedup = asRecord(config.dedup);
  const firstComment = asRecord(config.first_comment);
  const enrichment = asRecord(config.enrichment);
  const adsPause = asRecord(config.ads_pause);

  const mapped = suggestScheduleToWindow({
    enabled: asBool(scheduleRaw.enabled, false),
    timezone:
      typeof scheduleRaw.timezone === 'string' && scheduleRaw.timezone
        ? scheduleRaw.timezone
        : 'Europe/Moscow',
    windows: Array.isArray(scheduleRaw.windows)
      ? (scheduleRaw.windows as SuggestBackendSchedule['windows'])
      : undefined,
    min_interval_minutes: asNumber(scheduleRaw.min_interval_minutes, 0),
    pause_after_ads_minutes: asNumber(scheduleRaw.pause_after_ads_minutes, 0),
  });

  const reject =
    config.reject_policy === 'keep' || config.reject_policy === 'delete_after'
      ? config.reject_policy
      : 'delete';
  const editMode =
    config.publish_edit_mode === 'fallback_new_post' ? 'fallback_new_post' : 'wall_post_edit';

  return {
    config_version: 1,
    filters: {
      keyword_include: asStringList(filters.keyword_include),
      keyword_exclude: asStringList(filters.keyword_exclude),
      stop_words: asStringList(filters.stop_words),
      keyword_match: filters.keyword_match === 'whole_word' ? 'whole_word' : 'contains',
      require_text: asBool(filters.require_text, false),
      require_image: asBool(filters.require_image, false),
      forbid_attachments: asStringList(filters.forbid_attachments),
      require_messages_allowed: asBool(filters.require_messages_allowed, false),
      forbid_closed_profile: asBool(filters.forbid_closed_profile, false),
      author_blacklist: asStringList(filters.author_blacklist),
      author_whitelist: asStringList(filters.author_whitelist),
    },
    limits: {
      per_author: {
        hour: asNumber(perAuthor.hour, 0),
        day: asNumber(perAuthor.day, 0),
        week: asNumber(perAuthor.week, 0),
      },
      global: {
        hour: asNumber(global.hour, 0),
        day: asNumber(global.day, 0),
        week: asNumber(global.week, 0),
      },
    },
    schedule: scheduleWindowToSuggest(mapped.window, mapped.enabled, {
      minIntervalMinutes: mapped.minIntervalMinutes,
      pauseAfterAdsMinutes: mapped.pauseAfterAdsMinutes,
    }),
    dedup: {
      enabled: asBool(dedup.enabled, true),
      lookback_days: asNumber(dedup.lookback_days, 7),
      similarity_threshold: asNumber(dedup.similarity_threshold, 0.8),
    },
    reject_policy: reject,
    delete_after_hours: asNumber(config.delete_after_hours, 24),
    notify_author: asBool(config.notify_author, true),
    first_comment: {
      enabled: asBool(firstComment.enabled, false),
      text: typeof firstComment.text === 'string' ? firstComment.text : '',
    },
    signed: asBool(config.signed, true),
    publish_edit_mode: editMode,
    enrichment: {
      hashtags: asStringList(enrichment.hashtags),
      append_text: typeof enrichment.append_text === 'string' ? enrichment.append_text : '',
      media_asset_ids: asStringList(enrichment.media_asset_ids),
      keyword_signatures: asStringList(enrichment.keyword_signatures),
    },
    ads_pause: {
      enabled: asBool(adsPause.enabled, true),
      detect_marked_as_ads: asBool(adsPause.detect_marked_as_ads, true),
      detect_erid: asBool(adsPause.detect_erid, true),
      pause_after_ads_minutes: asNumber(adsPause.pause_after_ads_minutes, 60),
    },
  };
}

export function defaultFormValues(channelId?: string): SuggestSettingsFormValues {
  const defaults = withConfigDefaults({});
  const schedule = suggestScheduleToWindow(defaults.schedule);
  return {
    channelId: channelId ?? '',
    mode: 'manual',
    activate: true,
    keywordInclude: [],
    keywordExclude: [],
    stopWords: [],
    keywordMatchMode: 'substring',
    requireText: false,
    requireImage: false,
    forbidAttachments: [],
    requireMessagesAllowed: false,
    forbidClosedProfile: false,
    authorBlacklist: [],
    authorWhitelist: [],
    perAuthorHour: 0,
    perAuthorDay: 0,
    perAuthorWeek: 0,
    globalHour: 0,
    globalDay: 0,
    globalWeek: 0,
    scheduleEnabled: false,
    scheduleTimezone: schedule.window.timezone,
    scheduleDays: schedule.window.days,
    scheduleIntervals: schedule.window.intervals,
    minIntervalMinutes: 0,
    schedulePauseAfterAdsMinutes: 0,
    dedupEnabled: true,
    dedupLookbackDays: 7,
    dedupSimilarityThreshold: 0.8,
    rejectPolicy: 'delete',
    deleteAfterHours: 24,
    notifyAuthor: true,
    firstCommentEnabled: false,
    firstCommentText: '',
    signed: true,
    publishEditMode: 'wall_post_edit',
    enrichmentHashtags: [],
    enrichmentAppendText: '',
    keywordSignatures: [],
    adsPauseEnabled: true,
    adsDetectMarkedAsAds: true,
    adsDetectErid: true,
    adsPauseAfterMinutes: 60,
  };
}

export function buildSuggestConfig(
  values: SuggestSettingsFormValues,
  preserved?: { mediaAssetIds?: string[] },
): SuggestConfig {
  const windowValue: ScheduleWindowValue = {
    days: values.scheduleDays as ScheduleWindowValue['days'],
    intervals: values.scheduleIntervals,
    timezone: values.scheduleTimezone,
  };

  return {
    config_version: 1,
    filters: {
      keyword_include: values.keywordInclude,
      keyword_exclude: values.keywordExclude,
      stop_words: values.stopWords,
      keyword_match: matchModeToApi(values.keywordMatchMode),
      require_text: values.requireText,
      require_image: values.requireImage,
      forbid_attachments: values.forbidAttachments,
      require_messages_allowed: values.requireMessagesAllowed,
      forbid_closed_profile: values.forbidClosedProfile,
      author_blacklist: values.authorBlacklist,
      author_whitelist: values.authorWhitelist,
    },
    limits: {
      per_author: {
        hour: values.perAuthorHour,
        day: values.perAuthorDay,
        week: values.perAuthorWeek,
      },
      global: {
        hour: values.globalHour,
        day: values.globalDay,
        week: values.globalWeek,
      },
    },
    schedule: scheduleWindowToSuggest(windowValue, values.scheduleEnabled, {
      minIntervalMinutes: values.minIntervalMinutes,
      pauseAfterAdsMinutes: values.schedulePauseAfterAdsMinutes,
    }),
    dedup: {
      enabled: values.dedupEnabled,
      lookback_days: values.dedupLookbackDays,
      similarity_threshold: values.dedupSimilarityThreshold,
    },
    reject_policy: values.rejectPolicy,
    delete_after_hours: values.deleteAfterHours,
    notify_author: values.notifyAuthor,
    first_comment: {
      enabled: values.firstCommentEnabled,
      text: values.firstCommentText,
    },
    signed: values.signed,
    publish_edit_mode: values.publishEditMode,
    enrichment: {
      hashtags: values.enrichmentHashtags,
      append_text: values.enrichmentAppendText,
      media_asset_ids: preserved?.mediaAssetIds ?? [],
      keyword_signatures: values.keywordSignatures,
    },
    ads_pause: {
      enabled: values.adsPauseEnabled,
      detect_marked_as_ads: values.adsDetectMarkedAsAds,
      detect_erid: values.adsDetectErid,
      pause_after_ads_minutes: values.adsPauseAfterMinutes,
    },
  };
}

/** Cast typed config to OpenAPI blob (`Record` / index signature). */
export function configAsApiBlob(config: SuggestConfig): SuggestSettings['config'] {
  return config as unknown as SuggestSettings['config'];
}

export function formValuesFromSettings(settings: SuggestSettings): SuggestSettingsFormValues {
  const config = withConfigDefaults(settings.config);
  const schedule = suggestScheduleToWindow(config.schedule);
  const mode: SuggestMode =
    settings.mode === 'auto' || settings.mode === 'hybrid' ? settings.mode : 'manual';

  return {
    channelId: settings.channelId ?? '',
    mode,
    activate: settings.status === 'active',
    keywordInclude: config.filters.keyword_include,
    keywordExclude: config.filters.keyword_exclude,
    stopWords: config.filters.stop_words,
    keywordMatchMode: matchModeFromApi(config.filters.keyword_match),
    requireText: config.filters.require_text,
    requireImage: config.filters.require_image,
    forbidAttachments: config.filters.forbid_attachments,
    requireMessagesAllowed: config.filters.require_messages_allowed,
    forbidClosedProfile: config.filters.forbid_closed_profile,
    authorBlacklist: config.filters.author_blacklist,
    authorWhitelist: config.filters.author_whitelist,
    perAuthorHour: config.limits.per_author.hour,
    perAuthorDay: config.limits.per_author.day,
    perAuthorWeek: config.limits.per_author.week,
    globalHour: config.limits.global.hour,
    globalDay: config.limits.global.day,
    globalWeek: config.limits.global.week,
    scheduleEnabled: schedule.enabled,
    scheduleTimezone: schedule.window.timezone,
    scheduleDays: schedule.window.days,
    scheduleIntervals: schedule.window.intervals,
    minIntervalMinutes: schedule.minIntervalMinutes,
    schedulePauseAfterAdsMinutes: schedule.pauseAfterAdsMinutes,
    dedupEnabled: config.dedup.enabled,
    dedupLookbackDays: config.dedup.lookback_days,
    dedupSimilarityThreshold: config.dedup.similarity_threshold,
    rejectPolicy: config.reject_policy,
    deleteAfterHours: config.delete_after_hours,
    notifyAuthor: config.notify_author,
    firstCommentEnabled: config.first_comment.enabled,
    firstCommentText: config.first_comment.text,
    signed: config.signed,
    publishEditMode: config.publish_edit_mode,
    enrichmentHashtags: config.enrichment.hashtags,
    enrichmentAppendText: config.enrichment.append_text,
    keywordSignatures: config.enrichment.keyword_signatures,
    adsPauseEnabled: config.ads_pause.enabled,
    adsDetectMarkedAsAds: config.ads_pause.detect_marked_as_ads,
    adsDetectErid: config.ads_pause.detect_erid,
    adsPauseAfterMinutes: config.ads_pause.pause_after_ads_minutes,
  };
}

export function parseSuggestConfig(raw: unknown): SuggestConfig {
  return withConfigDefaults(raw);
}
