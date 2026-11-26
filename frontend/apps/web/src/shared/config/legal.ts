/**
 * Public legal requisites for the self-employed seller.
 *
 * Read on each call via process.env[key]. Next.js inlines static
 * process.env.NEXT_PUBLIC_* at build time, which would ignore values
 * set later in the container env. Routes that render these values must
 * stay dynamic so a prerendered page does not bake empty build-time data.
 *
 * Never commit real names, INN, phone or email. Empty strings are hidden.
 */

export const LEGAL_FIELDS = [
  'companyName',
  'inn',
  'taxStatus',
  'ogrn',
  'address',
  'email',
  'phone',
] as const;

export type LegalField = (typeof LEGAL_FIELDS)[number];

export type LegalRequisites = Record<LegalField, string>;

export type LegalRequisiteRow = {
  field: LegalField;
  label: string;
  value: string;
};

const LEGAL_ENV_KEYS: Record<LegalField, string> = {
  companyName: 'NEXT_PUBLIC_LEGAL_COMPANY_NAME',
  inn: 'NEXT_PUBLIC_LEGAL_INN',
  taxStatus: 'NEXT_PUBLIC_LEGAL_TAX_STATUS',
  ogrn: 'NEXT_PUBLIC_LEGAL_OGRN',
  address: 'NEXT_PUBLIC_LEGAL_ADDRESS',
  email: 'NEXT_PUBLIC_LEGAL_EMAIL',
  phone: 'NEXT_PUBLIC_LEGAL_PHONE',
};

function readLegalEnv(key: string): string {
  const value = process.env[key];
  if (typeof value !== 'string') {
    return '';
  }
  return value.trim();
}

export function getLegalRequisites(): LegalRequisites {
  return {
    companyName: readLegalEnv(LEGAL_ENV_KEYS.companyName),
    inn: readLegalEnv(LEGAL_ENV_KEYS.inn),
    taxStatus: readLegalEnv(LEGAL_ENV_KEYS.taxStatus),
    ogrn: readLegalEnv(LEGAL_ENV_KEYS.ogrn),
    address: readLegalEnv(LEGAL_ENV_KEYS.address),
    email: readLegalEnv(LEGAL_ENV_KEYS.email),
    phone: readLegalEnv(LEGAL_ENV_KEYS.phone),
  };
}

export function hasLegalValue(value: string): boolean {
  return value.trim().length > 0;
}

/** Rows with an empty value (including a blank OGRN for a self-employed seller) are omitted. */
export function legalRequisiteRows(
  requisites: LegalRequisites,
  labels: Record<LegalField, string>,
): LegalRequisiteRow[] {
  return LEGAL_FIELDS.filter((field) => hasLegalValue(requisites[field])).map((field) => ({
    field,
    label: labels[field],
    value: requisites[field],
  }));
}
