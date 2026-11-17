import { describe, expect, it } from 'vitest';

import {
  ApiProblemError,
  AppError,
  getErrorMessage,
  isTwoFactorRequired,
  toApiProblemError,
  TWO_FACTOR_REQUIRED_CODE,
} from './errors';

describe('getErrorMessage', () => {
  it('reads AppError message', () => {
    expect(getErrorMessage(new AppError('boom', { code: 'X' }))).toBe('boom');
  });

  it('falls back for unknown values', () => {
    expect(getErrorMessage(null, 'fallback')).toBe('fallback');
  });
});

describe('ApiProblemError', () => {
  it('maps ProblemDetail fields', () => {
    const error = new ApiProblemError({
      title: 'Unauthorized',
      detail: 'Invalid email or password',
      status: 401,
      code: 'unauthorized',
    });
    expect(error.message).toBe('Invalid email or password');
    expect(error.code).toBe('unauthorized');
    expect(error.status).toBe(401);
  });

  it('detects provisional two-factor code', () => {
    const error = toApiProblemError({
      code: TWO_FACTOR_REQUIRED_CODE,
      detail: 'TOTP code required',
      status: 401,
    });
    expect(isTwoFactorRequired(error)).toBe(true);
  });
});
