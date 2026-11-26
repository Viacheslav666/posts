import { readFileSync } from 'node:fs';
import path from 'node:path';

import { afterEach, describe, expect, it } from 'vitest';

import messages from '@/messages/ru.json';

import {
  getLegalRequisites,
  LEGAL_FIELDS,
  legalRequisiteRows,
  type LegalField,
} from './legal';

const ENV_KEYS = [
  'NEXT_PUBLIC_LEGAL_COMPANY_NAME',
  'NEXT_PUBLIC_LEGAL_INN',
  'NEXT_PUBLIC_LEGAL_OGRN',
  'NEXT_PUBLIC_LEGAL_ADDRESS',
  'NEXT_PUBLIC_LEGAL_EMAIL',
  'NEXT_PUBLIC_LEGAL_PHONE',
  'NEXT_PUBLIC_LEGAL_TAX_STATUS',
] as const;

const LABELS: Record<LegalField, string> = {
  companyName: 'Исполнитель',
  inn: 'ИНН',
  taxStatus: 'Статус',
  ogrn: 'ОГРН',
  address: 'Город',
  email: 'Эл. почта',
  phone: 'Телефон',
};

const webRoot = path.resolve(__dirname, '../../..');

function readWeb(relativePath: string): string {
  return readFileSync(path.join(webRoot, relativePath), 'utf8');
}

function clearLegalEnv(): void {
  for (const key of ENV_KEYS) {
    delete process.env[key];
  }
}

afterEach(() => {
  clearLegalEnv();
});

describe('legal requisites', () => {
  it('returns empty strings when env is unset', () => {
    clearLegalEnv();
    expect(getLegalRequisites()).toEqual({
      companyName: '',
      inn: '',
      taxStatus: '',
      ogrn: '',
      address: '',
      email: '',
      phone: '',
    });
    expect(legalRequisiteRows(getLegalRequisites(), LABELS)).toEqual([]);
  });

  it('hides empty and whitespace-only rows, including OGRN', () => {
    process.env.NEXT_PUBLIC_LEGAL_COMPANY_NAME = 'Example Seller';
    process.env.NEXT_PUBLIC_LEGAL_INN = '000000000000';
    process.env.NEXT_PUBLIC_LEGAL_TAX_STATUS =
      'Плательщик налога на профессиональный доход (самозанятый)';
    process.env.NEXT_PUBLIC_LEGAL_OGRN = '   ';
    process.env.NEXT_PUBLIC_LEGAL_ADDRESS = 'Example City';
    process.env.NEXT_PUBLIC_LEGAL_EMAIL = 'seller@example.com';
    process.env.NEXT_PUBLIC_LEGAL_PHONE = '+70000000000';

    const rows = legalRequisiteRows(getLegalRequisites(), LABELS);

    expect(rows.map((row) => row.field)).toEqual([
      'companyName',
      'inn',
      'taxStatus',
      'address',
      'email',
      'phone',
    ]);
    expect(rows.find((row) => row.field === 'ogrn')).toBeUndefined();
    expect(rows.map((row) => row.label)).toEqual([
      'Исполнитель',
      'ИНН',
      'Статус',
      'Город',
      'Эл. почта',
      'Телефон',
    ]);
    expect(rows.map((row) => row.value).join(' ')).not.toMatch(/указано|placeholder|конфигурац/i);
  });

  it('reads values at call time, including tax status', () => {
    process.env.NEXT_PUBLIC_LEGAL_TAX_STATUS = 'Плательщик НПД';
    process.env.NEXT_PUBLIC_LEGAL_INN = '000000000000';
    expect(getLegalRequisites().taxStatus).toBe('Плательщик НПД');
    expect(getLegalRequisites().inn).toBe('000000000000');

    process.env.NEXT_PUBLIC_LEGAL_TAX_STATUS = 'Самозанятый';
    process.env.NEXT_PUBLIC_LEGAL_INN = '000000000001';
    expect(getLegalRequisites().taxStatus).toBe('Самозанятый');
    expect(getLegalRequisites().inn).toBe('000000000001');
  });

  it('keeps a filled OGRN in field order', () => {
    process.env.NEXT_PUBLIC_LEGAL_COMPANY_NAME = 'Example Seller';
    process.env.NEXT_PUBLIC_LEGAL_OGRN = '0000000000000';
    const rows = legalRequisiteRows(getLegalRequisites(), LABELS);
    expect(rows.map((row) => row.field)).toEqual(['companyName', 'ogrn']);
    expect(LEGAL_FIELDS.indexOf('ogrn')).toBeGreaterThan(LEGAL_FIELDS.indexOf('taxStatus'));
  });

  it('reads env dynamically and renders legal routes dynamically', () => {
    const source = readWeb('src/shared/config/legal.ts');
    expect(source).toContain('process.env[key]');
    expect(source).not.toMatch(/process\.env\.NEXT_PUBLIC_LEGAL_/);

    for (const page of [
      'src/app/(public)/page.tsx',
      'src/app/(public)/pricing/page.tsx',
      'src/app/(public)/legal/contacts/page.tsx',
      'src/app/(public)/legal/offer/page.tsx',
    ]) {
      expect(readWeb(page)).toContain("export const dynamic = 'force-dynamic'");
    }
  });

  it('does not ship draft placeholders or seller secrets in public copy', () => {
    const json = JSON.stringify(messages);
    expect(json).not.toContain('Текст утверждается');
    expect(json).not.toContain('Реквизиты оператора сервиса задаются конфигурацией окружения');
    expect(json).toContain('Тарифы для фрилансеров, команд и агентств. Годовая оплата со скидкой 20%.');
    expect(json).toContain('Услуга оказывается онлайн: доступ к тарифу открывается в личном кабинете сразу после оплаты.');

    const offer = readWeb('src/features/landing/components/public-offer.tsx');
    expect(offer).not.toMatch(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);
    expect(offer).not.toMatch(/\+7\d{10}/);
    expect(offer).toContain('ЮKassa');
    expect(offer).toContain('Мой налог');
    expect(offer).toContain('Физическая доставка не осуществляется');
    expect(offer).toContain('/pricing');
    expect(offer).toContain('/legal/privacy');
    expect(offer).toContain('/legal/personal-data');
  });
});
