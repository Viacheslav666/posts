import type { GrabberConfig, GrabberRule } from '@posts/api-client';

import {
  keywordsFromText,
  type GrabberRuleFormValues,
} from './schemas';
import { normalizeSourceIds, sourceIdsToTextarea } from './source-ids';
import { DEFAULT_ATTRIBUTION_TEMPLATE } from './types';

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {};
}

export function buildGrabberConfig(values: GrabberRuleFormValues): GrabberConfig {
  const filters = {
    keyword_include: keywordsFromText(values.keywordIncludeText),
    keyword_exclude: keywordsFromText(values.keywordExcludeText),
    keyword_match: 'any',
    exclude_ads: values.excludeAds,
    exclude_reposts: values.excludeReposts,
  };

  const config: GrabberConfig = {
    scanIntervalMinutes: values.scanIntervalMinutes,
    publishMode: values.publishMode,
    scheduleMode: values.scheduleMode,
    publishDelayMinutes: values.publishDelayMinutes,
    filters: filters as unknown as GrabberConfig['filters'],
    transform: {
      prepend_text: '',
      append_text: '',
      strip_hashtags: false,
      strip_mentions: false,
    } as unknown as GrabberConfig['transform'],
    attribution: {
      enabled: true,
      placement: values.attributionPlacement,
      template: values.attributionTemplate || DEFAULT_ATTRIBUTION_TEMPLATE,
    } as unknown as GrabberConfig['attribution'],
    limits: {
      rate_limit_per_hour: values.rateLimitPerHour,
      rate_limit_per_day: values.rateLimitPerDay,
    } as unknown as GrabberConfig['limits'],
    reliability: {
      dedup_enabled: true,
      dedup_scope: 'channel',
      ignore_edits: true,
      auto_pause_on_errors: true,
      auto_pause_error_threshold: 5,
    } as unknown as GrabberConfig['reliability'],
    aiRewriteEnabled: false,
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
  const attribution = asRecord(config.attribution);
  const limits = asRecord(config.limits);

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
    scheduleMode: (config.scheduleMode as GrabberRuleFormValues['scheduleMode']) ?? 'immediate',
    publishDelayMinutes: config.publishDelayMinutes ?? 0,
    excludeAds: filters.exclude_ads !== false,
    excludeReposts: filters.exclude_reposts !== false,
    keywordIncludeText: Array.isArray(filters.keyword_include)
      ? (filters.keyword_include as string[]).join(', ')
      : '',
    keywordExcludeText: Array.isArray(filters.keyword_exclude)
      ? (filters.keyword_exclude as string[]).join(', ')
      : '',
    attributionEnabled: true,
    attributionPlacement:
      (attribution.placement as GrabberRuleFormValues['attributionPlacement']) ?? 'footer',
    attributionTemplate:
      typeof attribution.template === 'string' && attribution.template
        ? attribution.template
        : DEFAULT_ATTRIBUTION_TEMPLATE,
    userResponsibilityAccepted: Boolean(rule.userResponsibilityAcceptedAt),
    activate: rule.status === 'active',
    rateLimitPerHour:
      typeof limits.rate_limit_per_hour === 'number' ? limits.rate_limit_per_hour : 10,
    rateLimitPerDay: typeof limits.rate_limit_per_day === 'number' ? limits.rate_limit_per_day : 50,
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
    scheduleMode: 'immediate',
    publishDelayMinutes: 0,
    excludeAds: true,
    excludeReposts: true,
    keywordIncludeText: '',
    keywordExcludeText: '',
    attributionEnabled: true,
    attributionPlacement: 'footer',
    attributionTemplate: DEFAULT_ATTRIBUTION_TEMPLATE,
    userResponsibilityAccepted: false,
    activate: true,
    rateLimitPerHour: 10,
    rateLimitPerDay: 50,
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
