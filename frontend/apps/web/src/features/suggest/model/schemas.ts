import { z } from 'zod';

const boolField = z.boolean();

export const suggestSettingsFormSchema = z.object({
  channelId: z.string().min(1, 'channelRequired'),
  mode: z.enum(['auto', 'manual', 'hybrid']),
  activate: z.boolean(),

  keywordInclude: z.array(z.string()),
  keywordExclude: z.array(z.string()),
  stopWords: z.array(z.string()),
  /** KeywordListEditor matchMode; maps to API `contains` ↔ `substring`. */
  keywordMatchMode: z.enum(['substring', 'whole_word']),
  requireText: boolField,
  requireImage: boolField,
  forbidAttachments: z.array(z.string()),
  requireMessagesAllowed: boolField,
  forbidClosedProfile: boolField,
  authorBlacklist: z.array(z.string()),
  authorWhitelist: z.array(z.string()),

  perAuthorHour: z.number().int().min(0).max(500),
  perAuthorDay: z.number().int().min(0).max(2000),
  perAuthorWeek: z.number().int().min(0).max(10000),
  globalHour: z.number().int().min(0).max(500),
  globalDay: z.number().int().min(0).max(2000),
  globalWeek: z.number().int().min(0).max(10000),

  scheduleEnabled: boolField,
  scheduleTimezone: z.string().min(1),
  scheduleDays: z.array(z.number().int().min(0).max(6)),
  scheduleIntervals: z.array(
    z.object({
      start: z.string(),
      end: z.string(),
    }),
  ),
  minIntervalMinutes: z.number().int().min(0).max(1440),
  schedulePauseAfterAdsMinutes: z.number().int().min(0).max(10080),

  dedupEnabled: boolField,
  dedupLookbackDays: z.number().int().min(1).max(90),
  dedupSimilarityThreshold: z.number().min(0).max(1),

  rejectPolicy: z.enum(['delete', 'keep', 'delete_after']),
  deleteAfterHours: z.number().int().min(0).max(8760),
  notifyAuthor: boolField,

  firstCommentEnabled: boolField,
  firstCommentText: z.string().max(4000),

  signed: boolField,
  publishEditMode: z.enum(['wall_post_edit', 'fallback_new_post']),

  enrichmentHashtags: z.array(z.string()),
  enrichmentAppendText: z.string().max(4000),

  adsPauseEnabled: boolField,
  adsDetectMarkedAsAds: boolField,
  adsDetectErid: boolField,
  adsPauseAfterMinutes: z.number().int().min(0).max(10080),
});

export type SuggestSettingsFormValues = z.infer<typeof suggestSettingsFormSchema>;

export function vkIdsFromText(raw: string | undefined): string[] {
  return (raw ?? '')
    .split(/[,;\n\s]+/)
    .map((part) => part.trim())
    .filter(Boolean)
    .slice(0, 200);
}

export function vkIdsToText(ids: string[]): string {
  return ids.join(', ');
}
