import type { GrabberConfig, GrabberRule } from '@posts/api-client';

import {
  keywordsFromText,
  wordReplacementsFromText,
  wordReplacementsToText,
  type GrabberRuleFormValues,
} from './schemas';
import { normalizeSourceIds, sourceIdsToTextarea } from './source-ids';
import { DEFAULT_ATTRIBUTION_TEMPLATE } from './types';

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

function attachmentField(value: unknown): string {
  if (typeof value === 'string') return value;
  if (Array.isArray(value)) return value.map((item) => String(item)).filter(Boolean).join(', ');
  return '';
}

function actionButtonField(value: unknown, key: 'title' | 'url'): string {
  const button = asRecord(value);
  return typeof button[key] === 'string' ? button[key] : '';
}

function deleteAfterUnit(value: unknown): GrabberRuleFormValues['deleteAfterUnit'] {
  return value === 'minutes' || value === 'days' || value === 'hours' ? value : 'hours';
}

function readMaxAgeDays(filters: Record<string, unknown>): number {
  if (!Object.prototype.hasOwnProperty.call(filters, 'max_age_days') || filters.max_age_days == null) {
    return Object.prototype.hasOwnProperty.call(filters, 'max_age_days') ? 0 : 60;
  }
  return asNumber(filters.max_age_days, 60);
}

function asDayList(value: unknown): number[] {
  if (!Array.isArray(value) || value.length === 0) return [1, 2, 3, 4, 5, 6, 7];
  return value
    .map((item) => Number(item))
    .filter((n) => Number.isInteger(n) && n >= 1 && n <= 7);
}

export function buildGrabberConfig(values: GrabberRuleFormValues): GrabberConfig {
  const filters = {
    keyword_include: keywordsFromText(values.keywordIncludeText),
    keyword_exclude: keywordsFromText(values.keywordExcludeText),
    stop_words: keywordsFromText(values.stopWordsText),
    keyword_match: 'any',
    exclude_ads: values.excludeAds,
    exclude_reposts: values.excludeReposts,
    exclude_links: values.excludeLinks,
    skip_if_no_text: values.skipIfNoText,
    skip_if_no_media: values.skipIfNoMedia,
    skip_if_any_text: values.skipIfAnyText,
    skip_if_homoglyphs: values.skipIfHomoglyphs,
    skip_if_caps: values.skipIfCaps,
    caps_threshold: values.capsThreshold,
    caps_min_letters: values.capsMinLetters,
    skip_if_phone: values.skipIfPhone,
    require_phone: values.requirePhone,
    require_phone_or_signature: values.requirePhoneOrSignature,
    only_group_posts: values.onlyGroupPosts,
    skip_if_no_music: values.skipIfNoMusic,
    skip_if_no_photo: values.skipIfNoPhoto,
    skip_if_no_video: values.skipIfNoVideo,
    skip_if_no_clip: values.skipIfNoClip,
    skip_if_no_product: values.skipIfNoProduct,
    skip_if_no_file: values.skipIfNoFile,
    skip_if_no_poll: values.skipIfNoPoll,
    skip_if_has_article: values.skipIfHasArticle,
    skip_if_has_video: values.skipIfHasVideo,
    skip_if_has_clip: values.skipIfHasClip,
    skip_if_has_photo: values.skipIfHasPhoto,
    skip_if_has_music: values.skipIfHasMusic,
    skip_if_has_file: values.skipIfHasFile,
    skip_if_has_product: values.skipIfHasProduct,
    skip_if_has_poll: values.skipIfHasPoll,
    max_age_days: values.maxAgeDays,
  };

  const transform = {
    prepend_text: values.prependText,
    append_text: values.appendText,
    strip_photo: values.stripPhoto,
    strip_video: values.stripVideo,
    strip_clip: values.stripClip,
    strip_music: values.stripMusic,
    strip_audio: values.stripMusic,
    strip_playlist: values.stripPlaylist,
    strip_file: values.stripFile,
    strip_doc: values.stripFile,
    strip_poll: values.stripPoll,
    strip_album: values.stripAlbum,
    strip_product: values.stripProduct,
    strip_market: values.stripProduct,
    strip_market_album: values.stripMarketAlbum,
    strip_wiki: values.stripWiki,
    strip_article: values.stripArticle,
    strip_all_links: values.stripAllLinks,
    strip_links: values.stripAllLinks,
    strip_hashtags: values.stripHashtags,
    strip_mentions: values.stripMentions,
    strip_all_text: values.stripAllText,
    strip_phones: values.stripPhones,
    word_replacements: wordReplacementsFromText(values.wordReplacementsText),
    remove_keywords: keywordsFromText(values.removeKeywordsText),
    append_author_signature: values.appendAuthorSignature,
    close_comments: values.closeComments,
    delete_after_enabled: values.deleteAfterEnabled,
    delete_after_amount: values.deleteAfterAmount,
    delete_after_unit: values.deleteAfterUnit,
    action_button:
      values.actionButtonTitle.trim() || values.actionButtonUrl.trim()
        ? {
            type: 'open_url',
            title: values.actionButtonTitle.trim(),
            url: values.actionButtonUrl.trim(),
          }
        : {},
    ai_model: values.aiModel.trim() || 'stub',
    attach_when_no_attachments: values.attachWhenNoAttachments,
    attachments_mode: values.attachmentsMode,
    link_to_source: values.linkToSource,
    link_to_author: values.linkToAuthor,
  };

  const config: GrabberConfig = {
    configVersion: 3,
    scanIntervalMinutes: values.scanIntervalMinutes,
    publishMode: values.publishMode,
    targetMode: values.targetMode,
    scheduleMode: values.scheduleMode,
    publishDelayMinutes: values.publishDelayMinutes,
    filters: filters as unknown as GrabberConfig['filters'],
    transform: transform as unknown as GrabberConfig['transform'],
    attribution: {
      enabled: true,
      placement: values.attributionPlacement,
      template: values.attributionTemplate || DEFAULT_ATTRIBUTION_TEMPLATE,
    } as unknown as GrabberConfig['attribution'],
    limits: {
      rate_limit_per_hour: values.rateLimitPerHour,
      rate_limit_per_day: values.rateLimitPerDay,
      rate_limit_per_source_per_day: values.rateLimitPerSourcePerDay,
      min_interval_minutes: values.minIntervalMinutes,
    } as unknown as GrabberConfig['limits'],
    reliability: {
      dedup_enabled: true,
      dedup_scope: 'channel',
      similarity_dedup_enabled: values.similarityDedupEnabled,
      similarity_threshold: values.similarityThreshold,
      similarity_lookback: values.similarityLookback,
      ignore_edits: true,
      auto_pause_on_errors: values.autoPauseOnErrors,
      auto_pause_error_threshold: 5,
      notify_on_error: values.notifyOnError,
    } as unknown as GrabberConfig['reliability'],
    schedule: {
      enabled: values.scheduleEnabled,
      timezone: values.scheduleTimezone,
      days_of_week: values.scheduleDays,
      hours_from: values.hoursFrom,
      hours_to: values.hoursTo,
    } as unknown as GrabberConfig['schedule'],
    firstComment: {
      enabled: values.firstCommentEnabled,
      text: values.firstCommentText,
      attachments: values.firstCommentAttachments,
      pending_be23: false,
    } as unknown as GrabberConfig['firstComment'],
    macros: {} as unknown as GrabberConfig['macros'],
    aiRewriteEnabled: values.aiRewriteEnabled,
  };

  if (values.type === 'grabber') {
    config.sourceIds = normalizeSourceIds(values.sourceIdsText ?? '');
  }
  if (values.type === 'rss') {
    config.rssUrl = values.rssUrl?.trim();
    config.rssMaxItemsPerRun = 20;
  }
  if (values.type === 'website') {
    config.websiteUrls = normalizeSourceIds(values.websiteUrlsText ?? '');
    config.websiteSelector = values.websiteSelector?.trim() || undefined;
  }

  return config;
}

export function formValuesFromRule(rule: GrabberRule): GrabberRuleFormValues {
  const config = rule.config ?? {};
  const filters = asRecord(config.filters);
  const transform = asRecord(config.transform);
  const attribution = asRecord(config.attribution);
  const limits = asRecord(config.limits);
  const reliability = asRecord(config.reliability);
  const schedule = asRecord(config.schedule);
  const firstComment = asRecord(config.firstComment);

  return {
    name: rule.name ?? '',
    type: rule.type ?? 'grabber',
    targetChannelId: rule.targetChannelId ?? '',
    sourceIdsText: sourceIdsToTextarea(config.sourceIds),
    rssUrl: config.rssUrl ?? '',
    websiteUrlsText: sourceIdsToTextarea(config.websiteUrls),
    websiteSelector: config.websiteSelector ?? '',
    scanIntervalMinutes: (config.scanIntervalMinutes as GrabberRuleFormValues['scanIntervalMinutes']) ?? 180,
    publishMode: config.publishMode ?? 'moderate',
    targetMode: config.targetMode ?? 'wall_now',
    notifyOnError: asBool(reliability.notify_on_error, true),
    scheduleMode: (config.scheduleMode as GrabberRuleFormValues['scheduleMode']) ?? 'immediate',
    publishDelayMinutes: config.publishDelayMinutes ?? 0,

    excludeAds: asBool(filters.exclude_ads, true),
    excludeReposts: asBool(filters.exclude_reposts, true),
    excludeLinks: asBool(filters.exclude_links, false),
    skipIfNoText: asBool(filters.skip_if_no_text, false),
    skipIfNoMedia: asBool(filters.skip_if_no_media, false),
    skipIfAnyText: asBool(filters.skip_if_any_text, false),
    skipIfHomoglyphs: asBool(filters.skip_if_homoglyphs, false),
    skipIfCaps: asBool(filters.skip_if_caps, false),
    capsThreshold: asNumber(filters.caps_threshold, 0.8),
    capsMinLetters: asNumber(filters.caps_min_letters, 8),
    skipIfPhone: asBool(filters.skip_if_phone, false),
    requirePhone: asBool(filters.require_phone, false),
    requirePhoneOrSignature: asBool(filters.require_phone_or_signature, false),
    onlyGroupPosts: asBool(filters.only_group_posts, false),
    skipIfNoMusic: asBool(filters.skip_if_no_music, false),
    skipIfNoPhoto: asBool(filters.skip_if_no_photo, false),
    skipIfNoVideo: asBool(filters.skip_if_no_video, false),
    skipIfNoClip: asBool(filters.skip_if_no_clip, false),
    skipIfNoProduct: asBool(filters.skip_if_no_product, false),
    skipIfNoFile: asBool(filters.skip_if_no_file, false),
    skipIfNoPoll: asBool(filters.skip_if_no_poll, false),
    skipIfHasArticle: asBool(filters.skip_if_has_article, false),
    skipIfHasVideo: asBool(filters.skip_if_has_video, false),
    skipIfHasClip: asBool(filters.skip_if_has_clip, false),
    skipIfHasPhoto: asBool(filters.skip_if_has_photo, false),
    skipIfHasMusic: asBool(filters.skip_if_has_music, false),
    skipIfHasFile: asBool(filters.skip_if_has_file, false),
    skipIfHasProduct: asBool(filters.skip_if_has_product, false),
    skipIfHasPoll: asBool(filters.skip_if_has_poll, false),
    maxAgeDays: readMaxAgeDays(filters),
    keywordIncludeText: asStringList(filters.keyword_include).join(', '),
    keywordExcludeText: asStringList(filters.keyword_exclude).join(', '),
    stopWordsText: asStringList(filters.stop_words).join(', '),
    similarityDedupEnabled: asBool(reliability.similarity_dedup_enabled, true),
    similarityThreshold: asNumber(reliability.similarity_threshold, 0.8),
    similarityLookback: asNumber(reliability.similarity_lookback, 15),

    stripPhoto: asBool(transform.strip_photo, false),
    stripVideo: asBool(transform.strip_video, false),
    stripClip: asBool(transform.strip_clip, false),
    stripMusic: asBool(transform.strip_music, false) || asBool(transform.strip_audio, false),
    stripPlaylist: asBool(transform.strip_playlist, false),
    stripFile: asBool(transform.strip_file, false) || asBool(transform.strip_doc, false),
    stripPoll: asBool(transform.strip_poll, false),
    stripAlbum: asBool(transform.strip_album, false),
    stripProduct: asBool(transform.strip_product, false) || asBool(transform.strip_market, false),
    stripMarketAlbum: asBool(transform.strip_market_album, false),
    stripWiki: asBool(transform.strip_wiki, false),
    stripArticle: asBool(transform.strip_article, false),
    stripAllLinks: asBool(transform.strip_all_links, false) || asBool(transform.strip_links, false),
    stripHashtags: asBool(transform.strip_hashtags, false),
    stripMentions: asBool(transform.strip_mentions, false),
    stripAllText: asBool(transform.strip_all_text, false),
    stripPhones: asBool(transform.strip_phones, false),

    wordReplacementsText: wordReplacementsToText(transform.word_replacements),
    removeKeywordsText: asStringList(transform.remove_keywords).join(', '),

    scheduleEnabled: asBool(schedule.enabled, false),
    scheduleTimezone:
      typeof schedule.timezone === 'string' && schedule.timezone
        ? schedule.timezone
        : 'Europe/Moscow',
    scheduleDays: asDayList(schedule.days_of_week),
    hoursFrom: asNumber(schedule.hours_from, 0),
    hoursTo: asNumber(schedule.hours_to, 24),

    rateLimitPerHour: asNumber(limits.rate_limit_per_hour, 10),
    rateLimitPerDay: asNumber(limits.rate_limit_per_day, 50),
    rateLimitPerSourcePerDay: asNumber(limits.rate_limit_per_source_per_day, 0),
    minIntervalMinutes: asNumber(limits.min_interval_minutes, 0),

    firstCommentEnabled: asBool(firstComment.enabled, false),
    firstCommentText: typeof firstComment.text === 'string' ? firstComment.text : '',
    firstCommentAttachments: attachmentField(firstComment.attachments),
    closeComments: asBool(transform.close_comments, false),
    actionButtonTitle: actionButtonField(transform.action_button, 'title'),
    actionButtonUrl: actionButtonField(transform.action_button, 'url'),
    deleteAfterEnabled: asBool(transform.delete_after_enabled, false),
    deleteAfterAmount: asNumber(transform.delete_after_amount, 24),
    deleteAfterUnit: deleteAfterUnit(transform.delete_after_unit),
    appendAuthorSignature: asBool(transform.append_author_signature, true),
    aiRewriteEnabled: asBool(config.aiRewriteEnabled, false),
    aiModel: typeof transform.ai_model === 'string' && transform.ai_model ? transform.ai_model : 'stub',

    prependText: typeof transform.prepend_text === 'string' ? transform.prepend_text : '',
    appendText: typeof transform.append_text === 'string' ? transform.append_text : '',
    attachWhenNoAttachments: asBool(transform.attach_when_no_attachments, false),
    attachmentsMode: transform.attachments_mode === 'carousel' ? 'carousel' : 'grid',
    linkToSource: asBool(transform.link_to_source, false),
    linkToAuthor: asBool(transform.link_to_author, false),
    autoPauseOnErrors: asBool(reliability.auto_pause_on_errors, true),

    attributionEnabled: true,
    attributionPlacement:
      (attribution.placement as GrabberRuleFormValues['attributionPlacement']) ?? 'footer',
    attributionTemplate:
      typeof attribution.template === 'string' && attribution.template
        ? attribution.template
        : DEFAULT_ATTRIBUTION_TEMPLATE,
    userResponsibilityAccepted: Boolean(rule.userResponsibilityAcceptedAt),
    activate: rule.status === 'active',
  };
}

export function defaultFormValues(channelId?: string): GrabberRuleFormValues {
  return {
    name: '',
    type: 'grabber',
    targetChannelId: channelId ?? '',
    sourceIdsText: '',
    rssUrl: '',
    websiteUrlsText: '',
    websiteSelector: '',
    scanIntervalMinutes: 180,
    publishMode: 'moderate',
    targetMode: 'wall_now',
    notifyOnError: true,
    scheduleMode: 'immediate',
    publishDelayMinutes: 0,

    excludeAds: true,
    excludeReposts: true,
    excludeLinks: false,
    skipIfNoText: false,
    skipIfNoMedia: false,
    skipIfAnyText: false,
    skipIfHomoglyphs: false,
    skipIfCaps: false,
    capsThreshold: 0.8,
    capsMinLetters: 8,
    skipIfPhone: false,
    requirePhone: false,
    requirePhoneOrSignature: false,
    onlyGroupPosts: false,
    skipIfNoMusic: false,
    skipIfNoPhoto: false,
    skipIfNoVideo: false,
    skipIfNoClip: false,
    skipIfNoProduct: false,
    skipIfNoFile: false,
    skipIfNoPoll: false,
    skipIfHasArticle: false,
    skipIfHasVideo: false,
    skipIfHasClip: false,
    skipIfHasPhoto: false,
    skipIfHasMusic: false,
    skipIfHasFile: false,
    skipIfHasProduct: false,
    skipIfHasPoll: false,
    maxAgeDays: 60,
    keywordIncludeText: '',
    keywordExcludeText: '',
    stopWordsText: '',
    similarityDedupEnabled: true,
    similarityThreshold: 0.8,
    similarityLookback: 15,

    stripPhoto: false,
    stripVideo: false,
    stripClip: false,
    stripMusic: false,
    stripPlaylist: false,
    stripFile: false,
    stripPoll: false,
    stripAlbum: false,
    stripProduct: false,
    stripMarketAlbum: false,
    stripWiki: false,
    stripArticle: false,
    stripAllLinks: false,
    stripHashtags: false,
    stripMentions: false,
    stripAllText: false,
    stripPhones: false,

    wordReplacementsText: '',
    removeKeywordsText: '',

    scheduleEnabled: false,
    scheduleTimezone: 'Europe/Moscow',
    scheduleDays: [1, 2, 3, 4, 5, 6, 7],
    hoursFrom: 0,
    hoursTo: 24,

    rateLimitPerHour: 10,
    rateLimitPerDay: 50,
    rateLimitPerSourcePerDay: 0,
    minIntervalMinutes: 0,

    firstCommentEnabled: false,
    firstCommentText: '',
    firstCommentAttachments: '',
    closeComments: false,
    actionButtonTitle: '',
    actionButtonUrl: '',
    deleteAfterEnabled: false,
    deleteAfterAmount: 24,
    deleteAfterUnit: 'hours',
    appendAuthorSignature: true,
    aiRewriteEnabled: false,
    aiModel: 'stub',

    prependText: '',
    appendText: '',
    attachWhenNoAttachments: false,
    attachmentsMode: 'grid',
    linkToSource: false,
    linkToAuthor: false,
    autoPauseOnErrors: true,

    attributionEnabled: true,
    attributionPlacement: 'footer',
    attributionTemplate: DEFAULT_ATTRIBUTION_TEMPLATE,
    userResponsibilityAccepted: false,
    activate: true,
  };
}

export function isPrivateOrMetadataUrl(url: string): boolean {
  try {
    const parsed = new URL(url);
    const host = parsed.hostname.toLowerCase();
    if (host === 'localhost' || host === 'metadata.google.internal') return true;
    if (host === '169.254.169.254') return true;
    if (/^127\./.test(host)) return true;
    if (/^10\./.test(host)) return true;
    if (/^192\.168\./.test(host)) return true;
    if (/^172\.(1[6-9]|2\d|3[0-1])\./.test(host)) return true;
    if (host.endsWith('.local') || host.endsWith('.internal')) return true;
    return false;
  } catch {
    return false;
  }
}

/** Apply cloned settings maps into form values without changing sources / publish mode. */
export function applyClonedConfigToForm(
  current: GrabberRuleFormValues,
  config: GrabberConfig,
): GrabberRuleFormValues {
  const synthetic: GrabberRule = {
    name: current.name,
    type: current.type,
    targetChannelId: current.targetChannelId,
    status: current.activate ? 'active' : 'draft',
    userResponsibilityAcceptedAt: current.userResponsibilityAccepted
      ? new Date().toISOString()
      : undefined,
    config: {
      ...config,
      // keep current source / mode fields
      sourceIds: current.type === 'grabber' ? normalizeSourceIds(current.sourceIdsText) : undefined,
      rssUrl: current.type === 'rss' ? current.rssUrl : undefined,
      websiteUrls: current.type === 'website' ? normalizeSourceIds(current.websiteUrlsText) : undefined,
      websiteSelector: current.websiteSelector,
      publishMode: current.publishMode,
      scanIntervalMinutes: current.scanIntervalMinutes,
      scheduleMode: current.scheduleMode,
      publishDelayMinutes: current.publishDelayMinutes,
    },
  };
  const next = formValuesFromRule(synthetic);
  return {
    ...next,
    name: current.name,
    type: current.type,
    targetChannelId: current.targetChannelId,
    sourceIdsText: current.sourceIdsText,
    rssUrl: current.rssUrl,
    websiteUrlsText: current.websiteUrlsText,
    websiteSelector: current.websiteSelector,
    publishMode: current.publishMode,
    scanIntervalMinutes: current.scanIntervalMinutes,
    scheduleMode: current.scheduleMode,
    publishDelayMinutes: current.publishDelayMinutes,
    userResponsibilityAccepted: current.userResponsibilityAccepted,
    activate: current.activate,
  };
}
