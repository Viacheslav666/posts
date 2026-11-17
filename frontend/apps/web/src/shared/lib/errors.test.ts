import { describe, expect, it } from 'vitest';

import { AppError, getErrorMessage } from './errors';

describe('getErrorMessage', () => {
  it('reads AppError message', () => {
    expect(getErrorMessage(new AppError('boom', { code: 'X' }))).toBe('boom');
  });

  it('falls back for unknown values', () => {
    expect(getErrorMessage(null, 'fallback')).toBe('fallback');
  });
});
