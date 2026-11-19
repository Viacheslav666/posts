import { describe, expect, it } from 'vitest';

import { defaultFormValues } from './config';
import { antispamRuleFormSchema } from './schemas';

describe('antispamRuleFormSchema', () => {
  it('accepts dryRun without delete/ban/reply', () => {
    const result = antispamRuleFormSchema.safeParse({
      ...defaultFormValues('c1', 'vk'),
      name: 'Dry',
      actionDelete: false,
      banEnabled: false,
      replyEnabled: false,
      dryRun: true,
    });
    expect(result.success).toBe(true);
  });

  it('rejects when no actions and not dryRun', () => {
    const result = antispamRuleFormSchema.safeParse({
      ...defaultFormValues('c1', 'vk'),
      name: 'No action',
      actionDelete: false,
      banEnabled: false,
      replyEnabled: false,
      dryRun: false,
    });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues.some((i) => i.message === 'actionRequired')).toBe(true);
    }
  });

  it('rejects empty channel', () => {
    const result = antispamRuleFormSchema.safeParse({
      ...defaultFormValues('', 'vk'),
      name: 'X',
      channelId: '',
    });
    expect(result.success).toBe(false);
  });
});
