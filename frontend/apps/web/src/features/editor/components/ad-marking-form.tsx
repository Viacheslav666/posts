'use client';

import type {
  AdMarking,
  AdMarkingCustomerRole,
  AdMarkingPlacement,
  AdMarkingVatRate,
  OrdProvider,
  VatBreakdown,
} from '@posts/api-client';
import { Input } from '@posts/ui';
import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { useEffect, useMemo, useState } from 'react';

import {
  useCalculateOrdVat,
  useOrdContractors,
  useOrdKktu,
  useOrdOksm,
} from '@/features/ord/hooks/use-ord-queries';
import {
  AD_PLACEMENTS,
  previewAdPlacement,
} from '@/features/ord/lib/ad-marker-preview';
import {
  type AdFieldErrors,
  validateAdMarkingClient,
} from '@/features/ord/lib/ad-marking-validation';
import { calculateVatBreakdown, VAT_RATES } from '@/features/ord/lib/vat-calculator';
import { useCan } from '@/shared/hooks/use-can';
import { isValidInn } from '@/shared/lib/inn';

/** Official АРИР recommendations PDF (аrir.рф). */
export const ARIR_RECOMMENDATIONS_URL =
  'https://xn--d1acmcsfk8d0a.xn--p1ai/public/files/Rekomendacii_po_razmeshheniju_identifikatora_reklamy_final_22112023_h4n.pdf';

type AdMarkingFormProps = {
  workspaceId: string;
  enabled: boolean;
  ad: AdMarking;
  provider: OrdProvider;
  hasOrdConnection: boolean;
  eridStatus: 'idle' | 'missing' | 'ready' | 'error';
  erid?: string;
  /** Optional sample body for placement preview. */
  previewBody?: string;
  /** Server / parent field errors (ProblemDetail.errors or mapped). */
  fieldErrors?: AdFieldErrors;
  onEnabledChange: (enabled: boolean) => void;
  onAdChange: (ad: AdMarking) => void;
  onProviderChange: (provider: OrdProvider) => void;
  onRegister?: () => void;
  registering?: boolean;
};

function FieldError({ message }: { message?: string }) {
  if (!message) return null;
  return (
    <span className="text-xs text-destructive" role="alert">
      {message}
    </span>
  );
}

function formatMoney(value: number | undefined): string {
  if (value === undefined || Number.isNaN(value)) return '—';
  return value.toLocaleString('ru-RU', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

function VatSummary({
  label,
  breakdown,
  source,
}: {
  label: string;
  breakdown: VatBreakdown;
  source: 'server' | 'local';
}) {
  return (
    <div
      className="rounded-md border border-dashed border-border px-3 py-2 text-xs text-muted-foreground"
      data-testid="ad-vat-summary"
      data-vat-source={source}
    >
      <p className="font-medium text-foreground">{label}</p>
      <p>
        без НДС: {formatMoney(breakdown.amountWithoutVat)} · НДС:{' '}
        {formatMoney(breakdown.vatAmount)} · с НДС:{' '}
        {formatMoney(breakdown.amountWithVat)}
      </p>
    </div>
  );
}

export function AdMarkingForm({
  workspaceId,
  enabled,
  ad,
  provider,
  hasOrdConnection,
  eridStatus,
  erid,
  previewBody = 'Текст поста https://example.com/offer',
  fieldErrors: externalErrors,
  onEnabledChange,
  onAdChange,
  onProviderChange,
  onRegister,
  registering = false,
}: AdMarkingFormProps) {
  const t = useTranslations('editor.ad');
  const canScheduleAd = useCan('posts.ad.schedule', workspaceId);
  const contractorsQuery = useOrdContractors(workspaceId, enabled);
  const [kktuQuery, setKktuQuery] = useState('');
  const [kktuDebounced, setKktuDebounced] = useState('');
  const kktuDictQuery = useOrdKktu(enabled, kktuDebounced);
  const oksmQuery = useOrdOksm(enabled && Boolean(ad.isForeign));
  const vatMutation = useCalculateOrdVat();

  const [amountVatServer, setAmountVatServer] = useState<VatBreakdown | null>(null);
  const [contractVatServer, setContractVatServer] = useState<VatBreakdown | null>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => setKktuDebounced(kktuQuery.trim()), 250);
    return () => window.clearTimeout(timer);
  }, [kktuQuery]);

  const patch = (partial: Partial<AdMarking>) => onAdChange({ ...ad, ...partial });

  const clientErrors = useMemo(
    () => (enabled ? validateAdMarkingClient(ad) : {}),
    [ad, enabled],
  );
  const errors: AdFieldErrors = { ...clientErrors, ...externalErrors };

  const knownErrorKeys = new Set([
    'advertiserNameRequired',
    'innInvalid',
    'innPhysical12',
    'innJuridical10',
    'orgTypeUnsupported',
    'kppRequired',
    'kktuRequired',
    'oksmRequired',
    'foreignIdRequired',
    'vatRateUnsupported',
    'contractDateRequired',
    'contractDateEndOrder',
    'socialAdQuotaRequiresSocialAd',
    'customerRoleUnsupported',
    'executorInvalid',
    'placementInvalid',
  ]);

  const resolveError = (key: string) => {
    const raw = errors[key];
    if (!raw) return undefined;
    if (knownErrorKeys.has(raw)) {
      return t(`errors.${raw}` as 'errors.innInvalid');
    }
    return raw;
  };

  const innValue = ad.inn ?? '';
  const innInvalid = Boolean(resolveError('inn')) || (innValue.length > 0 && !ad.isForeign && !isValidInn(innValue));

  const amountLocal = useMemo(
    () => calculateVatBreakdown(ad.amount ?? 0, ad.vatRate),
    [ad.amount, ad.vatRate],
  );
  const contractLocal = useMemo(
    () => calculateVatBreakdown(ad.contractAmount ?? 0, ad.vatRate),
    [ad.contractAmount, ad.vatRate],
  );

  useEffect(() => {
    if (!enabled) return;
    const rate = (ad.vatRate ?? 'without_vat') as AdMarkingVatRate;
    let cancelled = false;
    const timer = window.setTimeout(() => {
      void (async () => {
        try {
          const [amountVat, contractVat] = await Promise.all([
            vatMutation.mutateAsync({ amountWithVat: ad.amount ?? 0, vatRate: rate }),
            vatMutation.mutateAsync({
              amountWithVat: ad.contractAmount ?? 0,
              vatRate: rate,
            }),
          ]);
          if (cancelled) return;
          setAmountVatServer(amountVat);
          setContractVatServer(contractVat);
        } catch {
          if (!cancelled) {
            setAmountVatServer(null);
            setContractVatServer(null);
          }
        }
      })();
    }, 350);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- debounce amount/rate only
  }, [enabled, ad.amount, ad.contractAmount, ad.vatRate]);

  const placementPreview = useMemo(
    () =>
      previewAdPlacement({
        bodyText: previewBody,
        advertiserName: ad.advertiserName ?? '',
        inn: ad.inn ?? '',
        erid: erid ?? ad.erid ?? 'preview-erid',
        placement: ad.placement,
      }),
    [ad.advertiserName, ad.erid, ad.inn, ad.placement, erid, previewBody],
  );

  const amountBreakdown = amountVatServer ?? ad.amountVat ?? amountLocal;
  const contractBreakdown = contractVatServer ?? ad.contractAmountVat ?? contractLocal;
  const amountSource = amountVatServer || ad.amountVat ? 'server' : 'local';
  const contractSource = contractVatServer || ad.contractAmountVat ? 'server' : 'local';

  const canRegister =
    !registering &&
    hasOrdConnection &&
    !innInvalid &&
    Boolean(ad.kktuCodes?.length) &&
    Boolean(ad.advertiserName?.trim()) &&
    Object.keys(clientErrors).length === 0;

  return (
    <section
      aria-label={t('label')}
      className="space-y-4 rounded-lg border border-border bg-card/40 p-4"
      data-testid="ad-marking-form"
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-sm font-semibold">{t('title')}</h2>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={enabled}
            disabled={!canScheduleAd}
            data-testid="ad-is-ad-toggle"
            onChange={(event) => onEnabledChange(event.target.checked)}
          />
          {t('toggle')}
        </label>
      </div>

      {!canScheduleAd ? (
        <p className="text-sm text-muted-foreground">{t('noPermission')}</p>
      ) : null}

      {enabled ? (
        <div className="space-y-3">
          {!hasOrdConnection ? (
            <div
              className="rounded-md border border-destructive/40 bg-destructive/5 px-3 py-2 text-sm text-destructive"
              data-testid="ad-ord-not-configured"
              role="alert"
            >
              {t('ordNotConfigured')}{' '}
              <Link className="underline" href={`/app/${workspaceId}/ord`}>
                {t('openOrdSettings')}
              </Link>
            </div>
          ) : null}

          <div className="flex flex-wrap gap-4 text-sm sm:col-span-2">
            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                checked={Boolean(ad.selfPromo)}
                data-testid="ad-self-promo"
                onChange={(event) => patch({ selfPromo: event.target.checked })}
              />
              {t('selfPromo')}
            </label>
            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                checked={Boolean(ad.socialAd)}
                data-testid="ad-social-ad"
                onChange={(event) => {
                  const socialAd = event.target.checked;
                  patch({
                    socialAd,
                    socialAdQuota: socialAd ? ad.socialAdQuota : false,
                  });
                }}
              />
              {t('socialAd')}
            </label>
            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                checked={Boolean(ad.socialAdQuota)}
                disabled={!ad.socialAd}
                data-testid="ad-social-ad-quota"
                onChange={(event) => patch({ socialAdQuota: event.target.checked })}
              />
              {t('socialAdQuota')}
            </label>
            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                checked={Boolean(ad.isForeign)}
                data-testid="ad-is-foreign"
                onChange={(event) => patch({ isForeign: event.target.checked })}
              />
              {t('isForeign')}
            </label>
          </div>
          <FieldError message={resolveError('socialAdQuota')} />

          <div className="grid gap-3 sm:grid-cols-2">
            <label className="flex flex-col gap-1 text-sm">
              <span>{t('provider')}</span>
              <select
                className="h-10 rounded-md border border-input bg-background px-3 text-sm"
                value={provider}
                aria-label={t('provider')}
                data-testid="ad-ord-provider"
                onChange={(event) => onProviderChange(event.target.value as OrdProvider)}
              >
                <option value="vk_ord">{t('providers.vk_ord')}</option>
                <option value="yandex_ord">{t('providers.yandex_ord')}</option>
              </select>
            </label>

            <label className="flex flex-col gap-1 text-sm">
              <span>{t('contractor')}</span>
              <select
                className="h-10 rounded-md border border-input bg-background px-3 text-sm"
                value={ad.contractorId ?? ''}
                aria-label={t('contractor')}
                data-testid="ad-contractor"
                onChange={(event) => {
                  const id = event.target.value;
                  const contractor = contractorsQuery.data?.items?.find((item) => item.id === id);
                  patch({
                    contractorId: id || undefined,
                    advertiserName: contractor?.name ?? ad.advertiserName,
                    inn: contractor?.inn ?? ad.inn,
                    kpp: contractor?.kpp ?? ad.kpp,
                    orgType: (contractor?.orgType as AdMarking['orgType']) ?? ad.orgType,
                    phone: contractor?.phone ?? ad.phone,
                    address: contractor?.address ?? ad.address,
                  });
                }}
              >
                <option value="">{t('contractorPlaceholder')}</option>
                {(contractorsQuery.data?.items ?? []).map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.name} ({item.inn})
                  </option>
                ))}
              </select>
            </label>

            <label className="flex flex-col gap-1 text-sm">
              <span>{t('advertiserName')}</span>
              <Input
                value={ad.advertiserName ?? ''}
                aria-label={t('advertiserName')}
                aria-invalid={Boolean(resolveError('advertiserName'))}
                data-testid="ad-advertiser-name"
                onChange={(event) => patch({ advertiserName: event.target.value })}
              />
              <FieldError message={resolveError('advertiserName')} />
            </label>

            <label className="flex flex-col gap-1 text-sm">
              <span>{t('inn')}</span>
              <Input
                value={innValue}
                aria-label={t('inn')}
                aria-invalid={innInvalid}
                data-testid="ad-inn"
                onChange={(event) => patch({ inn: event.target.value.replace(/\D/g, '') })}
              />
              {innInvalid ? (
                <span className="text-xs text-destructive" data-testid="ad-inn-error" role="alert">
                  {resolveError('inn') ?? t('innInvalid')}
                </span>
              ) : null}
            </label>

            <label className="flex flex-col gap-1 text-sm">
              <span>{t('orgType')}</span>
              <select
                className="h-10 rounded-md border border-input bg-background px-3 text-sm"
                value={ad.orgType ?? 'juridical'}
                aria-label={t('orgType')}
                data-testid="ad-org-type"
                onChange={(event) =>
                  patch({ orgType: event.target.value as AdMarking['orgType'] })
                }
              >
                <option value="physical">{t('orgTypes.physical')}</option>
                <option value="ip">{t('orgTypes.ip')}</option>
                <option value="juridical">{t('orgTypes.juridical')}</option>
                <option value="foreign_physical">{t('orgTypes.foreign_physical')}</option>
                <option value="foreign_juridical">{t('orgTypes.foreign_juridical')}</option>
              </select>
              <FieldError message={resolveError('orgType')} />
            </label>

            <label className="flex flex-col gap-1 text-sm">
              <span>{t('kpp')}</span>
              <Input
                value={ad.kpp ?? ''}
                aria-label={t('kpp')}
                aria-invalid={Boolean(resolveError('kpp'))}
                disabled={ad.orgType !== 'juridical'}
                data-testid="ad-kpp"
                onChange={(event) => patch({ kpp: event.target.value })}
              />
              <FieldError message={resolveError('kpp')} />
            </label>

            <label className="flex flex-col gap-1 text-sm">
              <span>{t('phone')}</span>
              <Input
                type="tel"
                value={ad.phone ?? ''}
                aria-label={t('phone')}
                data-testid="ad-phone"
                onChange={(event) => patch({ phone: event.target.value })}
              />
            </label>

            <label className="flex flex-col gap-1 text-sm">
              <span>{t('address')}</span>
              <Input
                value={ad.address ?? ''}
                aria-label={t('address')}
                aria-invalid={Boolean(resolveError('address'))}
                data-testid="ad-address"
                onChange={(event) => patch({ address: event.target.value })}
              />
              <FieldError message={resolveError('address')} />
            </label>

            {ad.isForeign ? (
              <>
                <label className="flex flex-col gap-1 text-sm">
                  <span>{t('foreignInn')}</span>
                  <Input
                    value={ad.foreignInn ?? ''}
                    aria-label={t('foreignInn')}
                    aria-invalid={Boolean(resolveError('foreignInn'))}
                    data-testid="ad-foreign-inn"
                    onChange={(event) => patch({ foreignInn: event.target.value })}
                  />
                  <FieldError message={resolveError('foreignInn')} />
                </label>
                <label className="flex flex-col gap-1 text-sm">
                  <span>{t('foreignRegNumber')}</span>
                  <Input
                    value={ad.foreignRegNumber ?? ''}
                    aria-label={t('foreignRegNumber')}
                    data-testid="ad-foreign-reg"
                    onChange={(event) => patch({ foreignRegNumber: event.target.value })}
                  />
                </label>
                <label className="flex flex-col gap-1 text-sm">
                  <span>{t('foreignEpayment')}</span>
                  <Input
                    value={ad.foreignEpayment ?? ''}
                    aria-label={t('foreignEpayment')}
                    data-testid="ad-foreign-epayment"
                    onChange={(event) => patch({ foreignEpayment: event.target.value })}
                  />
                </label>
                <label className="flex flex-col gap-1 text-sm">
                  <span>{t('oksmCountry')}</span>
                  <select
                    className="h-10 rounded-md border border-input bg-background px-3 text-sm"
                    value={ad.oksmCountry ?? ''}
                    aria-label={t('oksmCountry')}
                    aria-invalid={Boolean(resolveError('oksmCountry'))}
                    data-testid="ad-oksm"
                    onChange={(event) => patch({ oksmCountry: event.target.value })}
                  >
                    <option value="">{t('oksmPlaceholder')}</option>
                    {(oksmQuery.data ?? []).map((item) => (
                      <option key={item.code} value={item.code}>
                        {item.code} — {item.name}
                      </option>
                    ))}
                  </select>
                  <FieldError message={resolveError('oksmCountry')} />
                </label>
              </>
            ) : null}

            <div className="flex flex-col gap-1 text-sm sm:col-span-2">
              <span>{t('kktu')}</span>
              <Input
                value={kktuQuery}
                aria-label={t('kktuSearch')}
                placeholder={t('kktuSearch')}
                data-testid="ad-kktu-search"
                onChange={(event) => setKktuQuery(event.target.value)}
              />
              <select
                className="h-10 rounded-md border border-input bg-background px-3 text-sm"
                value={ad.kktuCodes?.[0] ?? ''}
                aria-label={t('kktu')}
                aria-invalid={Boolean(resolveError('kktuCodes'))}
                data-testid="ad-kktu"
                onChange={(event) =>
                  patch({ kktuCodes: event.target.value ? [event.target.value] : [] })
                }
              >
                <option value="">{t('kktuPlaceholder')}</option>
                {(kktuDictQuery.data ?? []).map((item) => (
                  <option key={item.code} value={item.code}>
                    {item.code} — {item.name}
                  </option>
                ))}
              </select>
              <FieldError message={resolveError('kktuCodes')} />
            </div>

            <label className="flex flex-col gap-1 text-sm">
              <span>{t('amount')}</span>
              <Input
                type="number"
                min={0}
                step="0.01"
                value={ad.amount ?? 0}
                aria-label={t('amount')}
                data-testid="ad-amount"
                onChange={(event) => patch({ amount: Number(event.target.value) || 0 })}
              />
            </label>

            <label className="flex flex-col gap-1 text-sm">
              <span>{t('contractAmount')}</span>
              <Input
                type="number"
                min={0}
                step="0.01"
                value={ad.contractAmount ?? 0}
                aria-label={t('contractAmount')}
                data-testid="ad-contract-amount"
                onChange={(event) => patch({ contractAmount: Number(event.target.value) || 0 })}
              />
            </label>

            <label className="flex flex-col gap-1 text-sm">
              <span>{t('vatRate')}</span>
              <select
                className="h-10 rounded-md border border-input bg-background px-3 text-sm"
                value={ad.vatRate ?? 'without_vat'}
                aria-label={t('vatRate')}
                data-testid="ad-vat-rate"
                onChange={(event) =>
                  patch({ vatRate: event.target.value as AdMarkingVatRate })
                }
              >
                {VAT_RATES.map((rate) => (
                  <option key={rate} value={rate}>
                    {t(`vatRates.${rate}`)}
                  </option>
                ))}
              </select>
              <FieldError message={resolveError('vatRate')} />
            </label>

            <div className="space-y-2 sm:col-span-2">
              <VatSummary
                label={t('amountVatBreakdown')}
                breakdown={amountBreakdown}
                source={amountSource}
              />
              <VatSummary
                label={t('contractVatBreakdown')}
                breakdown={contractBreakdown}
                source={contractSource}
              />
            </div>

            <label className="flex flex-col gap-1 text-sm">
              <span>{t('contractSerial')}</span>
              <Input
                value={ad.contractSerial ?? ''}
                aria-label={t('contractSerial')}
                data-testid="ad-contract-serial"
                disabled={Boolean(ad.selfPromo)}
                onChange={(event) => patch({ contractSerial: event.target.value })}
              />
            </label>

            <label className="flex flex-col gap-1 text-sm">
              <span>{t('contractDate')}</span>
              <Input
                type="date"
                value={ad.contractDate ?? ''}
                aria-label={t('contractDate')}
                aria-invalid={Boolean(resolveError('contractDate'))}
                data-testid="ad-contract-date"
                disabled={Boolean(ad.selfPromo)}
                onChange={(event) => patch({ contractDate: event.target.value })}
              />
              <FieldError message={resolveError('contractDate')} />
            </label>

            <label className="flex flex-col gap-1 text-sm">
              <span>{t('contractDateEnd')}</span>
              <Input
                type="date"
                value={ad.contractDateEnd ?? ''}
                aria-label={t('contractDateEnd')}
                aria-invalid={Boolean(resolveError('contractDateEnd'))}
                data-testid="ad-contract-date-end"
                disabled={Boolean(ad.selfPromo)}
                onChange={(event) => patch({ contractDateEnd: event.target.value })}
              />
              <FieldError message={resolveError('contractDateEnd')} />
            </label>

            <label className="flex flex-col gap-1 text-sm">
              <span>{t('executor')}</span>
              <select
                className="h-10 rounded-md border border-input bg-background px-3 text-sm"
                value={ad.executor ?? 'self'}
                aria-label={t('executor')}
                data-testid="ad-executor"
                onChange={(event) => patch({ executor: event.target.value })}
              >
                <option value="self">{t('executorSelf')}</option>
                {(contractorsQuery.data?.items ?? []).map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.name}
                  </option>
                ))}
              </select>
              <FieldError message={resolveError('executor')} />
            </label>

            <label className="flex flex-col gap-1 text-sm">
              <span>{t('customerRole')}</span>
              <select
                className="h-10 rounded-md border border-input bg-background px-3 text-sm"
                value={ad.customerRole ?? 'advertiser'}
                aria-label={t('customerRole')}
                data-testid="ad-customer-role"
                onChange={(event) =>
                  patch({ customerRole: event.target.value as AdMarkingCustomerRole })
                }
              >
                <option value="advertiser">{t('customerRoles.advertiser')}</option>
                <option value="agency">{t('customerRoles.agency')}</option>
                <option value="publisher">{t('customerRoles.publisher')}</option>
                <option value="ors">{t('customerRoles.ors')}</option>
              </select>
              <FieldError message={resolveError('customerRole')} />
            </label>

            <label className="flex flex-col gap-1 text-sm sm:col-span-2">
              <span>{t('placement')}</span>
              <select
                className="h-10 rounded-md border border-input bg-background px-3 text-sm"
                value={ad.placement ?? 'text_start'}
                aria-label={t('placement')}
                data-testid="ad-placement"
                onChange={(event) => {
                  const placement = event.target.value as AdMarkingPlacement;
                  const legacy = String(AD_PLACEMENTS.indexOf(placement));
                  patch({ placement, markingType: legacy });
                }}
              >
                {AD_PLACEMENTS.map((value) => (
                  <option key={value} value={value}>
                    {t(`placements.${value}`)}
                  </option>
                ))}
              </select>
              <FieldError message={resolveError('placement')} />
            </label>

            <div
              className="space-y-2 rounded-md border border-border bg-muted/30 px-3 py-2 sm:col-span-2"
              data-testid="ad-placement-preview"
            >
              <p className="text-sm font-medium">{t('placementPreview')}</p>
              <pre className="whitespace-pre-wrap break-words text-xs text-muted-foreground">
                {placementPreview.text}
              </pre>
              {placementPreview.warnings.some((w) => w.startsWith('vk_menu_unsupported')) ? (
                <p
                  className="text-xs text-amber-700 dark:text-amber-400"
                  role="status"
                  data-testid="ad-vk-menu-unsupported"
                >
                  {t('vkMenuUnsupported')}
                </p>
              ) : null}
              {placementPreview.warnings
                .filter((w) => !w.startsWith('vk_menu_unsupported'))
                .map((warning) => (
                  <p key={warning} className="text-xs text-amber-700 dark:text-amber-400">
                    {warning}
                  </p>
                ))}
              <a
                className="inline-block text-xs text-primary underline"
                href={ARIR_RECOMMENDATIONS_URL}
                target="_blank"
                rel="noreferrer"
                data-testid="ad-arir-link"
              >
                {t('arirLink')}
              </a>
            </div>

            <label className="flex items-center gap-2 text-sm sm:col-span-2">
              <input
                type="checkbox"
                checked={ad.autoDeleteAfterPeriod !== false}
                data-testid="ad-auto-delete"
                onChange={(event) => patch({ autoDeleteAfterPeriod: event.target.checked })}
              />
              {t('autoDeleteAfterPeriod')}
            </label>
          </div>

          <div
            className="flex flex-wrap items-center gap-3 rounded-md border border-dashed border-border px-3 py-2"
            data-testid="ad-erid-status"
          >
            <span className="text-sm font-medium">{t('eridStatus')}</span>
            {eridStatus === 'ready' && erid ? (
              <span className="font-mono text-xs" data-testid="ad-erid-value">
                erid: {erid}
              </span>
            ) : eridStatus === 'missing' ? (
              <span className="text-sm text-amber-700 dark:text-amber-400">{t('eridMissing')}</span>
            ) : eridStatus === 'error' ? (
              <span className="text-sm text-destructive">{t('eridError')}</span>
            ) : (
              <span className="text-sm text-muted-foreground">{t('eridIdle')}</span>
            )}
            {onRegister ? (
              <button
                type="button"
                className="ml-auto rounded-md border border-input px-3 py-1.5 text-sm hover:bg-muted disabled:opacity-50"
                disabled={!canRegister}
                data-testid="ad-register-erid"
                onClick={onRegister}
              >
                {registering ? t('registering') : t('register')}
              </button>
            ) : null}
          </div>
        </div>
      ) : null}
    </section>
  );
}
