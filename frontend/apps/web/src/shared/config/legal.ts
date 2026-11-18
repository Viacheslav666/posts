/**
 * Public legal / company requisites from env.
 * Placeholders only — never invent real company data.
 */
export type LegalRequisites = {
  companyName: string;
  inn: string;
  ogrn: string;
  address: string;
  email: string;
  phone: string;
};

function envOrPlaceholder(key: string): string {
  const value = process.env[key]?.trim();
  return value && value.length > 0 ? value : '';
}

export function getLegalRequisites(): LegalRequisites {
  return {
    companyName: envOrPlaceholder('NEXT_PUBLIC_LEGAL_COMPANY_NAME'),
    inn: envOrPlaceholder('NEXT_PUBLIC_LEGAL_INN'),
    ogrn: envOrPlaceholder('NEXT_PUBLIC_LEGAL_OGRN'),
    address: envOrPlaceholder('NEXT_PUBLIC_LEGAL_ADDRESS'),
    email: envOrPlaceholder('NEXT_PUBLIC_LEGAL_EMAIL'),
    phone: envOrPlaceholder('NEXT_PUBLIC_LEGAL_PHONE'),
  };
}

export function hasLegalValue(value: string): boolean {
  return value.trim().length > 0;
}
