import { describe, expect, it } from 'vitest';

import { defaultFormValues } from './config';
import { suggestSettingsFormSchema, vkIdsFromText, vkIdsToText } from './schemas';

describe('suggest settings form schema', () => {
  it('accepts default form values', () => {
    const parsed = suggestSettingsFormSchema.safeParse(
      defaultFormValues('019a0000-chan-7000-8000-000000000001'),
    );
    expect(parsed.success).toBe(true);
  });

  it('rejects empty channel id', () => {
    const parsed = suggestSettingsFormSchema.safeParse({
      ...defaultFormValues(),
      channelId: '',
    });
    expect(parsed.success).toBe(false);
  });

  it('rejects invalid mode', () => {
    const parsed = suggestSettingsFormSchema.safeParse({
      ...defaultFormValues('c1'),
      mode: 'wat',
    });
    expect(parsed.success).toBe(false);
  });

  it('treats a cleared numeric limit as unlimited (0)', () => {
    const parsed = suggestSettingsFormSchema.safeParse({
      ...defaultFormValues('c1'),
      perAuthorHour: Number.NaN,
      dedupSimilarityThreshold: Number.NaN,
    });
    expect(parsed.success).toBe(true);
    if (parsed.success) {
      expect(parsed.data.perAuthorHour).toBe(0);
      expect(parsed.data.dedupSimilarityThreshold).toBe(0.8);
    }
  });

  it('rejects negative limits', () => {
    const parsed = suggestSettingsFormSchema.safeParse({
      ...defaultFormValues('c1'),
      perAuthorHour: -1,
    });
    expect(parsed.success).toBe(false);
  });

  it('parses vk ids from free text', () => {
    expect(vkIdsFromText('1, 2\n3 ;4')).toEqual(['1', '2', '3', '4']);
    expect(vkIdsToText(['1', '2'])).toBe('1, 2');
  });

  it('caps first comment length at 4000', () => {
    const parsed = suggestSettingsFormSchema.safeParse({
      ...defaultFormValues('c1'),
      firstCommentText: 'x'.repeat(4001),
    });
    expect(parsed.success).toBe(false);
  });
});
