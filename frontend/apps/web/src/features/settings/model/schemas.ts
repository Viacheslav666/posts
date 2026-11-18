import { z } from 'zod';

export const profileSchema = z.object({
  name: z.string().trim().min(1, 'nameRequired').max(120, 'nameMax'),
  timezone: z.string().trim().min(1, 'timezoneRequired'),
  locale: z.enum(['ru', 'en']),
});

export type ProfileFormValues = z.infer<typeof profileSchema>;

export const passwordSchema = z
  .object({
    currentPassword: z.string().min(1, 'currentRequired'),
    newPassword: z.string().min(10, 'passwordMin'),
    confirmPassword: z.string().min(10, 'passwordMin'),
  })
  .refine((values) => values.newPassword === values.confirmPassword, {
    message: 'passwordMismatch',
    path: ['confirmPassword'],
  });

export type PasswordFormValues = z.infer<typeof passwordSchema>;
