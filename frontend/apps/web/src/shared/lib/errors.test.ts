import { describe, expect, it } from 'vitest';

import {
  ApiProblemError,
  AppError,
  getErrorMessage,
  INSUFFICIENT_PERMISSIONS_CODE,
  isInsufficientPermissions,
  isPlanLimitExceeded,
  isTwoFactorRequired,
  PLAN_LIMIT_EXCEEDED_CODE,
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

  it('detects insufficient_permissions', () => {
    const error = toApiProblemError({
      code: INSUFFICIENT_PERMISSIONS_CODE,
      detail: 'Forbidden',
      status: 403,
    });
    expect(isInsufficientPermissions(error)).toBe(true);
  });

  it('detects plan_limit_exceeded', () => {
    const error = toApiProblemError({
      code: PLAN_LIMIT_EXCEEDED_CODE,
      detail: 'Channel limit reached',
      status: 402,
    });
    expect(isPlanLimitExceeded(error)).toBe(true);
  });
});

