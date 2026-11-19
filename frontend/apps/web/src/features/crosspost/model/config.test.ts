import { describe, expect, it } from 'vitest';

import {
  buildCrosspostConfig,
  defaultFormValues,
  formValuesFromRule,
  withConfigDefaults,
} from './config';
import { crosspostRuleFormSchema } from './schemas';

describe('crosspost config', () => {
  it('applies CrosspostConfigSupport defaults', () => {
    const config = withConfigDefaults({});
    expect(config.filters.exclude_ads).toBe(true);
    expect(config.filters.exclude_clips).toBe(true);
    expect(config.filters.exclude_links).toBe(false);
    expect(config.filters.include_reposts).toBe(true);
    expect(config.transform.preserve_formatting).toBe(true);
    expect(config.transform.long_text_mode).toBe('split');
    expect(config.transform.vk_video_as_link).toBe(true);
    expect(config.delay_minutes).toBe(0);
    expect(config.include_posts_published_via_posts).toBe(false);
    expect(config.schedule.timezone).toBe('Europe/Moscow');
    expect(config.schedule.days_of_week).toEqual([1, 2, 3, 4, 5, 6, 7]);
  });

  it('round-trips form values through buildCrosspostConfig', () => {
    const values = {
      ...defaultFormValues('src-1'),
      name: 'VK → TG',
      targetChannelIds: ['tg-1', 'max-1'],
      excludeLinks: true,
      removeHashtags: true,
      delayMinutes: 15,
      scheduleEnabled: true,
      scheduleDays: [1, 2, 3],
      scheduleIntervals: [{ start: '09:00', end: '18:00' }],
      keywordInclude: ['новости'],
      keywordExclude: ['реклама'],
      longTextMode: 'truncate_read_more' as const,
    };
    const config = buildCrosspostConfig(values);
    expect(config.filters.exclude_links).toBe(true);
    expect(config.filters.keyword_include).toEqual(['новости']);
    expect(config.transform.remove_hashtags).toBe(true);
    expect(config.transform.long_text_mode).toBe('truncate_read_more');
    expect(config.delay_minutes).toBe(15);
    expect(config.schedule.enabled).toBe(true);
    expect(config.schedule.hours_from).toBe(9);
    expect(config.schedule.hours_to).toBe(18);

    const restored = formValuesFromRule({
      id: 'r1',
      name: values.name,
      sourceChannelId: values.sourceChannelId,
      status: 'active',
      targets: values.targetChannelIds.map((id) => ({ targetChannelId: id })),
      config: config as never,
      version: 1,
    });
    expect(restored.excludeLinks).toBe(true);
    expect(restored.removeHashtags).toBe(true);
    expect(restored.delayMinutes).toBe(15);
    expect(restored.keywordInclude).toEqual(['новости']);
    expect(restored.longTextMode).toBe('truncate_read_more');
    expect(restored.scheduleEnabled).toBe(true);
    expect(restored.targetChannelIds).toEqual(['tg-1', 'max-1']);
  });

  it('rejects source equal to a target in schema', () => {
    const result = crosspostRuleFormSchema.safeParse({
      ...defaultFormValues('same'),
      name: 'Bad',
      sourceChannelId: 'same',
      targetChannelIds: ['same', 'other'],
    });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues.some((i) => i.message === 'sourceEqualsTarget')).toBe(true);
    }
  });

  it('requires at least one target', () => {
    const result = crosspostRuleFormSchema.safeParse({
      ...defaultFormValues('src'),
      name: 'No targets',
      targetChannelIds: [],
    });
    expect(result.success).toBe(false);
  });
});
