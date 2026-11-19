import { describe, expect, it } from 'vitest';

import { defaultFormValues } from './config';
import { crosspostRuleFormSchema } from './schemas';

describe('crosspostRuleFormSchema', () => {
  it('rejects empty name and missing targets', () => {
    const result = crosspostRuleFormSchema.safeParse({
      ...defaultFormValues('source-1'),
      name: '',
      targetChannelIds: [],
    });
    expect(result.success).toBe(false);
  });

  it('rejects when source equals a target', () => {
    const result = crosspostRuleFormSchema.safeParse({
      ...defaultFormValues('source-1'),
      name: 'Loop',
      sourceChannelId: 'source-1',
      targetChannelIds: ['source-1', 'target-2'],
    });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues.some((issue) => issue.message === 'sourceEqualsTarget')).toBe(
        true,
      );
    }
  });

  it('accepts valid VK → TG + Max payload', () => {
    const result = crosspostRuleFormSchema.safeParse({
      ...defaultFormValues('vk-1'),
      name: 'VK → TG + Max',
      sourceChannelId: 'vk-1',
      targetChannelIds: ['tg-1', 'max-1'],
    });
    expect(result.success).toBe(true);
  });
});
