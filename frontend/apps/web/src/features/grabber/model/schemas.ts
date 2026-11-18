import { z } from 'zod';

import { DEFAULT_ATTRIBUTION_TEMPLATE, SCAN_INTERVALS } from './types';
import { isValidSourceId, normalizeSourceIds } from './source-ids';

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
    scheduleMode: z.enum(['immediate', 'delay', 'slot']),
    publishDelayMinutes: z.number().int().min(0).max(10080),
    excludeAds: z.boolean(),
    excludeReposts: z.boolean(),
    keywordIncludeText: z.string(),
    keywordExcludeText: z.string(),
    attributionEnabled: z.literal(true),
    attributionPlacement: z.enum(['footer', 'first_comment', 'both']),
    attributionTemplate: z.string().trim().min(1, 'attributionRequired'),
    userResponsibilityAccepted: z.boolean(),
    activate: z.boolean(),
    rateLimitPerHour: z.number().int().min(1).max(60),
    rateLimitPerDay: z.number().int().min(1).max(500),
  })
  .superRefine((values, ctx) => {
    if (values.type === 'grabber') {
      const ids = normalizeSourceIds(values.sourceIdsText ?? '');
      if (ids.length < 1) {
        ctx.addIssue({ code: 'custom', path: ['sourceIdsText'], message: 'sourcesRequired' });
      }
      if (ids.length > 50) {
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
  });

export type GrabberRuleFormValues = z.infer<typeof grabberRuleFormSchema>;

export function keywordsFromText(raw: string | undefined): string[] {
  return (raw ?? '')
    .split(/[,;\n]+/)
    .map((part) => part.trim())
    .filter(Boolean)
    .slice(0, 50);
}

export { DEFAULT_ATTRIBUTION_TEMPLATE };
