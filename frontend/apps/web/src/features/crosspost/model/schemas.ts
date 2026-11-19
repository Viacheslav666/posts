import { z } from 'zod';

const boolField = z.boolean();

export const crosspostRuleFormSchema = z
  .object({
    name: z.string().trim().min(1, 'nameRequired').max(120, 'nameTooLong'),
    sourceChannelId: z.string().min(1, 'sourceRequired'),
    targetChannelIds: z.array(z.string()).min(1, 'targetsRequired'),

    // Filters
    keywordInclude: z.array(z.string()),
    keywordExclude: z.array(z.string()),
    excludeLinks: boolField,
    excludeSnippets: boolField,
    excludeArticles: boolField,
    skipIfNoText: boolField,
    skipIfNoImages: boolField,
    excludeVideo: boolField,
    excludeClips: boolField,
    excludePoll: boolField,
    excludeGeo: boolField,
    excludeAudio: boolField,
    excludeAds: boolField,
    excludeReposts: boolField,
    includeReposts: boolField,
    onlyGroupPostsInTgGroups: boolField,
    includePostsPublishedViaPosts: boolField,
    includeSuggestPublished: boolField,

    // Transform
    removeLinks: boolField,
    removeHashtags: boolField,
    addSourceLink: boolField,
    preserveFormatting: boolField,
    closeComments: boolField,
    publishAsVkRepost: boolField,
    longTextMode: z.enum(['split', 'truncate_read_more']),
    vkVideoAsLink: boolField,

    // Schedule / delay
    delayMinutes: z.number().int().min(0).max(10080),
    scheduleEnabled: boolField,
    scheduleTimezone: z.string().min(1),
    scheduleDays: z.array(z.number().int().min(0).max(6)),
    scheduleIntervals: z.array(
      z.object({
        start: z.string(),
        end: z.string(),
      }),
    ),

    activate: z.boolean(),
  })
  .superRefine((values, ctx) => {
    if (values.targetChannelIds.includes(values.sourceChannelId)) {
      ctx.addIssue({
        code: 'custom',
        path: ['targetChannelIds'],
        message: 'sourceEqualsTarget',
      });
    }
    const unique = new Set(values.targetChannelIds);
    if (unique.size !== values.targetChannelIds.length) {
      ctx.addIssue({
        code: 'custom',
        path: ['targetChannelIds'],
        message: 'targetsDuplicate',
      });
    }
  });

export type CrosspostRuleFormValues = z.infer<typeof crosspostRuleFormSchema>;
