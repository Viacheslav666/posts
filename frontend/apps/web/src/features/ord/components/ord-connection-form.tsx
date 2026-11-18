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
import {
  ordConnectionSchema,
  ORD_PROVIDERS,
  type OrdConnectionFormValues,
} from '../model/schemas';

type OrdConnectionFormProps = {
  workspaceId: string;
};

function pickConnection(
  items: OrdConnection[] | undefined,
  provider: OrdProvider,
): OrdConnection | undefined {
  return items?.find((item) => item.provider === provider);
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
      responsibilityAccepted: false,
    },
  });

  const provider = watch('provider');
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

  return (
    <form
      className="space-y-4"
      onSubmit={onSave}
      noValidate
      data-testid="ord-connection-form"
    >
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

      <FormField label={t('connection.phone')} type="tel" {...register('phone')} />

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