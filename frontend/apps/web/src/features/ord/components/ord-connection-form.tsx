'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import type { OrdConnection, OrdProvider } from '@posts/api-client';
import {
  Button,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Skeleton,
  toast,
} from '@posts/ui';
import { useTranslations } from 'next-intl';
import { useEffect, useMemo } from 'react';
import { Controller, useForm } from 'react-hook-form';

import { FormField } from '@/features/auth/components/form-field';
import { useCan } from '@/shared/hooks/use-can';
import { getErrorMessage } from '@/shared/lib/errors';

import {
  useOrdConnections,
  useTestOrdConnection,
  useUpsertOrdConnection,
} from '../hooks/use-ord-queries';
import { VAT_RATES } from '../lib/vat-calculator';
import {
  ORD_CONNECTION_ROLES,
  ORD_PROVIDERS,
  ordConnectionSchema,
  type OrdConnectionFormValues,
} from '../model/schemas';

/** Personal cabinet ЕРИР (official portal). */
export const ERIR_CABINET_URL = 'https://erir.digital.gov.ru/';

/** Static draft offer template (legal review required). */
export const ORD_OFFER_TEMPLATE_HREF = '/legal/ord-offer-template.docx';

type OrdConnectionFormProps = {
  workspaceId: string;
};

function pickConnection(
  items: OrdConnection[] | undefined,
  provider: OrdProvider,
): OrdConnection | undefined {
  return items?.find((item) => item.provider === provider);
}

function normalizeRoles(roles: string[] | undefined): Array<'advertiser' | 'publisher'> {
  const filtered = (roles ?? []).filter(
    (role): role is 'advertiser' | 'publisher' =>
      role === 'advertiser' || role === 'publisher',
  );
  return filtered.length > 0 ? filtered : ['advertiser', 'publisher'];
}

export function OrdConnectionForm({ workspaceId }: OrdConnectionFormProps) {
  const t = useTranslations('ord');
  const canManage = useCan('ord.connection.manage', workspaceId);
  const connectionsQuery = useOrdConnections(workspaceId, canManage);
  const upsertMutation = useUpsertOrdConnection(workspaceId);
  const testMutation = useTestOrdConnection(workspaceId);

  const {
    register,
    handleSubmit,
    control,
    watch,
    reset,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<OrdConnectionFormValues>({
    resolver: zodResolver(ordConnectionSchema),
    defaultValues: {
      provider: 'vk_ord',
      apiKey: '',
      publisherInn: '',
      publisherName: '',
      orgType: 'juridical',
      phone: '',
      address: '',
      vatRate: 'without_vat',
      roles: ['advertiser', 'publisher'],
      isForeign: false,
      notifyProblems: true,
      responsibilityAccepted: false,
    },
  });

  const provider = watch('provider');
  const roles = watch('roles');
  const activeConnection = useMemo(
    () => pickConnection(connectionsQuery.data?.items, provider),
    [connectionsQuery.data?.items, provider],
  );
  const hasStoredKey = Boolean(activeConnection?.hasApiKey);

  useEffect(() => {
    reset({
      provider,
      apiKey: '',
      publisherInn: activeConnection?.publisherInn ?? '',
      publisherName: activeConnection?.publisherName ?? '',
      orgType: activeConnection?.orgType ?? 'juridical',
      phone: activeConnection?.phone ?? '',
      address: activeConnection?.address ?? '',
      vatRate: (activeConnection?.vatRate as OrdConnectionFormValues['vatRate']) ?? 'without_vat',
      roles: normalizeRoles(activeConnection?.roles),
      isForeign: Boolean(activeConnection?.isForeign),
      notifyProblems: activeConnection?.notifyProblems !== false,
      responsibilityAccepted: false,
    });
  }, [activeConnection, provider, reset]);

  if (!canManage) {
    return (
      <p className="text-sm text-muted-foreground" data-testid="ord-connection-readonly">
        {t('connection.readOnly')}
      </p>
    );
  }

  if (connectionsQuery.isLoading) {
    return <Skeleton className="h-48 w-full" data-testid="ord-connection-form" />;
  }

  const onSave = handleSubmit(async (values) => {
    try {
      await upsertMutation.mutateAsync({
        provider: values.provider,
        body: {
          apiKey: values.apiKey?.trim() || undefined,
          publisherInn: values.publisherInn,
          publisherName: values.publisherName,
          orgType: values.orgType,
          phone: values.phone?.trim() || undefined,
          address: values.address.trim(),
          vatRate: values.vatRate,
          roles: values.roles,
          isForeign: values.isForeign,
          notifyProblems: values.notifyProblems,
          responsibilityAccepted: true,
        },
      });
      toast({ title: t('connection.savedTitle'), description: t('connection.savedBody') });
    } catch (error) {
      toast({
        variant: 'destructive',
        title: t('connection.errorTitle'),
        description: getErrorMessage(error, t('connection.errorTitle')),
      });
    }
  });

  const onTest = async () => {
    try {
      const result = await testMutation.mutateAsync(provider);
      if (result.ok) {
        toast({ title: t('connection.testOkTitle'), description: result.message ?? '' });
      } else {
        toast({
          variant: 'destructive',
          title: t('connection.testFailTitle'),
          description: result.message ?? t('connection.testFailBody'),
        });
      }
    } catch (error) {
      toast({
        variant: 'destructive',
        title: t('connection.testFailTitle'),
        description: getErrorMessage(error, t('connection.testFailTitle')),
      });
    }
  };

  const toggleRole = (role: 'advertiser' | 'publisher', checked: boolean) => {
    const next = checked
      ? Array.from(new Set([...(roles ?? []), role]))
      : (roles ?? []).filter((item) => item !== role);
    setValue('roles', next.length > 0 ? next : [role], { shouldValidate: true });
  };

  return (
    <form
      className="space-y-4"
      onSubmit={onSave}
      noValidate
      data-testid="ord-connection-form"
    >
      <aside
        className="space-y-2 rounded-md border border-border bg-muted/20 px-3 py-3 text-sm"
        data-testid="ord-connection-help"
      >
        <p className="font-medium text-foreground">{t('connection.helpTitle')}</p>
        <p className="text-muted-foreground">{t('connection.vkKeyHelp')}</p>
        <p className="text-muted-foreground">{t('connection.yandexKeyHelp')}</p>
        <div className="flex flex-wrap gap-3 pt-1">
          <a
            className="text-primary underline"
            href={ERIR_CABINET_URL}
            target="_blank"
            rel="noreferrer"
            data-testid="ord-erir-link"
          >
            {t('connection.erirLink')}
          </a>
          <a
            className="text-primary underline"
            href={ORD_OFFER_TEMPLATE_HREF}
            download="ord-offer-template.docx"
            data-testid="ord-offer-download"
          >
            {t('connection.offerDownload')}
          </a>
        </div>
        <p className="text-xs text-amber-700 dark:text-amber-400">{t('connection.offerDraftNote')}</p>
      </aside>

      <div className="space-y-1.5">
        <label htmlFor="ord-provider" className="text-sm font-medium text-foreground">
          {t('connection.provider')}
        </label>
        <Controller
          control={control}
          name="provider"
          render={({ field }) => (
            <Select value={field.value} onValueChange={field.onChange}>
              <SelectTrigger id="ord-provider" aria-label={t('connection.provider')}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {ORD_PROVIDERS.map((value) => (
                  <SelectItem key={value} value={value}>
                    {t(`providers.${value}`)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
        />
      </div>

      <FormField
        label={t('connection.apiKey')}
        type="password"
        autoComplete="off"
        data-testid="ord-api-key"
        hint={hasStoredKey ? t('connection.apiKeySaved') : undefined}
        error={
          errors.apiKey
            ? t(`validation.${errors.apiKey.message as string}`)
            : undefined
        }
        {...register('apiKey')}
      />

      <FormField
        label={t('connection.publisherInn')}
        inputMode="numeric"
        error={
          errors.publisherInn
            ? t(`validation.${errors.publisherInn.message as 'publisherInnRequired'}`)
            : undefined
        }
        {...register('publisherInn')}
      />

      <FormField
        label={t('connection.publisherName')}
        error={
          errors.publisherName
            ? t(`validation.${errors.publisherName.message as 'publisherNameRequired'}`)
            : undefined
        }
        {...register('publisherName')}
      />

      <FormField
        label={t('connection.orgType')}
        error={
          errors.orgType
            ? t(`validation.${errors.orgType.message as 'orgTypeRequired'}`)
            : undefined
        }
        {...register('orgType')}
      />

      <FormField
        label={t('connection.address')}
        data-testid="ord-address"
        error={
          errors.address
            ? t(`validation.${errors.address.message as 'addressRequired'}`)
            : undefined
        }
        {...register('address')}
      />

      <div className="space-y-1.5">
        <label htmlFor="ord-vat-rate" className="text-sm font-medium text-foreground">
          {t('connection.vatRate')}
        </label>
        <Controller
          control={control}
          name="vatRate"
          render={({ field }) => (
            <Select value={field.value} onValueChange={field.onChange}>
              <SelectTrigger id="ord-vat-rate" aria-label={t('connection.vatRate')} data-testid="ord-vat-rate">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {VAT_RATES.map((rate) => (
                  <SelectItem key={rate} value={rate}>
                    {t(`connection.vatRates.${rate}`)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
        />
      </div>

      <FormField label={t('connection.phone')} type="tel" {...register('phone')} />

      <fieldset className="space-y-2" data-testid="ord-roles">
        <legend className="text-sm font-medium text-foreground">{t('connection.roles')}</legend>
        {ORD_CONNECTION_ROLES.map((role) => (
          <label key={role} className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={(roles ?? []).includes(role)}
              onChange={(event) => toggleRole(role, event.target.checked)}
            />
            {t(`connection.roleOptions.${role}`)}
          </label>
        ))}
        {errors.roles ? (
          <p role="alert" className="text-xs text-destructive">
            {t('validation.rolesRequired')}
          </p>
        ) : null}
      </fieldset>

      <label className="flex items-start gap-2 text-sm">
        <input
          type="checkbox"
          className="mt-1 h-4 w-4 rounded border-input"
          data-testid="ord-is-foreign"
          {...register('isForeign')}
        />
        <span>{t('connection.isForeign')}</span>
      </label>

      <label className="flex items-start gap-2 text-sm">
        <input
          type="checkbox"
          className="mt-1 h-4 w-4 rounded border-input"
          data-testid="ord-notify-problems"
          {...register('notifyProblems')}
        />
        <span>{t('connection.notifyProblems')}</span>
      </label>

      <div className="flex items-start gap-2">
        <input
          id="ord-responsibility"
          type="checkbox"
          className="mt-1 h-4 w-4 rounded border-input"
          aria-label={t('connection.responsibility')}
          {...register('responsibilityAccepted')}
        />
        <label htmlFor="ord-responsibility" className="text-sm text-foreground">
          {t('connection.responsibility')}
        </label>
      </div>
      {errors.responsibilityAccepted ? (
        <p role="alert" className="text-xs text-destructive">
          {t('validation.responsibilityRequired')}
        </p>
      ) : null}

      <div className="flex flex-wrap gap-2">
        <Button type="submit" disabled={isSubmitting || upsertMutation.isPending}>
          {t('connection.save')}
        </Button>
        <Button
          type="button"
          variant="outline"
          disabled={testMutation.isPending}
          onClick={() => void onTest()}
        >
          {t('connection.test')}
        </Button>
      </div>
    </form>
  );
}
