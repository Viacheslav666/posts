'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Button, Select, SelectContent, SelectItem, SelectTrigger, SelectValue, toast } from '@posts/ui';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { useTranslations } from 'next-intl';

import { FormField } from '@/features/auth/components/form-field';
import { queryKeys } from '@/shared/api-client';
import { useMe } from '@/shared/hooks/use-me';
import { getErrorMessage } from '@/shared/lib/errors';

import { updateMe } from '../api/me-settings-api';
import { profileSchema, type ProfileFormValues } from '../model/schemas';
import { SettingsShell } from './settings-shell';

const TIMEZONES = [
  'Europe/Kaliningrad',
  'Europe/Moscow',
  'Europe/Samara',
  'Asia/Yekaterinburg',
  'Asia/Novosibirsk',
  'Asia/Vladivostok',
  'UTC',
];

type ProfileSettingsFormProps = {
  workspaceId: string;
};

export function ProfileSettingsForm({ workspaceId }: ProfileSettingsFormProps) {
  const t = useTranslations('settings.profile');
  const queryClient = useQueryClient();
  const { data, isLoading } = useMe();

  const {
    register,
    control,
    handleSubmit,
    reset,
    formState: { errors, isDirty },
  } = useForm<ProfileFormValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: { name: '', timezone: 'Europe/Moscow', locale: 'ru' },
  });

  useEffect(() => {
    if (data) {
      reset({
        name: data.name ?? '',
        timezone: data.timezone ?? 'Europe/Moscow',
        locale: (data.locale === 'en' ? 'en' : 'ru') as 'ru' | 'en',
      });
    }
  }, [data, reset]);

  const saveMutation = useMutation({
    mutationFn: (values: ProfileFormValues) => updateMe(values),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.me.profile() });
      toast({ title: t('savedTitle'), description: t('savedBody') });
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('errorTitle'),
        description: getErrorMessage(error, t('errorTitle')),
      });
    },
  });

  return (
    <SettingsShell workspaceId={workspaceId} title={t('title')} description={t('description')}>
      {isLoading ? (
        <p className="text-sm text-muted-foreground">{t('loading')}</p>
      ) : (
        <form
          className="space-y-4"
          onSubmit={handleSubmit((values) => saveMutation.mutate(values))}
          noValidate
          data-testid="profile-settings-form"
        >
          <FormField
            label={t('fields.name')}
            error={
              errors.name
                ? t(`validation.${errors.name.message as 'nameRequired' | 'nameMax'}`)
                : undefined
            }
            {...register('name')}
          />

          <div className="space-y-2">
            <label className="text-sm font-medium" htmlFor="timezone">
              {t('fields.timezone')}
            </label>
            <Controller
              control={control}
              name="timezone"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger id="timezone" aria-label={t('fields.timezone')}>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {TIMEZONES.map((tz) => (
                      <SelectItem key={tz} value={tz}>
                        {tz}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
            {errors.timezone ? (
              <p className="text-sm text-destructive">{t('validation.timezoneRequired')}</p>
            ) : null}
          </div>

          <div className="space-y-2">
            <label className="text-sm font-medium" htmlFor="locale">
              {t('fields.locale')}
            </label>
            <Controller
              control={control}
              name="locale"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger id="locale" aria-label={t('fields.locale')}>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ru">{t('locales.ru')}</SelectItem>
                    <SelectItem value="en">{t('locales.en')}</SelectItem>
                  </SelectContent>
                </Select>
              )}
            />
          </div>

          <p className="text-sm text-muted-foreground">{t('themeHint')}</p>

          <Button type="submit" disabled={!isDirty || saveMutation.isPending}>
            {saveMutation.isPending ? t('saving') : t('save')}
          </Button>
        </form>
      )}
    </SettingsShell>
  );
}
