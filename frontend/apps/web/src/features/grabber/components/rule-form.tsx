'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import type { Channel, GrabberRule, Project } from '@posts/api-client';
import {
  Button,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  toast,
} from '@posts/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { useEffect, useMemo, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';

import { FormField } from '@/features/auth/components/form-field';
import { PlanLimitPaywall } from '@/features/billing/components/plan-limit-paywall';
import { queryKeys } from '@/shared/api-client';
import {
  getErrorMessage,
  isIngestAttributionRequired,
  isIngestResponsibilityRequired,
  isIngestSsrfBlocked,
  isPlanLimitExceeded,
} from '@/shared/lib/errors';

import {
  createIngestRule,
  listIngestRules,
  patchIngestRule,
  previewIngestSource,
} from '../api/grabber-api';
import {
  applyClonedConfigToForm,
  buildGrabberConfig,
  defaultFormValues,
  formValuesFromRule,
} from '../model/config';
import { grabberRuleFormSchema, type GrabberRuleFormValues } from '../model/schemas';
import { isTargetModeAvailable } from '../model/mode-capabilities';
import { normalizeSourceIds } from '../model/source-ids';
import { useGrabberModeCapabilities } from '../model/use-grabber-mode-capabilities';
import {
  isPostponedTarget,
  isRepostTarget,
  isWebsiteSourceEnabled,
  MAX_SOURCES_PER_RULE,
  SCAN_INTERVALS,
  TARGET_MODES,
} from '../model/types';
import { AutoModeWarning } from './auto-mode-warning';
import { CloneConfigControl } from './clone-config-control';
import { RuleSettingsSections } from './rule-settings-sections';

type RuleFormProps = {
  workspaceId: string;
  project: Project;
  channels: Channel[];
  rule?: GrabberRule;
};

export function RuleForm({ workspaceId, project, channels, rule }: RuleFormProps) {
  const t = useTranslations('grabber.form');
  const tProblems = useTranslations('grabber.problems');
  const tCommonProblems = useTranslations('problems');
  const router = useRouter();
  const queryClient = useQueryClient();
  const websiteEnabled = isWebsiteSourceEnabled();
  const [previewLines, setPreviewLines] = useState<string[]>([]);
  const [showPaywall, setShowPaywall] = useState(false);

  const {
    register,
    handleSubmit,
    control,
    watch,
    setValue,
    reset,
    formState: { errors },
  } = useForm<GrabberRuleFormValues>({
    // Zod 4 + RHF resolver inference is looser than our form values.
    resolver: zodResolver(grabberRuleFormSchema) as never,
    defaultValues: rule ? formValuesFromRule(rule) : defaultFormValues(channels[0]?.id),
  });

  const type = watch('type');
  const publishMode = watch('publishMode');
  const targetMode = watch('targetMode');
  const minIntervalMinutes = watch('minIntervalMinutes');
  const scheduleEnabled = watch('scheduleEnabled');
  const firstCommentEnabled = watch('firstCommentEnabled');
  const adminCommunities = useMemo(
    () =>
      channels.filter(
        (channel) =>
          channel.network === 'vk' &&
          channel.status === 'active' &&
          channel.type !== 'profile' &&
          channel.type !== 'chat',
      ),
    [channels],
  );

  const { caps, matrix } = useGrabberModeCapabilities(targetMode, minIntervalMinutes || 0);

  useEffect(() => {
    if (!caps.interval && minIntervalMinutes > 0) {
      setValue('minIntervalMinutes', 0);
    }
    if (!caps.firstComment && firstCommentEnabled) {
      setValue('firstCommentEnabled', false);
    }
  }, [caps.interval, caps.firstComment, minIntervalMinutes, firstCommentEnabled, setValue]);

  const siblingRulesQuery = useQuery({
    queryKey: queryKeys.ingest.rules(workspaceId, project.id!),
    queryFn: () => listIngestRules({ workspaceId, projectId: project.id! }),
    enabled: Boolean(project.id),
  });

  const saveMutation = useMutation({
    mutationFn: async (values: GrabberRuleFormValues) => {
      const config = buildGrabberConfig(values);
      if (rule?.id) {
        return patchIngestRule(rule.id, {
          name: values.name,
          targetChannelId: values.targetChannelId,
          config,
          version: rule.version ?? 1,
        });
      }
      return createIngestRule(workspaceId, project.id!, {
        type: values.type,
        name: values.name,
        targetChannelId: values.targetChannelId,
        config,
        userResponsibilityAccepted: values.userResponsibilityAccepted,
        activate: values.activate,
      });
    },
    onSuccess: async (saved) => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.ingest.all(workspaceId) });
      toast({ title: rule ? t('updateSuccess') : t('createSuccess') });
      router.push(`/app/${workspaceId}/automations/grabber/${saved.id}`);
    },
    onError: (error) => {
      let description = getErrorMessage(error);
      if (isIngestSsrfBlocked(error)) description = tProblems('ingest_ssrf_blocked');
      if (isIngestResponsibilityRequired(error)) {
        description = tProblems('ingest_responsibility_required');
      }
      if (isIngestAttributionRequired(error)) {
        description = tProblems('ingest_attribution_required');
      }
      if (isPlanLimitExceeded(error)) {
        description = tCommonProblems('plan_limit_exceeded');
        setShowPaywall(true);
      }
      toast({
        variant: 'destructive',
        title: t('errorTitle'),
        description,
      });
    },
  });

  const previewMutation = useMutation({
    mutationFn: async () => {
      const values = watch();
      if (values.type === 'grabber') {
        return previewIngestSource(workspaceId, {
          type: 'grabber',
          sourceIds: normalizeSourceIds(values.sourceIdsText ?? ''),
          limit: 3,
        });
      }
      if (values.type === 'rss') {
        return previewIngestSource(workspaceId, {
          type: 'rss',
          rssUrl: values.rssUrl?.trim(),
          limit: 3,
        });
      }
      return previewIngestSource(workspaceId, {
        type: 'website',
        websiteUrls: normalizeSourceIds(values.websiteUrlsText ?? ''),
        websiteSelector: values.websiteSelector?.trim() || undefined,
        limit: 3,
      });
    },
    onSuccess: (data) => {
      setPreviewLines((data.items ?? []).map((item) => item.contentText ?? ''));
      toast({ title: t('previewSuccess') });
    },
    onError: (error) => {
      const description = isIngestSsrfBlocked(error)
        ? tProblems('ingest_ssrf_blocked')
        : getErrorMessage(error);
      toast({
        variant: 'destructive',
        title: t('errorTitle'),
        description,
      });
    },
  });

  const fieldError = (key: keyof GrabberRuleFormValues) => {
    const code = errors[key]?.message;
    return code ? t(`errors.${code}`) : undefined;
  };

  return (
    <form
      className="space-y-6"
      data-testid="grabber-rule-form"
      onSubmit={handleSubmit((values) => saveMutation.mutate(values))}
      noValidate
    >
      {showPaywall ? (
        <PlanLimitPaywall workspaceId={workspaceId} variant="hard" limitKey="grabber_rules" />
      ) : null}

      {rule?.id ? (
        <CloneConfigControl
          ruleId={rule.id}
          siblingRules={siblingRulesQuery.data?.items ?? []}
          onCloned={(cloned) => {
            reset(applyClonedConfigToForm(watch(), cloned.config ?? {}));
            void queryClient.invalidateQueries({ queryKey: queryKeys.ingest.all(workspaceId) });
          }}
        />
      ) : (
        <LocalImportControl
          siblingRules={(siblingRulesQuery.data?.items ?? []).filter((item) => item.config)}
          onImport={(imported) => reset(applyClonedConfigToForm(watch(), imported))}
        />
      )}

      <FormField
        label={t('name')}
        error={fieldError('name')}
        {...register('name')}
        data-testid="grabber-rule-name"
      />

      <div className="grid gap-4 md:grid-cols-2">
        <div className="space-y-1.5">
          <label className="text-sm font-medium" htmlFor="grabber-type">
            {t('type')}
          </label>
          <Controller
            control={control}
            name="type"
            render={({ field }) => (
              <Select
                value={field.value}
                onValueChange={(value) => field.onChange(value)}
                disabled={Boolean(rule)}
              >
                <SelectTrigger id="grabber-type" data-testid="grabber-rule-type">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="grabber">{t('types.grabber')}</SelectItem>
                  <SelectItem value="rss">{t('types.rss')}</SelectItem>
                  {websiteEnabled ? (
                    <SelectItem value="website">{t('types.website')}</SelectItem>
                  ) : null}
                </SelectContent>
              </Select>
            )}
          />
        </div>

        <div className="space-y-1.5">
          <label className="text-sm font-medium" htmlFor="grabber-channel">
            {t('channel')}
          </label>
          <Controller
            control={control}
            name="targetChannelId"
            render={({ field }) => (
              <Select value={field.value} onValueChange={field.onChange}>
                <SelectTrigger id="grabber-channel" data-testid="grabber-rule-channel">
                  <SelectValue placeholder={t('channelPlaceholder')} />
                </SelectTrigger>
                <SelectContent>
                  {adminCommunities.map((channel) => (
                    <SelectItem key={channel.id} value={channel.id!}>
                      {channel.title}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          />
          <p className="text-xs text-muted-foreground">{t('channelHint')}</p>
          {adminCommunities.length === 0 ? (
            <p className="text-xs text-muted-foreground" data-testid="grabber-no-admin-communities">
              {t('channelEmpty')}
            </p>
          ) : null}
          {fieldError('targetChannelId') ? (
            <p role="alert" className="text-xs text-destructive">
              {fieldError('targetChannelId')}
            </p>
          ) : null}
        </div>
      </div>

      {type === 'grabber' ? (
        <div className="space-y-1.5">
          <label className="text-sm font-medium" htmlFor="grabber-sources">
            {t('sourceIds')}
          </label>
          <textarea
            id="grabber-sources"
            className="min-h-[88px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
            placeholder={t('sourceIdsPlaceholder')}
            data-testid="grabber-source-ids"
            {...register('sourceIdsText')}
          />
          <p className="text-xs text-muted-foreground">
            {t('sourceIdsHint', { max: MAX_SOURCES_PER_RULE })}{' '}
            <a
              className="underline underline-offset-2"
              href="https://dev.vk.com/ru/reference/objects/group"
              target="_blank"
              rel="noopener noreferrer"
              data-testid="grabber-source-id-help"
            >
              {t('sourceIdHelp')}
            </a>
          </p>
          {fieldError('sourceIdsText') ? (
            <p role="alert" className="text-xs text-destructive">
              {fieldError('sourceIdsText')}
            </p>
          ) : null}
        </div>
      ) : null}

      {type === 'rss' ? (
        <FormField
          label={t('rssUrl')}
          error={fieldError('rssUrl')}
          placeholder="https://example.com/feed.xml"
          data-testid="grabber-rss-url"
          {...register('rssUrl')}
        />
      ) : null}

      {type === 'website' ? (
        <div className="space-y-4">
          <div className="space-y-1.5">
            <label className="text-sm font-medium" htmlFor="grabber-website-urls">
              {t('websiteUrls')}
            </label>
            <textarea
              id="grabber-website-urls"
              className="min-h-[88px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
              data-testid="grabber-website-urls"
              {...register('websiteUrlsText')}
            />
            {fieldError('websiteUrlsText') ? (
              <p role="alert" className="text-xs text-destructive">
                {fieldError('websiteUrlsText')}
              </p>
            ) : null}
          </div>
          <FormField label={t('websiteSelector')} {...register('websiteSelector')} />
        </div>
      ) : null}

      <div className="grid gap-4 md:grid-cols-2">
        <div className="space-y-1.5">
          <label className="text-sm font-medium">{t('interval')}</label>
          <Controller
            control={control}
            name="scanIntervalMinutes"
            render={({ field }) => (
              <Select
                value={String(field.value)}
                onValueChange={(value) => field.onChange(Number(value))}
              >
                <SelectTrigger data-testid="grabber-interval">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {SCAN_INTERVALS.map((value) => (
                    <SelectItem key={value} value={String(value)}>
                      {t(`intervals.${value}`)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          />
        </div>

        <div className="space-y-1.5">
          <label className="text-sm font-medium">{t('publishMode')}</label>
          <Controller
            control={control}
            name="publishMode"
            render={({ field }) => (
              <Select value={field.value} onValueChange={field.onChange}>
                <SelectTrigger data-testid="grabber-publish-mode">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="moderate">{t('modes.moderate')}</SelectItem>
                  <SelectItem value="auto">{t('modes.auto')}</SelectItem>
                  <SelectItem value="draft">{t('modes.draft')}</SelectItem>
                </SelectContent>
              </Select>
            )}
          />
        </div>
      </div>

      <div className="space-y-1.5">
        <label className="text-sm font-medium">{t('targetMode')}</label>
        <Controller
          control={control}
          name="targetMode"
          render={({ field }) => (
            <Select value={field.value} onValueChange={field.onChange}>
              <SelectTrigger data-testid="grabber-target-mode">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {TARGET_MODES.filter((mode) => type === 'grabber' || !isRepostTarget(mode)).map((mode) => {
                  const available = isTargetModeAvailable(mode, matrix);
                  return (
                    <SelectItem key={mode} value={mode} disabled={!available}>
                      {t(`targetModes.${mode}`)}
                    </SelectItem>
                  );
                })}
              </SelectContent>
            </Select>
          )}
        />
        <p className="text-xs text-muted-foreground">
          {isPostponedTarget(targetMode) ? t('targetModePostponedHint') : t('targetModeHint')}
        </p>
        {type === 'grabber' && !isTargetModeAvailable('vk_postponed_repost', matrix) ? (
          <p className="text-xs text-muted-foreground" data-testid="grabber-postponed-repost-unavailable">
            {t('targetModePostponedRepostUnavailable')}
          </p>
        ) : null}
        {fieldError('targetMode') ? (
          <p role="alert" className="text-xs text-destructive">
            {fieldError('targetMode')}
          </p>
        ) : null}
      </div>

      <div
        className="rounded-md border border-border p-3 text-sm"
        data-testid="grabber-mode-capabilities"
        data-action-button={String(caps.actionButton)}
        data-first-comment={String(caps.firstComment)}
        data-comment-attachments={String(caps.commentAttachments)}
        data-scheduled-delete={String(caps.scheduledDelete)}
        data-interval={String(caps.interval)}
      >
        <p className="font-medium">{t('capabilitiesTitle')}</p>
        <ul className="mt-1 list-disc space-y-1 pl-5 text-muted-foreground">
          {!caps.interval ? <li>{t('capabilities.interval')}</li> : null}
          {!caps.actionButton ? <li>{t('capabilities.actionButton')}</li> : null}
          {!caps.firstComment ? <li>{t('capabilities.firstComment')}</li> : null}
          {!caps.commentAttachments ? <li>{t('capabilities.commentAttachments')}</li> : null}
          {!caps.scheduledDelete ? <li>{t('capabilities.scheduledDelete')}</li> : null}
          {caps.interval && caps.actionButton && caps.firstComment && caps.commentAttachments && caps.scheduledDelete ? (
            <li>{t('capabilities.allAvailable')}</li>
          ) : null}
        </ul>
      </div>

      <AutoModeWarning visible={publishMode === 'auto'} />

      <RuleSettingsSections
        register={register}
        control={control}
        scheduleEnabled={scheduleEnabled}
        firstCommentEnabled={firstCommentEnabled}
        caps={caps}
        intervalError={fieldError('minIntervalMinutes')}
        firstCommentError={fieldError('firstCommentEnabled')}
      />

      <FormField
        label={t('attributionTemplate')}
        error={fieldError('attributionTemplate')}
        {...register('attributionTemplate')}
      />

      <label className="flex items-start gap-3 rounded-md border border-border p-3 text-sm">
        <input
          type="checkbox"
          className="mt-1 h-4 w-4"
          data-testid="grabber-notify-on-error"
          {...register('notifyOnError')}
        />
        <span>
          <span className="font-medium">{t('notifyOnError')}</span>
          <span className="mt-1 block text-muted-foreground">{t('notifyOnErrorHint')}</span>
        </span>
      </label>

      <label className="flex items-start gap-3 rounded-md border border-border p-3 text-sm">
        <input
          type="checkbox"
          className="mt-1 h-4 w-4"
          data-testid="grabber-responsibility"
          {...register('userResponsibilityAccepted')}
        />
        <span>
          <span className="font-medium">{t('responsibilityTitle')}</span>
          <span className="mt-1 block text-muted-foreground">{t('responsibilityBody')}</span>
        </span>
      </label>
      {fieldError('userResponsibilityAccepted') ? (
        <p role="alert" className="text-xs text-destructive">
          {fieldError('userResponsibilityAccepted')}
        </p>
      ) : null}

      {!rule ? (
        <label className="inline-flex items-center gap-2 text-sm">
          <input type="checkbox" {...register('activate')} />
          {t('activate')}
        </label>
      ) : null}

      {previewLines.length > 0 ? (
        <div className="space-y-2 rounded-md border border-border p-3" data-testid="grabber-preview">
          <p className="text-sm font-medium">{t('previewTitle')}</p>
          <p className="text-xs text-muted-foreground">{t('previewSourceOnly')}</p>
          <ul className="space-y-2 text-sm text-muted-foreground">
            {previewLines.map((line) => (
              <li key={line}>{line}</li>
            ))}
          </ul>
        </div>
      ) : null}

      <div className="flex flex-wrap gap-2">
        <Button type="submit" disabled={saveMutation.isPending} data-testid="grabber-rule-submit">
          {rule ? t('save') : t('create')}
        </Button>
        <Button
          type="button"
          variant="outline"
          disabled={previewMutation.isPending}
          onClick={() => previewMutation.mutate()}
          data-testid="grabber-preview-btn"
        >
          {t('preview')}
        </Button>
        <Button
          type="button"
          variant="ghost"
          onClick={() => {
            setValue('publishMode', 'moderate');
          }}
        >
          {t('resetModerate')}
        </Button>
      </div>
    </form>
  );
}

function LocalImportControl({
  siblingRules,
  onImport,
}: {
  siblingRules: GrabberRule[];
  onImport: (config: NonNullable<GrabberRule['config']>) => void;
}) {
  const t = useTranslations('grabber.form.clone');
  const [sourceRuleId, setSourceRuleId] = useState('');
  if (!siblingRules.length) {
    return (
      <p className="text-xs text-muted-foreground" data-testid="grabber-clone-empty">
        {t('empty')}
      </p>
    );
  }
  return (
    <div
      className="flex flex-wrap items-end gap-2 rounded-md border border-border p-3"
      data-testid="grabber-import-config"
    >
      <div className="min-w-[220px] flex-1 space-y-1.5">
        <label className="text-sm font-medium" htmlFor="grabber-import-source">
          {t('label')}
        </label>
        <Select value={sourceRuleId} onValueChange={setSourceRuleId}>
          <SelectTrigger id="grabber-import-source" data-testid="grabber-import-source">
            <SelectValue placeholder={t('placeholder')} />
          </SelectTrigger>
          <SelectContent>
            {siblingRules.map((item) => (
              <SelectItem key={item.id} value={item.id!}>
                {item.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <p className="text-xs text-muted-foreground">{t('createHint')}</p>
      </div>
      <Button
        type="button"
        variant="outline"
        disabled={!sourceRuleId}
        data-testid="grabber-import-submit"
        onClick={() => {
          const selected = siblingRules.find((item) => item.id === sourceRuleId);
          if (selected?.config) onImport(selected.config);
        }}
      >
        {t('submit')}
      </Button>
    </div>
  );
}
