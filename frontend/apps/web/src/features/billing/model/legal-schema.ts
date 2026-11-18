import { z } from 'zod';

import { isValidInn, normalizeInn } from '@/shared/lib/inn';
import { isValidKpp, normalizeKpp } from '@/shared/lib/kpp';

export const legalRequisitesSchema = z.object({
  inn: z
    .string()
    .trim()
    .min(1, 'innRequired')
    .transform(normalizeInn)
    .refine(isValidInn, 'innInvalid'),
  kpp: z
    .string()
    .trim()
    .min(1, 'kppRequired')
    .transform(normalizeKpp)
    .refine(isValidKpp, 'kppInvalid'),
  ogrn: z.string().trim().min(1, 'ogrnRequired').max(15, 'ogrnInvalid'),
  companyName: z.string().trim().min(1, 'companyRequired').max(300, 'companyMax'),
  address: z.string().trim().min(1, 'addressRequired').max(500, 'addressMax'),
});

export type LegalRequisitesValues = z.infer<typeof legalRequisitesSchema>;

const storageKey = (workspaceId: string) => `posts.billing.legal.${workspaceId}`;

/** Local draft until BE-31 company/invoice API (FE-10b). */
export function loadLegalDraft(workspaceId: string): LegalRequisitesValues | null {
  if (typeof window === 'undefined') return null;
  try {
    const raw = window.sessionStorage.getItem(storageKey(workspaceId));
    if (!raw) return null;
    const parsed = legalRequisitesSchema.safeParse(JSON.parse(raw));
    return parsed.success ? parsed.data : null;
  } catch {
    return null;
  }
}

export function saveLegalDraft(workspaceId: string, values: LegalRequisitesValues): void {
  if (typeof window === 'undefined') return;
  window.sessionStorage.setItem(storageKey(workspaceId), JSON.stringify(values));
}
