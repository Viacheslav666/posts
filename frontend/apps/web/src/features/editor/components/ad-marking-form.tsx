'use client';

import type { AdMarking, OrdProvider } from '@posts/api-client';
import { Input } from '@posts/ui';
import Link from 'next/link';
import { useTranslations } from 'next-intl';

import { useOrdContractors, useOrdKktu } from '@/features/ord/hooks/use-ord-queries';
import { isValidInn } from '@/shared/lib/inn';
import { useCan } from '@/shared/hooks/use-can';

type AdMarkingFormProps = {
  workspaceId: string;
  enabled: boolean;
  ad: AdMarking;
  provider: OrdProvider;
  hasOrdConnection: boolean;
  eridStatus: 'idle' | 'missing' | 'ready' | 'error';
  erid?: string;
  onEnabledChange: (enabled: boolean) => void;
  onAdChange: (ad: AdMarking) => void;
  onProviderChange: (provider: OrdProvider) => void;
  onRegister?: () => void;
  registering?: boolean;
};

export function AdMarkingForm({
  workspaceId,
  enabled,
  ad,
  provider,
  hasOrdConnection,
  eridStatus,
  erid,
  onEnabledChange,
  onAdChange,
  onProviderChange,
  onRegister,
  registering = false,
}: AdMarkingFormProps) {
  const t = useTranslations('editor.ad');
  const canScheduleAd = useCan('posts.ad.schedule', workspaceId);
  const contractorsQuery = useOrdContractors(workspaceId, enabled);
  const kktuQuery = useOrdKktu(enabled);

  const innValue = ad.inn ?? '';
  const innInvalid = innValue.length > 0 && !isValidInn(innValue);

  const patch = (partial: Partial<AdMarking>) => onAdChange({ ...ad, ...partial });

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
                onChange={(event) => patch({ advertiserName: event.target.value })}
              />
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
                <span className="text-xs text-destructive" data-testid="ad-inn-error">
                  {t('innInvalid')}
                </span>
              ) : null}
            </label>

            <label className="flex flex-col gap-1 text-sm">
              <span>{t('orgType')}</span>
              <select
                className="h-10 rounded-md border border-input bg-background px-3 text-sm"
                value={ad.orgType ?? 'juridical'}
                aria-label={t('orgType')}
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
            </label>

            <label className="flex flex-col gap-1 text-sm">
              <span>{t('kpp')}</span>
              <Input
                value={ad.kpp ?? ''}
                aria-label={t('kpp')}
                disabled={ad.orgType !== 'juridical'}
                onChange={(event) => patch({ kpp: event.target.value })}
              />
            </label>

            <label className="flex flex-col gap-1 text-sm sm:col-span-2">
              <span>{t('kktu')}</span>
              <select
                className="h-10 rounded-md border border-input bg-background px-3 text-sm"
                value={ad.kktuCodes?.[0] ?? ''}
                aria-label={t('kktu')}
                data-testid="ad-kktu"
                onChange={(event) =>
                  patch({ kktuCodes: event.target.value ? [event.target.value] : [] })
                }
              >
                <option value="">{t('kktuPlaceholder')}</option>
                {(kktuQuery.data ?? []).map((item) => (
                  <option key={item.code} value={item.code}>
                    {item.code} — {item.name}
                  </option>
                ))}
              </select>
            </label>

            <label className="flex flex-col gap-1 text-sm">
              <span>{t('amount')}</span>
              <Input
                type="number"
                min={0}
                step="0.01"
                value={ad.amount ?? 0}
                aria-label={t('amount')}
                onChange={(event) => patch({ amount: Number(event.target.value) || 0 })}
              />
            </label>

            <label className="flex flex-col gap-1 text-sm">
              <span>{t('contractDate')}</span>
              <Input
                type="date"
                value={ad.contractDate ?? ''}
                aria-label={t('contractDate')}
                disabled={Boolean(ad.selfPromo)}
                onChange={(event) => patch({ contractDate: event.target.value })}
              />
            </label>

            <label className="flex items-center gap-2 text-sm sm:col-span-2">
              <input
                type="checkbox"
                checked={Boolean(ad.selfPromo)}
                onChange={(event) => patch({ selfPromo: event.target.checked })}
              />
              {t('selfPromo')}
            </label>

            <label className="flex items-center gap-2 text-sm sm:col-span-2">
              <input
                type="checkbox"
                checked={ad.autoDeleteAfterPeriod !== false}
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
                disabled={
                  registering ||
                  !hasOrdConnection ||
                  innInvalid ||
                  !(ad.kktuCodes?.length) ||
                  !ad.advertiserName
                }
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
