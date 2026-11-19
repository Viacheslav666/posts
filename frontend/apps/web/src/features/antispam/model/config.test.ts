import { describe, expect, it } from 'vitest';

import {
  buildConfig,
  collectWordListUsage,
  configAsApiBlob,
  defaultFormValues,
  formValuesFromRule,
  previewReplyTemplate,
  withConfigDefaults,
} from './config';
import { antispamRuleFormSchema } from './schemas';
import { sectionsForNetwork } from './types';

describe('antispam config', () => {
  it('applies RuleConfig defaults', () => {
    const config = withConfigDefaults({});
    expect(config.actions.delete).toBe(true);
    expect(config.actions.ban.days).toBe(7);
    expect(config.actions.reply.template).toContain('{reasons}');
    expect(config.filters.caps.ratio).toBe(0.7);
    expect(config.filters.caps.min_letters).toBe(5);
    expect(config.filters.repeat.window_hours).toBe(12);
    expect(config.exceptions.exclude_own_community).toBe(true);
    expect(config.filters.attachments.photo).toBe(false);
  });

  it('merges partial raw config', () => {
    const config = withConfigDefaults({
      filters: {
        keywords: { black: ['спам'], black_list_ids: ['list-1'] },
        caps: { enabled: true, ratio: 0.9 },
        links: { all: true },
      },
      actions: { delete: false, ban: { enabled: true, days: 3 } },
    });
    expect(config.filters.keywords.black).toEqual(['спам']);
    expect(config.filters.keywords.black_list_ids).toEqual(['list-1']);
    expect(config.filters.keywords.white).toEqual([]);
    expect(config.filters.caps.enabled).toBe(true);
    expect(config.filters.caps.ratio).toBe(0.9);
    expect(config.filters.caps.min_letters).toBe(5);
    expect(config.filters.links.all).toBe(true);
    expect(config.actions.delete).toBe(false);
    expect(config.actions.ban.enabled).toBe(true);
    expect(config.actions.ban.days).toBe(3);
  });

  it('round-trips form values through buildConfig', () => {
    const values = {
      ...defaultFormValues('chan-1', 'vk'),
      name: 'VK stop',
      blackWords: ['спам'],
      linksAll: true,
      capsEnabled: true,
      actionDelete: true,
      dryRun: true,
      sections: ['wall_comment' as const],
    };
    const config = buildConfig(values);
    expect(config.filters.keywords.black).toEqual(['спам']);
    expect(config.filters.links.all).toBe(true);
    expect(config.filters.caps.enabled).toBe(true);

    const restored = formValuesFromRule({
      id: 'r1',
      name: values.name,
      channelId: values.channelId,
      status: 'active',
      dryRun: true,
      sections: values.sections,
      priority: 50,
      config: config as never,
      version: 1,
    });
    expect(restored.blackWords).toEqual(['спам']);
    expect(restored.linksAll).toBe(true);
    expect(restored.dryRun).toBe(true);
    expect(restored.sections).toEqual(['wall_comment']);
    expect(restored.priority).toBe(50);
    expect(restored.keywordMatchMode).toBe('substring');
  });

  it('round-trips whole-word match mode', () => {
    const values = {
      ...defaultFormValues('chan-1', 'vk'),
      name: 'Whole',
      keywordMatchMode: 'whole_word' as const,
      blackWords: ['спам'],
    };
    const restored = formValuesFromRule({
      name: 'Whole',
      config: buildConfig(values) as never,
    });
    expect(restored.keywordMatchMode).toBe('whole_word');
    expect(restored.blackWords).toEqual(['спам']);
  });

  it('keeps config keys the form does not edit', () => {
    const preserved = {
      filters: {
        keywords: { black: ['old'], match: 'substring', legacy_flag: true },
      },
      custom: { keep: 1 },
    };
    const values = {
      ...defaultFormValues('chan-1', 'vk'),
      name: 'Keep',
      blackWords: ['спам'],
      domainWhitelist: ['vk.com'],
      adminIds: ['1'],
    };
    const blob = configAsApiBlob(buildConfig(values), preserved) as unknown as {
      filters: { keywords: { black: string[]; legacy_flag?: boolean }; links: { domain_whitelist: string[] } };
      exceptions: { admin_ids: string[] };
      custom?: { keep: number };
    };
    expect(blob.filters.keywords.black).toEqual(['спам']);
    expect(blob.filters.keywords.legacy_flag).toBe(true);
    expect(blob.filters.links.domain_whitelist).toEqual(['vk.com']);
    expect(blob.exceptions.admin_ids).toEqual(['1']);
    expect(blob.custom).toEqual({ keep: 1 });
  });

  it('requires at least one section in schema', () => {
    const result = antispamRuleFormSchema.safeParse({
      ...defaultFormValues('chan-1', 'vk'),
      name: 'Bad',
      sections: [],
    });
    expect(result.success).toBe(false);
  });

  it('requires name', () => {
    const result = antispamRuleFormSchema.safeParse({
      ...defaultFormValues('chan-1', 'vk'),
      name: '  ',
    });
    expect(result.success).toBe(false);
  });

  it('accepts valid form values', () => {
    const result = antispamRuleFormSchema.safeParse({
      ...defaultFormValues('chan-1', 'vk'),
      name: 'OK',
      blackWords: ['спам'],
    });
    expect(result.success).toBe(true);
  });

  it('previews reply template with reasons', () => {
    expect(previewReplyTemplate('Удалено: {reasons}', ['ссылка', 'CAPS'])).toBe(
      'Удалено: ссылка, CAPS',
    );
    expect(previewReplyTemplate('Удалено: {reasons}', [])).toBe('Удалено: спам');
  });

  it('counts word-list usage across rules', () => {
    const usage = collectWordListUsage(
      [
        {
          id: '1',
          config: {
            filters: { keywords: { black_list_ids: ['L1'], white_list_ids: [] } },
          } as never,
        },
        {
          id: '2',
          config: {
            filters: { keywords: { black_list_ids: ['L1'], white_list_ids: ['L1'] } },
          } as never,
        },
      ],
      'L1',
    );
    expect(usage).toEqual({ black: 2, white: 1 });
  });
});

describe('antispam sections', () => {
  it('returns network-specific sections', () => {
    expect(sectionsForNetwork('vk')).toContain('wall_comment');
    expect(sectionsForNetwork('tg')).toEqual(['tg_discussion', 'tg_group']);
    expect(sectionsForNetwork('max')).toContain('max_comment');
    expect(sectionsForNetwork('ok')).toEqual([]);
  });
});
