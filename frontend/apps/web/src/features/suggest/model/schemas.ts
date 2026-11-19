import { z } from 'zod';

const boolField = z.boolean();

function emptyNumber(fallback: number) {
  return (value: unknown) => {
    if (typeof value === 'number' && Number.isNaN(value)) return fallback;
    if (value === '' || value === null || value === undefined) return fallback;
    return value;
  };
}

function intField(min: number, max: number, fallback = 0) {
  return z.preprocess(emptyNumber(fallback), z.number().int().min(min).max(max));
}

function floatField(min: number, max: number, fallback: number) {
  return z.preprocess(emptyNumber(fallback), z.number().min(min).max(max));
}

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

  perAuthorHour: intField(0, 500),
  perAuthorDay: intField(0, 2000),
  perAuthorWeek: intField(0, 10000),
  globalHour: intField(0, 500),
  globalDay: intField(0, 2000),
  globalWeek: intField(0, 10000),

  scheduleEnabled: boolField,
  scheduleTimezone: z.string().min(1),
  scheduleDays: z.array(z.number().int().min(0).max(6)),
  scheduleIntervals: z.array(
    z.object({
      start: z.string(),
      end: z.string(),
    }),
  ),
  minIntervalMinutes: intField(0, 1440),
  schedulePauseAfterAdsMinutes: intField(0, 10080),

  dedupEnabled: boolField,
  dedupLookbackDays: intField(1, 90, 7),
  dedupSimilarityThreshold: floatField(0, 1, 0.8),

  rejectPolicy: z.enum(['delete', 'keep', 'delete_after']),
  deleteAfterHours: intField(0, 8760),
  notifyAuthor: boolField,

  firstCommentEnabled: boolField,
  firstCommentText: z.string().max(4000),

  signed: boolField,
  publishEditMode: z.enum(['wall_post_edit', 'fallback_new_post']),

  enrichmentHashtags: z.array(z.string()),
  enrichmentAppendText: z.string().max(4000),
  keywordSignatures: z.array(z.string()),

  adsPauseEnabled: boolField,
  adsDetectMarkedAsAds: boolField,
  adsDetectErid: boolField,
  adsPauseAfterMinutes: intField(0, 10080),
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
