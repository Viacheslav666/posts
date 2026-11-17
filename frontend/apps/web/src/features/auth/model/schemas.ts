import { z } from 'zod';

const emailSchema = z
  .string()
  .trim()
  .min(1, 'emailRequired')
  .email('emailInvalid');

/** Matches OpenAPI RegisterRequest / PasswordResetRequest minLength=10. */
const passwordSchema = z
  .string()
  .min(1, 'passwordRequired')
  .min(10, 'passwordMin')
  .regex(/[A-Za-z]/, 'passwordLetter')
  .regex(/[0-9]/, 'passwordDigit');

export const loginSchema = z.object({
  email: emailSchema,
  password: z.string().min(1, 'passwordRequired'),
});

export type LoginValues = z.infer<typeof loginSchema>;

export const registerSchema = z
  .object({
    name: z.string().trim().min(1, 'nameRequired').max(120, 'nameMax'),
    email: emailSchema,
    password: passwordSchema,
    confirmPassword: z.string().min(1, 'confirmRequired'),
    acceptTerms: z
      .boolean()
      .refine((value) => value === true, { message: 'termsRequired' }),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: 'passwordMismatch',
    path: ['confirmPassword'],
  });

export type RegisterValues = z.infer<typeof registerSchema>;

export const verifyEmailSchema = z.object({
  token: z
    .string()
    .trim()
    .min(1, 'codeRequired')
    .min(6, 'codeFormat'),
});

export type VerifyEmailValues = z.infer<typeof verifyEmailSchema>;

export const forgotPasswordSchema = z.object({
  email: emailSchema,
});

export type ForgotPasswordValues = z.infer<typeof forgotPasswordSchema>;

export const resetPasswordSchema = z
  .object({
    password: passwordSchema,
    confirmPassword: z.string().min(1, 'confirmRequired'),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: 'passwordMismatch',
    path: ['confirmPassword'],
  });

export type ResetPasswordValues = z.infer<typeof resetPasswordSchema>;

export const twoFactorSchema = z.object({
  code: z
    .string()
    .trim()
    .min(1, 'codeRequired')
    .regex(/^\d{6}$/, 'codeFormat'),
});

export type TwoFactorValues = z.infer<typeof twoFactorSchema>;
