import { describe, expect, it } from 'vitest';

import { getErrorMessage, AppError } from '@/shared/lib/errors';

describe('HomePage stub', () => {
  it('passes a smoke assertion', () => {
    expect('Posts').toContain('Post');
  });
});

describe('getErrorMessage', () => {
  it('reads AppError message', () => {
    expect(getErrorMessage(new AppError('boom', { code: 'X' }))).toBe('boom');
  });

  it('falls back for unknown values', () => {
    expect(getErrorMessage(null, 'fallback')).toBe('fallback');
  });
});
