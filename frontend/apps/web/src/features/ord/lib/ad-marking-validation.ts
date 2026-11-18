import type { AdMarking } from '@posts/api-client';

import { isValidInn, normalizeInn } from '@/shared/lib/inn';
import { isApiProblemError } from '@/shared/lib/errors';

import { AD_PLACEMENTS } from './ad-marker-preview';
import { VAT_RATES } from './vat-calculator';

const ORG_TYPES = new Set([
  'physical',
  'ip',
  'juridical',
  'foreign_physical',
  'foreign_juridical',
]);

const CUSTOMER_ROLES = new Set(['advertiser', 'agency', 'publisher', 'ors']);

export type AdFieldErrors = Partial<Record<string, string>>;

/**
 * Client validation aligned with backend `AdMarkingValidator.requireValidWhenAd`.
 * Returns field → messageKey (i18n under `editor.ad.errors.*`) or raw server message.
 */
export function validateAdMarkingClient(ad: AdMarking): AdFieldErrors {
  const errors: AdFieldErrors = {};
  if (!ad.advertiserName?.trim()) {
    errors.advertiserName = 'advertiserNameRequired';
  }

  const foreign =
    Boolean(ad.isForeign) ||
    Boolean(ad.orgType?.startsWith('foreign_'));
  const inn = normalizeInn(ad.inn ?? '');

  if (foreign) {
    if (!inn && !ad.foreignInn?.trim() && !ad.foreignRegNumber?.trim()) {
      errors.inn = 'foreignIdRequired';
    }
  } else if (!isValidInn(inn)) {
    errors.inn = 'innInvalid';
  } else if (
    (ad.orgType === 'physical' || ad.orgType === 'ip') &&
    inn.length !== 12
  ) {
    errors.inn = 'innPhysical12';
  } else if (ad.orgType === 'juridical' && inn.length !== 10) {
    errors.inn = 'innJuridical10';
  }

  if (ad.orgType && !ORG_TYPES.has(ad.orgType)) {
    errors.orgType = 'orgTypeUnsupported';
  }
  if (ad.orgType === 'juridical' && !ad.kpp?.trim()) {
    errors.kpp = 'kppRequired';
  }
  if (!ad.kktuCodes?.length) {
    errors.kktuCodes = 'kktuRequired';
  }
  if (foreign) {
    if (!ad.oksmCountry?.trim()) {
      errors.oksmCountry = 'oksmRequired';
    }
    if (!ad.foreignInn?.trim() && !ad.foreignRegNumber?.trim()) {
      errors.foreignInn = 'foreignIdRequired';
    }
  }
  if (ad.vatRate && !VAT_RATES.includes(ad.vatRate)) {
    errors.vatRate = 'vatRateUnsupported';
  }
  if (!ad.selfPromo && !ad.contractDate) {
    errors.contractDate = 'contractDateRequired';
  }
  if (
    ad.contractDate &&
    ad.contractDateEnd &&
    ad.contractDateEnd < ad.contractDate
  ) {
    errors.contractDateEnd = 'contractDateEndOrder';
  }
  if (ad.socialAdQuota && !ad.socialAd) {
    errors.socialAdQuota = 'socialAdQuotaRequiresSocialAd';
  }
  if (ad.customerRole && !CUSTOMER_ROLES.has(ad.customerRole)) {
    errors.customerRole = 'customerRoleUnsupported';
  }
  if (ad.executor && ad.executor !== 'self') {
    const uuid =
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    if (!uuid.test(ad.executor.trim())) {
      errors.executor = 'executorInvalid';
    }
  }
  if (ad.placement && !AD_PLACEMENTS.includes(ad.placement)) {
    errors.placement = 'placementInvalid';
  }
  return errors;
}

/** Map ProblemDetail / ApiProblemError to field errors for the ad form. */
export function mapServerAdErrors(error: unknown): AdFieldErrors {
  const mapped: AdFieldErrors = {};
  if (!isApiProblemError(error)) {
    return mapped;
  }

  if (error.code === 'ad_inn_invalid') {
    mapped.inn = error.message || 'innInvalid';
  }

  const list = error.errors;
  if (Array.isArray(list)) {
    for (const item of list) {
      if (!item || typeof item !== 'object') continue;
      const field = 'field' in item ? String(item.field ?? '') : '';
      const message =
        'message' in item
          ? String(item.message ?? '')
          : Object.values(item).find((v) => typeof v === 'string' && v !== field);
      if (field && message) {
        mapped[field.replace(/^adMarking\./, '').replace(/^ads\./, '')] = String(message);
      }
    }
  }

  const detail = (error.message || '').toLowerCase();
  const heuristics: Array<[RegExp, string]> = [
    [/advertisername/, 'advertiserName'],
    [/inn/, 'inn'],
    [/kpp/, 'kpp'],
    [/kktu/, 'kktuCodes'],
    [/oksm/, 'oksmCountry'],
    [/foreigninn|foreignreg/, 'foreignInn'],
    [/contractdateend/, 'contractDateEnd'],
    [/contractdate/, 'contractDate'],
    [/socialadquota/, 'socialAdQuota'],
    [/customerrole/, 'customerRole'],
    [/executor/, 'executor'],
    [/placement|marking/, 'placement'],
    [/vatrate/, 'vatRate'],
    [/address/, 'address'],
  ];
  if (Object.keys(mapped).length === 0 && detail) {
    for (const [re, field] of heuristics) {
      if (re.test(detail)) {
        mapped[field] = error.message;
        break;
      }
    }
  }
  return mapped;
}
