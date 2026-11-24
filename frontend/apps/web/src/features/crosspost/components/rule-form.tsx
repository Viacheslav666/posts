'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import type { Channel, CrosspostRule, CrosspostWarning, Project } from '@posts/api-client';
import { Button, toast } from '@posts/ui';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { useMemo, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';

import {
  AutomationChannelPicker,
  KeywordListEditor,
  ScheduleWindowEditor,
} from '@/features/automations-common';
import { FormField } from '@/features/auth/components/form-field';
import { PlanLimitPaywall } from '@/features/billing/components/plan-limit-paywall';
import { queryKeys } from '@/shared/api-client';
import {
  getErrorMessage,
  isPlanLimitExceeded,
} from '@/shared/lib/errors';

import { createCrosspostRule, patchCrosspostRule } from '../api/crosspost-api';
import {
  buildCrosspostConfig,
  configAsApiBlob,
  defaultFormValues,
  formValuesFromRule,
} from '../model/config';
import { crosspostRuleFormSchema, type CrosspostRuleFormValues } from '../model/schemas';
import { FORM_STEPS, type FormStep, SOURCE_NETWORKS } from '../model/types';
import { CheckboxRow } from './checkbox-row';
import { TargetMultiPicker } from './target-multi-picker';

type RuleFormProps = {
  workspaceId: string;
  project: Project;
  channels: Channel[];
  rule?: CrosspostRule;
  /** Warnings from create/patch response for review step. */
  initialWarnings?: CrosspostWarning[];
};

export function RuleForm({
  workspaceId,
  project,
  channels,
  rule,
  initialWarnings = [],
}: RuleFormProps) {
  const t = useTranslations('automations.crosspost.form');
  const tSteps = useTranslations('automations.crosspost.steps');
  const tProblems = useTranslations('problems');
  const router = useRouter();
  const queryClient = useQueryClient();
  const [stepIndex, setStepIndex] = useState(0);
  const [showPaywall, setShowPaywall] = useState(false);
  const [warnings, setWarnings] = useState<CrosspostWarning[]>(
    initialWarnings.length ? initialWarnings : rule?.warnings ?? [],
  );

  const sourceChannels = useMemo(
    () =>
      channels.filter(
        (channel) =>
          channel.status === 'active' &&
          channel.network &&
          SOURCE_NETWORKS.includes(channel.network as (typeof SOURCE_NETWORKS)[number]),
      ),
    [channels],
  );

  const {
    register,
    handleSubmit,
    control,
    watch,
    setValue,
    trigger,
    formState: { errors },
  } = useForm<CrosspostRuleFormValues>({
    resolver: zodResolver(crosspostRuleFormSchema) as never,
    defaultValues: rule
      ? formValuesFromRule(rule)
      : defaultFormValues(sourceChannels[0]?.id),
  });

  const sourceChannelId = watch('sourceChannelId');
  const targetChannelIds = watch('targetChannelIds');
  const scheduleEnabled = watch('scheduleEnabled');
  const sourceChannel = channels.find((c) => c.id === sourceChannelId);
  const step = FORM_STEPS[stepIndex] ?? 'source';

  const saveMutation = useMutation({
    mutationFn: async (values: CrosspostRuleFormValues) => {
      const config = configAsApiBlob(buildCrosspostConfig(values));
      const targets = values.targetChannelIds.map((targetChannelId) => ({ targetChannelId }));
      if (rule?.id) {
        return patchCrosspostRule(rule.id, {
          name: values.name,
          config,
          targets,
          version: rule.version ?? 1,
        });
      }
      return createCrosspostRule(workspaceId, project.id!, {
        name: values.name,
        sourceChannelId: values.sourceChannelId,
        config,
        targets,
        activate: values.activate,
      });
    },
    onSuccess: async (saved) => {
      setWarnings(saved.warnings ?? []);
      await queryClient.invalidateQueries({ queryKey: queryKeys.crosspost.all(workspaceId) });
      toast({ title: rule ? t('updateSuccess') : t('createSuccess') });
      router.push(`/app/${workspaceId}/automations/crosspost/${saved.id}`);
    },
    onError: (error) => {
      let description = getErrorMessage(error);
      if (isPlanLimitExceeded(error)) {
        description = tProblems('plan_limit_exceeded');
        setShowPaywall(true);
      }
      toast({
        variant: 'destructive',
        title: t('errorTitle'),
        description,
      });
    },
  });

  const goNext = async () => {
    const fieldsByStep: Record<FormStep, (keyof CrosspostRuleFormValues)[]> = {
      source: ['name', 'sourceChannelId'],
      targets: ['targetChannelIds'],
      filters: [],
      transform: [],
      schedule: ['delayMinutes'],
      review: [],
    };
    const fields = fieldsByStep[step];
    const ok = fields.length === 0 ? true : await trigger(fields);
    if (!ok) return;
    if (stepIndex < FORM_STEPS.length - 1) {
      setStepIndex((i) => i + 1);
    }
  };

  const goBack = () => {
    if (stepIndex > 0) setStepIndex((i) => i - 1);
  };

  const onSubmit = handleSubmit((values) => saveMutation.mutate(values));

  const cycleWarning = warnings.find((w) => w.code === 'cycle_detected');

  return (
    <form
      className="space-y-6"
      onSubmit={onSubmit}
      data-testid="crosspost-rule-form"
      noValidate
    >
      <ol className="flex flex-wrap gap-2" aria-label={tSteps('label')}>
        {FORM_STEPS.map((key, index) => (
          <li key={key}>
            <button
              type="button"
              className={`rounded-md border px-2.5 py-1 text-xs ${
                index === stepIndex
                  ? 'border-primary bg-primary/10 text-primary'
                  : 'border-border text-muted-foreground'
              }`}
              data-testid={`crosspost-step-${key}`}
              onClick={() => setStepIndex(index)}
            >
              {index + 1}. {tSteps(key)}
            </button>
          </li>
        ))}
      </ol>

      {showPaywall ? (
        <PlanLimitPaywall workspaceId={workspaceId} limitKey="crosspost_rules" />
      ) : null}

      {step === 'source' ? (
        <section className="space-y-4" data-testid="crosspost-step-panel-source">
          <FormField
            label={t('name')}
            error={errors.name ? t(`errors.${errors.name.message as 'nameRequired'}`) : undefined}
            data-testid="crosspost-rule-name"
            {...register('name')}
          />
          <Controller
            control={control}
            name="sourceChannelId"
            render={({ field }) => (
              <AutomationChannelPicker
                channels={sourceChannels}
                value={field.value}
                onChange={field.onChange}
                networks={['vk', 'tg', 'max']}
                id="crosspost-source"
              />
            )}
          />
          {errors.sourceChannelId ? (
            <p role="alert" className="text-xs text-destructive">
              {t('errors.sourceRequired')}
            </p>
          ) : null}
          {sourceChannel?.network === 'tg' ? (
            <p
              className="rounded-md border border-border bg-muted/40 px-3 py-2 text-sm"
              data-testid="crosspost-tg-group-hint"
            >
              {t('tgGroupHint')}
            </p>
          ) : null}
        </section>
      ) : null}

      {step === 'targets' ? (
        <section className="space-y-4" data-testid="crosspost-step-panel-targets">
          <Controller
            control={control}
            name="targetChannelIds"
            render={({ field }) => (
              <TargetMultiPicker
                channels={channels}
                value={field.value}
                onChange={field.onChange}
                excludeChannelId={sourceChannelId}
                error={
                  errors.targetChannelIds
                    ? t(`errors.${errors.targetChannelIds.message as 'targetsRequired'}`)
                    : undefined
                }
              />
            )}
          />
        </section>
      ) : null}

      {step === 'filters' ? (
        <section className="space-y-4" data-testid="crosspost-step-panel-filters">
          <div className="grid gap-4 sm:grid-cols-2">
            <Controller
              control={control}
              name="keywordInclude"
              render={({ field }) => (
                <KeywordListEditor
                  value={field.value}
                  onChange={field.onChange}
                  matchMode="substring"
                  onMatchModeChange={() => undefined}
                  id="crosspost-keyword-include"
                />
              )}
            />
            <Controller
              control={control}
              name="keywordExclude"
              render={({ field }) => (
                <div>
                  <p className="mb-2 text-sm font-medium">{t('keywordExclude')}</p>
                  <KeywordListEditor
                    value={field.value}
                    onChange={field.onChange}
                    matchMode="substring"
                    onMatchModeChange={() => undefined}
                    id="crosspost-keyword-exclude"
                  />
                </div>
              )}
            />
          </div>

          <div className="grid gap-2 sm:grid-cols-2" data-testid="crosspost-filter-flags">
            <CheckboxRow label={t('filters.skipIfNoText')} {...register('skipIfNoText')} />
            <CheckboxRow label={t('filters.skipIfNoImages')} {...register('skipIfNoImages')} />
            <CheckboxRow label={t('filters.excludeVideo')} {...register('excludeVideo')} />
            <CheckboxRow label={t('filters.excludeClips')} {...register('excludeClips')} />
            <CheckboxRow label={t('filters.excludeLinks')} {...register('excludeLinks')} />
            <CheckboxRow label={t('filters.excludeSnippets')} {...register('excludeSnippets')} />
            <CheckboxRow label={t('filters.excludeArticles')} {...register('excludeArticles')} />
            <CheckboxRow label={t('filters.excludePoll')} {...register('excludePoll')} />
            <CheckboxRow label={t('filters.excludeGeo')} {...register('excludeGeo')} />
            <CheckboxRow label={t('filters.excludeAudio')} {...register('excludeAudio')} />
            <CheckboxRow
              label={t('filters.excludeAds')}
              hint={t('filters.excludeAdsHint')}
              {...register('excludeAds')}
            />
            <CheckboxRow label={t('filters.includeReposts')} {...register('includeReposts')} />
            <CheckboxRow label={t('filters.excludeReposts')} {...register('excludeReposts')} />
            <CheckboxRow
              label={t('filters.onlyGroupPostsInTgGroups')}
              {...register('onlyGroupPostsInTgGroups')}
            />
            <CheckboxRow
              label={t('filters.includePostsPublishedViaPosts')}
              {...register('includePostsPublishedViaPosts')}
            />
            <CheckboxRow
              label={t('filters.includeSuggestPublished')}
              {...register('includeSuggestPublished')}
            />
          </div>
        </section>
      ) : null}

      {step === 'transform' ? (
        <section className="space-y-4" data-testid="crosspost-step-panel-transform">
          <div className="grid gap-2 sm:grid-cols-2">
            <CheckboxRow label={t('transform.preserveFormatting')} {...register('preserveFormatting')} />
            <CheckboxRow label={t('transform.removeLinks')} {...register('removeLinks')} />
            <CheckboxRow label={t('transform.removeHashtags')} {...register('removeHashtags')} />
            <CheckboxRow label={t('transform.addSourceLink')} {...register('addSourceLink')} />
            <CheckboxRow label={t('transform.closeComments')} {...register('closeComments')} />
            <CheckboxRow label={t('transform.publishAsVkRepost')} {...register('publishAsVkRepost')} />
            <CheckboxRow label={t('transform.vkVideoAsLink')} {...register('vkVideoAsLink')} />
          </div>
          <div className="space-y-1.5">
            <label htmlFor="crosspost-long-text-mode" className="text-sm font-medium">
              {t('transform.longTextMode')}
            </label>
            <select
              id="crosspost-long-text-mode"
              className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
              data-testid="crosspost-long-text-mode"
              {...register('longTextMode')}
            >
              <option value="split">{t('transform.longTextSplit')}</option>
              <option value="truncate_read_more">{t('transform.longTextTruncate')}</option>
            </select>
          </div>
        </section>
      ) : null}

      {step === 'schedule' ? (
        <section className="space-y-4" data-testid="crosspost-step-panel-schedule">
          <FormField
            label={t('delayMinutes')}
            type="number"
            min={0}
            data-testid="crosspost-delay-minutes"
            {...register('delayMinutes', { valueAsNumber: true })}
          />
          <CheckboxRow
            label={t('scheduleEnabled')}
            data-testid="crosspost-schedule-enabled"
            {...register('scheduleEnabled')}
          />
          {scheduleEnabled ? (
            <Controller
              control={control}
              name="scheduleDays"
              render={({ field }) => (
                <ScheduleWindowEditor
                  value={{
                    days: field.value as never,
                    intervals: watch('scheduleIntervals'),
                    timezone: watch('scheduleTimezone'),
                  }}
                  onChange={(next) => {
                    field.onChange(next.days);
                    setValue('scheduleIntervals', next.intervals);
                    setValue('scheduleTimezone', next.timezone);
                  }}
                />
              )}
            />
          ) : null}
        </section>
      ) : null}

      {step === 'review' ? (
        <section className="space-y-4" data-testid="crosspost-step-panel-review">
          <dl className="grid gap-2 text-sm sm:grid-cols-2">
            <div>
              <dt className="text-muted-foreground">{t('name')}</dt>
              <dd className="font-medium">{watch('name') || '-'}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">{t('source')}</dt>
              <dd className="font-medium">
                {sourceChannel?.title ?? sourceChannelId ?? '-'}
              </dd>
            </div>
            <div className="sm:col-span-2">
              <dt className="text-muted-foreground">{t('targets')}</dt>
              <dd className="font-medium">
                {targetChannelIds
                  .map((id) => channels.find((c) => c.id === id)?.title ?? id)
                  .join(', ') || '-'}
              </dd>
            </div>
          </dl>

          {cycleWarning ? (
            <div
              role="alert"
              className="rounded-md border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-sm"
              data-testid="crosspost-cycle-warning"
            >
              <p className="font-medium">{t('cycleTitle')}</p>
              <p className="mt-1 text-muted-foreground">{t('cycleBody')}</p>
            </div>
          ) : null}

          {!rule ? (
            <CheckboxRow
              label={t('activate')}
              data-testid="crosspost-activate-on-create"
              {...register('activate')}
            />
          ) : null}
        </section>
      ) : null}

      <div className="flex flex-wrap gap-2">
        {stepIndex > 0 ? (
          <Button type="button" variant="outline" onClick={goBack} data-testid="crosspost-form-back">
            {t('back')}
          </Button>
        ) : null}
        {stepIndex < FORM_STEPS.length - 1 ? (
          <Button type="button" onClick={() => void goNext()} data-testid="crosspost-form-next">
            {t('next')}
          </Button>
        ) : (
          <Button
            type="submit"
            disabled={saveMutation.isPending}
            data-testid="crosspost-rule-submit"
          >
            {rule ? t('save') : t('create')}
          </Button>
        )}
      </div>
    </form>
  );
}
