import { describe, expect, it } from 'vitest';

import { legalRequisitesSchema } from './legal-schema';

describe('legalRequisitesSchema', () => {
  it('accepts valid legal entity requisites', () => {
    const result = legalRequisitesSchema.safeParse({
      inn: '7707083893',
      kpp: '773601001',
      ogrn: '1027700132195',
      companyName: 'ООО Ромашка',
      address: 'г. Москва, ул. Примерная, д. 1',
    });
    expect(result.success).toBe(true);
  });

  it('rejects invalid INN and KPP', () => {
    const result = legalRequisitesSchema.safeParse({
      inn: '1234567890',
      kpp: '000000000',
      ogrn: '1027700132195',
      companyName: 'ООО Ромашка',
      address: 'г. Москва',
    });
    expect(result.success).toBe(false);
    if (!result.success) {
      const fields = result.error.issues.map((issue) => issue.path[0]);
      expect(fields).toContain('inn');
      expect(fields).toContain('kpp');
    }
  });
});
