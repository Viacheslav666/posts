import { z } from 'zod';

import {
  DEFAULT_ATTRIBUTION_TEMPLATE,
  MAX_SOURCES_PER_RULE,
  SCAN_INTERVALS,
  isPostponedTarget,
  isRepostTarget,
} from './types';
import { isValidSourceId, normalizeSourceIds } from './source-ids';

const boolField = z.boolean();

export const grabberRuleFormSchema = z
  .object({
    name: z.string().trim().min(1, 'nameRequired').max(120, 'nameTooLong'),
    type: z.enum(['grabber', 'rss', 'website']),
    targetChannelId: z.string().min(1, 'channelRequired'),
    sourceIdsText: z.string(),
    rssUrl: z.string(),
    websiteUrlsText: z.string(),
    websiteSelector: z.string(),
    scanIntervalMinutes: z.number().refine((value) => (SCAN_INTERVALS as readonly number[]).includes(value)),
    publishMode: z.enum(['moderate', 'auto', 'draft']),
    targetMode: z.enum(['wall_now', 'vk_postponed_post', 'wall_repost', 'vk_postponed_repost']),
    notifyOnError: z.boolean(),
    scheduleMode: z.enum(['immediate', 'delay', 'slot']),
    publishDelayMinutes: z.number().int().min(0).max(10080),

    // Filters — «Не публиковать, если»
    excludeAds: boolField,
    excludeReposts: boolField,
    excludeLinks: boolField,
    skipIfNoText: boolField,
    skipIfNoMedia: boolField,
    skipIfAnyText: boolField,
    skipIfHomoglyphs: boolField,
    skipIfCaps: boolField,
    capsThreshold: z.number().min(0.1).max(1),
    capsMinLetters: z.number().int().min(1).max(200),
    skipIfPhone: boolField,
    requirePhone: boolField,
    requirePhoneOrSignature: boolField,
    onlyGroupPosts: boolField,
    skipIfNoMusic: boolField,
    skipIfNoPhoto: boolField,
    skipIfNoVideo: boolField,
    skipIfNoClip: boolField,
    skipIfNoProduct: boolField,
    skipIfNoFile: boolField,
    skipIfNoPoll: boolField,
    skipIfHasArticle: boolField,
    skipIfHasVideo: boolField,
    skipIfHasClip: boolField,
    skipIfHasPhoto: boolField,
    skipIfHasMusic: boolField,
    skipIfHasFile: boolField,
    skipIfHasProduct: boolField,
    skipIfHasPoll: boolField,
    maxAgeDays: z.number().int().min(0).max(3650),
    keywordIncludeText: z.string(),
    keywordExcludeText: z.string(),
    stopWordsText: z.string(),
    similarityDedupEnabled: boolField,
    similarityThreshold: z.number().min(0.5).max(1),
    similarityLookback: z.number().int().min(1).max(100),

    // Transform — «Удалять из поста»
    stripPhoto: boolField,
    stripVideo: boolField,
    stripClip: boolField,
    stripMusic: boolField,
    stripPlaylist: boolField,
    stripFile: boolField,
    stripPoll: boolField,
    stripAlbum: boolField,
    stripProduct: boolField,
    stripMarketAlbum: boolField,
    stripWiki: boolField,
    stripArticle: boolField,
    stripAllLinks: boolField,
    stripHashtags: boolField,
    stripMentions: boolField,
    stripAllText: boolField,
    stripPhones: boolField,

    // Word replacements / keyword removal
    wordReplacementsText: z.string(),
    removeKeywordsText: z.string(),

    // Working window
    scheduleEnabled: boolField,
    scheduleTimezone: z.string().min(1),
    scheduleDays: z.array(z.number().int().min(1).max(7)).min(1, 'scheduleDaysRequired'),
    hoursFrom: z.number().int().min(0).max(23),
    hoursTo: z.number().int().min(0).max(24),

    // Limits
    rateLimitPerHour: z.number().int().min(1).max(60),
    rateLimitPerDay: z.number().int().min(0).max(500),
    rateLimitPerSourcePerDay: z.number().int().min(0).max(500),
    minIntervalMinutes: z.number().int().min(0).max(120),

    // First comment (pending_be23)
    firstCommentEnabled: boolField,
    firstCommentText: z.string().max(4000),

    // Attached text / attachments
    prependText: z.string().max(4000),
    appendText: z.string().max(4000),
    attachWhenNoAttachments: boolField,
    attachmentsMode: z.enum(['grid', 'carousel']),
    linkToSource: boolField,
    linkToAuthor: boolField,
    autoPauseOnErrors: boolField,

    attributionEnabled: z.literal(true),
    attributionPlacement: z.enum(['footer', 'first_comment', 'both']),
    attributionTemplate: z.string().trim().min(1, 'attributionRequired'),
    userResponsibilityAccepted: z.boolean(),
    activate: z.boolean(),
  })
  .superRefine((values, ctx) => {
    if (values.type === 'grabber') {
      const ids = normalizeSourceIds(values.sourceIdsText ?? '');
      if (ids.length < 1) {
        ctx.addIssue({ code: 'custom', path: ['sourceIdsText'], message: 'sourcesRequired' });
      }
      if (ids.length > MAX_SOURCES_PER_RULE) {
        ctx.addIssue({ code: 'custom', path: ['sourceIdsText'], message: 'sourcesTooMany' });
      }
      for (const id of ids) {
        if (!isValidSourceId(id)) {
          ctx.addIssue({ code: 'custom', path: ['sourceIdsText'], message: 'sourcesInvalid' });
          break;
        }
      }
    }
    if (values.type === 'rss') {
      const url = (values.rssUrl ?? '').trim();
      if (!url) {
        ctx.addIssue({ code: 'custom', path: ['rssUrl'], message: 'rssRequired' });
      } else if (!/^https?:\/\//i.test(url)) {
        ctx.addIssue({ code: 'custom', path: ['rssUrl'], message: 'rssInvalid' });
      }
    }
    if (values.type === 'website') {
      const urls = normalizeSourceIds(values.websiteUrlsText ?? '');
      if (urls.length < 1) {
        ctx.addIssue({ code: 'custom', path: ['websiteUrlsText'], message: 'websiteRequired' });
      }
      if (urls.length > 20) {
        ctx.addIssue({ code: 'custom', path: ['websiteUrlsText'], message: 'websiteTooMany' });
      }
      for (const url of urls) {
        if (!/^https?:\/\//i.test(url)) {
          ctx.addIssue({ code: 'custom', path: ['websiteUrlsText'], message: 'websiteInvalid' });
          break;
        }
      }
    }
    if (isPostponedTarget(values.targetMode) && values.minIntervalMinutes > 0) {
      ctx.addIssue({
        code: 'custom',
        path: ['minIntervalMinutes'],
        message: 'intervalUnavailable',
      });
    }
    if (isPostponedTarget(values.targetMode) && values.firstCommentEnabled) {
      ctx.addIssue({
        code: 'custom',
        path: ['firstCommentEnabled'],
        message: 'firstCommentUnavailable',
      });
    }
    if (isRepostTarget(values.targetMode) && values.type !== 'grabber') {
      ctx.addIssue({
        code: 'custom',
        path: ['targetMode'],
        message: 'repostGrabberOnly',
      });
    }
    if (values.activate && !values.userResponsibilityAccepted) {
      ctx.addIssue({
        code: 'custom',
        path: ['userResponsibilityAccepted'],
        message: 'responsibilityRequired',
      });
    }
    if (!values.attributionTemplate?.trim()) {
      ctx.addIssue({
        code: 'custom',
        path: ['attributionTemplate'],
        message: 'attributionRequired',
      });
    }
    if (values.scheduleEnabled && values.hoursFrom === values.hoursTo && values.hoursTo !== 0) {
      // equal hours = full day on backend; allowed
    }
  });

export type GrabberRuleFormValues = z.infer<typeof grabberRuleFormSchema>;

export function keywordsFromText(raw: string | undefined): string[] {
  return (raw ?? '')
    .split(/[,;\n]+/)
    .map((part) => part.trim())
    .filter(Boolean)
    .slice(0, 50);
}

/** Parse `from=to` / `from → to` lines into a replacement map. */
export function wordReplacementsFromText(raw: string | undefined): Record<string, string> {
  const map: Record<string, string> = {};
  for (const line of (raw ?? '').split('\n')) {
    const trimmed = line.trim();
    if (!trimmed) continue;
    const match = trimmed.match(/^(.+?)\s*(?:=|→|->)\s*(.*)$/);
    if (!match) continue;
    const from = match[1]?.trim();
    if (!from) continue;
    map[from] = match[2]?.trim() ?? '';
  }
  return map;
}

export function wordReplacementsToText(map: unknown): string {
  if (!map || typeof map !== 'object' || Array.isArray(map)) return '';
  return Object.entries(map as Record<string, unknown>)
    .map(([from, to]) => `${from}=${to == null ? '' : String(to)}`)
    .join('\n');
}

export { DEFAULT_ATTRIBUTION_TEMPLATE };
