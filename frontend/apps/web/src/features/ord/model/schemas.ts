import { z } from 'zod';

import { isValidInn } from '@/shared/lib/inn';

export const ORD_PROVIDERS = ['vk_ord', 'yandex_ord'] as const;

export const ordConnectionSchema = z.object({
  provider: z.enum(ORD_PROVIDERS),
  apiKey: z.string().optional(),
  publisherInn: z.string().min(1, { message: 'publisherInnRequired' }),
  publisherName: z.string().min(1, { message: 'publisherNameRequired' }),
  orgType: z.string().min(1, { message: 'orgTypeRequired' }),
  phone: z.string().optional(),
  responsibilityAccepted: z.boolean().refine((value) => value === true, {
    message: 'responsibilityRequired',
  }),
});

export type OrdConnectionFormValues = z.infer<typeof ordConnectionSchema>;

export const ordContractorSchema = z.object({
  inn: z
    .string()
    .min(1, { message: 'innRequired' })
    .refine((value) => isValidInn(value), { message: 'ad_inn_invalid' }),
  name: z.string().min(1, { message: 'nameRequired' }),
  orgType: z.string().min(1, { message: 'orgTypeRequired' }),
  kpp: z.string().optional(),
  phone: z.string().optional(),
  address: z.string().optional(),
  countryOksm: z.string().optional(),
});

export type OrdContractorFormValues = z.infer<typeof ordContractorSchema>;