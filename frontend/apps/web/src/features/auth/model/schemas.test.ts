import { describe, expect, it } from 'vitest';

import {
  forgotPasswordSchema,
  loginSchema,
  registerSchema,
  resetPasswordSchema,
  twoFactorSchema,
  verifyEmailSchema,
} from './schemas';

describe('auth schemas', () => {
  it('accepts a valid login payload', () => {
    const parsed = loginSchema.safeParse({
      email: 'user@posts.app',
      password: 'secret',
    });
    expect(parsed.success).toBe(true);
  });

  it('rejects invalid email on login', () => {
    const parsed = loginSchema.safeParse({
      email: 'not-an-email',
      password: 'secret',
    });
    expect(parsed.success).toBe(false);
  });

  it('requires strong password and terms on register', () => {
    const weak = registerSchema.safeParse({
      name: 'Anna',
      email: 'anna@posts.app',
      password: 'short',
      confirmPassword: 'short',
      acceptTerms: true,
    });
    expect(weak.success).toBe(false);

    const tooShort = registerSchema.safeParse({
      name: 'Anna',
      email: 'anna@posts.app',
      password: 'Secret12',
      confirmPassword: 'Secret12',
      acceptTerms: true,
    });
    expect(tooShort.success).toBe(false);

    const ok = registerSchema.safeParse({
      name: 'Anna',
      email: 'anna@posts.app',
      password: 'Secret12ab',
      confirmPassword: 'Secret12ab',
      acceptTerms: true,
    });
    expect(ok.success).toBe(true);
  });

  it('validates verification token and 2FA codes', () => {
    expect(verifyEmailSchema.safeParse({ token: '123456' }).success).toBe(true);
    expect(verifyEmailSchema.safeParse({ token: '12' }).success).toBe(false);
    expect(twoFactorSchema.safeParse({ code: '654321' }).success).toBe(true);
  });

  it('validates forgot and reset flows', () => {
    expect(forgotPasswordSchema.safeParse({ email: 'a@b.co' }).success).toBe(true);
    expect(
      resetPasswordSchema.safeParse({
        password: 'Secret12ab',
        confirmPassword: 'Secret12ab',
      }).success,
    ).toBe(true);
    expect(
      resetPasswordSchema.safeParse({
        password: 'Secret12ab',
        confirmPassword: 'Other12ab',
      }).success,
    ).toBe(false);
  });
});
