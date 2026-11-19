import { z } from 'zod';

import { ALL_SECTIONS, ATTACHMENT_KEYS } from './types';

const boolField = z.boolean();

const attachmentShape = ATTACHMENT_KEYS.reduce(
  (acc, key) => {
    acc[key] = boolField;
    return acc;
  },
  {} as Record<(typeof ATTACHMENT_KEYS)[number], typeof boolField>,
);

export const antispamRuleFormSchema = z
  .object({
    name: z.string().trim().min(1, 'nameRequired').max(200, 'nameTooLong'),
    channelId: z.string().min(1, 'channelRequired'),
    sections: z.array(z.enum(ALL_SECTIONS as [string, ...string[]])).min(1, 'sectionsRequired'),
    priority: z.number().int().min(0).max(10_000),
    dryRun: boolField,
    activate: boolField,

    // Keywords
    blackWords: z.array(z.string()),
    whiteWords: z.array(z.string()),
    blackListIds: z.array(z.string()),
    whiteListIds: z.array(z.string()),
    keywordMatchMode: z.enum(['whole_word', 'substring']),

    // Links
    linksAll: boolField,
    linksVk: boolField,
    domainWhitelist: z.array(z.string()),

    // Attachments
    attachments: z.object(attachmentShape),

    // CAPS
    capsEnabled: boolField,
    capsRatio: z.number().min(0).max(1),
    capsMinLetters: z.number().int().min(1).max(10_000),

    // Repeat
    repeatEnabled: boolField,
    repeatWindowHours: z.number().int().min(1).max(168),

    // Authors
    authorsCommunities: boolField,
    authorsNonMembers: boolField,
    authorsSenderChat: boolField,

    // Context
    contextUnderAds: boolField,
    contextUnderPinned: boolField,

    // Actions
    actionDelete: boolField,
    banEnabled: boolField,
    banDays: z.number().int().min(1).max(366),
    banReason: z.string().max(500),
    banCommentVisible: boolField,
    replyEnabled: boolField,
    replyTemplate: z.string().max(2000),

    // Exceptions
    adminIds: z.array(z.string()),
    userWhitelist: z.array(z.string()),
    excludeOwnCommunity: boolField,
  })
  .superRefine((values, ctx) => {
    if (!values.actionDelete && !values.banEnabled && !values.replyEnabled && !values.dryRun) {
      ctx.addIssue({
        code: 'custom',
        path: ['actionDelete'],
        message: 'actionRequired',
      });
    }
  });

export type AntispamRuleFormValues = z.infer<typeof antispamRuleFormSchema>;
