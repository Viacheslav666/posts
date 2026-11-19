'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import type { Channel, Project, SuggestSettings } from '@posts/api-client';
import { Button, toast } from '@posts/ui';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { useForm } from 'react-hook-form';

import { AutomationChannelPicker, CapabilityNotice } from '@/features/automations-common';
import { queryKeys } from '@/shared/api-client';
import { getErrorMessage } from '@/shared/lib/errors';

import {
  activateSuggestSettings,
  createSuggestSettings,
  patchSuggestSettings,
} from '../api/suggest-api';
import { channelHasPublishSuggested } from '../model/capabilities';
import {
  buildSuggestConfig,
  configAsApiBlob,
  defaultFormValues,
  formValuesFromSettings,
} from '../model/config';
import {
  suggestSettingsFormSchema,
  type SuggestSettingsFormValues,
} from '../model/schemas';
import { CheckboxRow } from './checkbox-row';
import { SettingsSections } from './settings-sections';

type SettingsFormProps = {
  workspaceId: string;
  project: Project;
  channels: Channel[];
  settings?: SuggestSettings;
  canManage: boolean;
};

export function SettingsForm({
  workspaceId,
  project,
  channels,
  settings,
  canManage,
}: SettingsFormProps) {
  const t = useTranslations('automations.suggest.form');
  const router = useRouter();
  const queryClient = useQueryClient();
  const vkChannels = channels.filter((channel) => channel.network === 'vk');

  const {
    register,
    handleSubmit,
    control,
    watch,
    setValue,
    formState: { isDirty },
  } = useForm<SuggestSettingsFormValues>({
    resolver: zodResolver(suggestSettingsFormSchema) as never,
    defaultValues: settings
      ? formValuesFromSettings(settings)
      : defaultFormValues(vkChannels[0]?.id),
  });

  const channelId = watch('channelId');
  const selectedChannel = channels.find((channel) => channel.id === channelId);
  const capable = channelHasPublishSuggested(selectedChannel);

  const saveMutation = useMutation({
    mutationFn: async (values: SuggestSettingsFormValues) => {
      const config = configAsApiBlob(buildSuggestConfig(values));
      if (settings?.id) {
        const patched = await patchSuggestSettings(settings.id, {
          mode: values.mode,
          config,
          version: settings.version ?? 1,
        });
        if (values.activate && patched.status !== 'active') {
          return activateSuggestSettings(settings.id);
        }
        return patched;
      }
      const created = await createSuggestSettings(workspaceId, project.id!, {
        channelId: values.channelId,
        mode: values.mode,
        config,
      });
      if (values.activate && created.id) {
        return activateSuggestSettings(created.id);
      }
      return created;
    },
    onSuccess: async (saved) => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.suggest.all(workspaceId) });
      toast({ title: settings ? t('updateSuccess') : t('createSuccess') });
      router.push(`/app/${workspaceId}/automations/suggest/${saved.id}`);
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('errorTitle'),
        description: getErrorMessage(error),
      });
    },
  });

  return (
    <form
      className="space-y-6"
      onSubmit={handleSubmit((values) => saveMutation.mutate(values))}
      data-testid="suggest-settings-form"
      noValidate
    >
      {!settings ? (
        <section className="space-y-3" data-testid="suggest-section-channel">
          <h3 className="text-base font-semibold">{t('sections.channel')}</h3>
          <AutomationChannelPicker
            channels={vkChannels}
            networks={['vk']}
            value={channelId}
            onChange={(id) => setValue('channelId', id, { shouldDirty: true, shouldValidate: true })}
            isChannelCapable={channelHasPublishSuggested}
            disabled={!canManage}
            id="suggest-channel-picker"
          />
          {!capable && channelId ? <CapabilityNotice variant="vk_user_wall" /> : null}
        </section>
      ) : null}

      <SettingsSections
        control={control}
        register={register}
        watch={watch}
        setValue={setValue}
        disabled={!canManage}
      />

      {canManage ? (
        <div className="flex flex-wrap items-center gap-3 border-t border-border pt-4">
          <CheckboxRow label={t('activate')} {...register('activate')} />
          <Button
            type="submit"
            disabled={saveMutation.isPending || (!isDirty && Boolean(settings))}
            data-testid="suggest-settings-save"
          >
            {settings ? t('save') : t('create')}
          </Button>
        </div>
      ) : null}
    </form>
  );
}
