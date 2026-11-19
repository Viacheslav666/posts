'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import type { AntispamRule, AntispamWordList, Channel, Project } from '@posts/api-client';
import { Button, Input, toast } from '@posts/ui';
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { useMemo, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';

import {
  AutomationChannelPicker,
  KeywordListEditor,
} from '@/features/automations-common';
import { FormField } from '@/features/auth/components/form-field';
import { parseKeywordPaste } from '@/features/automations-common/model/keywords';
import { queryKeys } from '@/shared/api-client';
import { getErrorMessage } from '@/shared/lib/errors';

import {
  activateAntispamRule,
  createAntispamRule,
  listAntispamWordLists,
  patchAntispamRule,
} from '../api/antispam-api';
import {
  buildConfig,
  configAsApiBlob,
  defaultFormValues,
  formValuesFromRule,
  previewReplyTemplate,
} from '../model/config';
import { antispamRuleFormSchema, type AntispamRuleFormValues } from '../model/schemas';
import { belongsToWorkspace } from '../model/scope';
import {
  ANTISPAM_NETWORKS,
  ATTACHMENT_KEYS,
  sectionsForNetwork,
  type AttachmentKey,
} from '../model/types';
import { CheckboxRow } from './checkbox-row';

type RuleFormProps = {
  workspaceId: string;
  project: Project;
  channels: Channel[];
  rule?: AntispamRule;
};

export function RuleForm({ workspaceId, project, channels, rule }: RuleFormProps) {
  const t = useTranslations('automations.antispam.form');
  const tSections = useTranslations('automations.antispam.sections');
  const tAttach = useTranslations('automations.antispam.attachments');
  const router = useRouter();
  const queryClient = useQueryClient();

  const antispamChannels = useMemo(
    () =>
      channels.filter(
        (channel) =>
          belongsToWorkspace(channel, workspaceId) &&
          channel.status === 'active' &&
          channel.network &&
          ANTISPAM_NETWORKS.includes(channel.network as (typeof ANTISPAM_NETWORKS)[number]),
      ),
    [channels, workspaceId],
  );

  const wordListsQuery = useInfiniteQuery({
    queryKey: queryKeys.antispam.wordLists(workspaceId),
    queryFn: ({ pageParam }) =>
      listAntispamWordLists({ workspaceId, cursor: pageParam, limit: 50 }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => (last.hasMore && last.nextCursor ? last.nextCursor : undefined),
  });
  const wordLists = useMemo(
    () =>
      (wordListsQuery.data?.pages ?? [])
        .flatMap((page) => page.items ?? [])
        .filter((list) => belongsToWorkspace(list, workspaceId)),
    [wordListsQuery.data?.pages, workspaceId],
  );

  const {
    register,
    handleSubmit,
    control,
    watch,
    setValue,
    formState: { errors },
  } = useForm<AntispamRuleFormValues>({
    resolver: zodResolver(antispamRuleFormSchema) as never,
    defaultValues: rule
      ? formValuesFromRule(rule)
      : defaultFormValues(antispamChannels[0]?.id, antispamChannels[0]?.network),
  });

  const channelId = watch('channelId');
  const channel = channels.find((c) => c.id === channelId);
  const network = channel?.network;
  const availableSections = sectionsForNetwork(network);
  const dryRun = watch('dryRun');
  const replyTemplate = watch('replyTemplate');
  const replyEnabled = watch('replyEnabled');
  const blackListIds = watch('blackListIds');
  const whiteListIds = watch('whiteListIds');

  const saveMutation = useMutation({
    mutationFn: async (values: AntispamRuleFormValues) => {
      if (rule && !belongsToWorkspace(rule, workspaceId)) {
        throw new Error('rule is outside this workspace');
      }
      const config = configAsApiBlob(buildConfig(values), rule?.config);
      if (rule?.id) {
        return patchAntispamRule(rule.id, {
          name: values.name,
          priority: values.priority,
          sections: values.sections,
          config,
          dryRun: values.dryRun,
          version: rule.version ?? 1,
        });
      }
      const created = await createAntispamRule(workspaceId, project.id!, {
        name: values.name,
        channelId: values.channelId,
        priority: values.priority,
        sections: values.sections,
        config,
        dryRun: values.dryRun,
      });
      if (values.activate && created.id) {
        return activateAntispamRule(created.id);
      }
      return created;
    },
    onSuccess: async (saved) => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.antispam.all(workspaceId) });
      toast({ title: rule ? t('updateSuccess') : t('createSuccess') });
      router.push(`/app/${workspaceId}/automations/antispam/${saved.id}`);
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('errorTitle'),
        description: getErrorMessage(error),
      });
    },
  });

  const onSubmit = handleSubmit((values) => saveMutation.mutate(values));

  const toggleListId = (
    field: 'blackListIds' | 'whiteListIds',
    listId: string,
    checked: boolean,
  ) => {
    const current = field === 'blackListIds' ? blackListIds : whiteListIds;
    const next = checked
      ? [...new Set([...current, listId])]
      : current.filter((id) => id !== listId);
    setValue(field, next, { shouldDirty: true });
  };

  const blackLists = wordLists.filter((list) => list.mode === 'black');
  const whiteLists = wordLists.filter((list) => list.mode === 'white');

  return (
    <form
      className="space-y-8"
      onSubmit={onSubmit}
      data-testid="antispam-rule-form"
      noValidate
    >
      <section className="space-y-4" data-testid="antispam-form-basics">
        <FormField
          label={t('name')}
          error={errors.name ? t(`errors.${errors.name.message as 'nameRequired'}`) : undefined}
          data-testid="antispam-rule-name"
          {...register('name')}
        />

        {!rule ? (
          <Controller
            control={control}
            name="channelId"
            render={({ field }) => (
              <div className="space-y-1">
                <AutomationChannelPicker
                  channels={antispamChannels}
                  value={field.value}
                  onChange={(id) => {
                    field.onChange(id);
                    const next = channels.find((c) => c.id === id);
                    const sections = sectionsForNetwork(next?.network);
                    if (sections[0]) {
                      setValue('sections', [sections[0]], { shouldDirty: true });
                    }
                  }}
                  networks={ANTISPAM_NETWORKS}
                />
                {errors.channelId ? (
                  <p className="text-sm text-destructive">{t('errors.channelRequired')}</p>
                ) : null}
              </div>
            )}
          />
        ) : null}

        <div className="grid gap-3 sm:grid-cols-2">
          <div className="space-y-1">
            <label htmlFor="antispam-priority" className="text-sm font-medium">
              {t('priority')}
            </label>
            <Input
              id="antispam-priority"
              type="number"
              data-testid="antispam-rule-priority"
              {...register('priority', { valueAsNumber: true })}
            />
          </div>
        </div>
      </section>

      <section className="space-y-3" data-testid="antispam-form-sections">
        <h3 className="text-sm font-semibold">{t('sectionsTitle')}</h3>
        <p className="text-xs text-muted-foreground">{t('sectionsHint')}</p>
        <Controller
          control={control}
          name="sections"
          render={({ field }) => (
            <div className="flex flex-col gap-2">
              {availableSections.map((section) => (
                <CheckboxRow
                  key={section}
                  id={`antispam-section-${section}`}
                  label={tSections(section)}
                  checked={field.value.includes(section)}
                  data-testid={`antispam-section-${section}`}
                  onChange={(event) => {
                    const checked = event.target.checked;
                    field.onChange(
                      checked
                        ? [...field.value, section]
                        : field.value.filter((s) => s !== section),
                    );
                  }}
                />
              ))}
              {availableSections.length === 0 ? (
                <p className="text-sm text-muted-foreground">{t('noSections')}</p>
              ) : null}
            </div>
          )}
        />
        {errors.sections ? (
          <p role="alert" className="text-sm text-destructive">
            {t('errors.sectionsRequired')}
          </p>
        ) : null}
      </section>

      <section className="space-y-4" data-testid="antispam-form-keywords">
        <h3 className="text-sm font-semibold">{t('keywordsTitle')}</h3>
        <Controller
          control={control}
          name="blackWords"
          render={({ field }) => (
            <div className="space-y-2">
              <p className="text-sm font-medium">{t('blackWords')}</p>
              <KeywordListEditor
                id="antispam-black-words"
                value={field.value}
                onChange={field.onChange}
                matchMode={watch('keywordMatchMode')}
                onMatchModeChange={(mode) => setValue('keywordMatchMode', mode)}
              />
            </div>
          )}
        />
        <Controller
          control={control}
          name="whiteWords"
          render={({ field }) => (
            <div className="space-y-2">
              <p className="text-sm font-medium">{t('whiteWords')}</p>
              <KeywordListEditor
                id="antispam-white-words"
                value={field.value}
                onChange={field.onChange}
                matchMode={watch('keywordMatchMode')}
                onMatchModeChange={(mode) => setValue('keywordMatchMode', mode)}
              />
            </div>
          )}
        />
        <WordListPickers
          title={t('blackLists')}
          lists={blackLists}
          selected={blackListIds}
          onToggle={(id, checked) => toggleListId('blackListIds', id, checked)}
          testIdPrefix="antispam-black-list"
        />
        <WordListPickers
          title={t('whiteLists')}
          lists={whiteLists}
          selected={whiteListIds}
          onToggle={(id, checked) => toggleListId('whiteListIds', id, checked)}
          testIdPrefix="antispam-white-list"
        />
      </section>

      <section className="space-y-3" data-testid="antispam-form-links">
        <h3 className="text-sm font-semibold">{t('linksTitle')}</h3>
        <CheckboxRow label={t('linksAll')} {...register('linksAll')} data-testid="antispam-links-all" />
        <CheckboxRow label={t('linksVk')} {...register('linksVk')} data-testid="antispam-links-vk" />
        <LinesField
          id="antispam-domain-whitelist"
          label={t('domainWhitelist')}
          testId="antispam-domain-whitelist"
          initial={watch('domainWhitelist')}
          onParsed={(lines) => setValue('domainWhitelist', lines, { shouldDirty: true })}
        />
      </section>

      <section className="space-y-3" data-testid="antispam-form-attachments">
        <h3 className="text-sm font-semibold">{t('attachmentsTitle')}</h3>
        {ATTACHMENT_KEYS.map((key) => (
          <Controller
            key={key}
            control={control}
            name={`attachments.${key}` as const}
            render={({ field }) => (
              <CheckboxRow
                id={`antispam-attach-${key}`}
                label={tAttach(key as AttachmentKey)}
                checked={field.value}
                data-testid={`antispam-attach-${key}`}
                onChange={(event) => field.onChange(event.target.checked)}
              />
            )}
          />
        ))}
      </section>

      <section className="space-y-3" data-testid="antispam-form-caps">
        <h3 className="text-sm font-semibold">{t('capsTitle')}</h3>
        <CheckboxRow
          label={t('capsEnabled')}
          {...register('capsEnabled')}
          data-testid="antispam-caps-enabled"
        />
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="space-y-1">
            <label htmlFor="antispam-caps-ratio" className="text-sm font-medium">
              {t('capsRatio')}
            </label>
            <Input
              id="antispam-caps-ratio"
              type="number"
              step="0.05"
              min={0}
              max={1}
              data-testid="antispam-caps-ratio"
              {...register('capsRatio', { valueAsNumber: true })}
            />
          </div>
          <div className="space-y-1">
            <label htmlFor="antispam-caps-min" className="text-sm font-medium">
              {t('capsMinLetters')}
            </label>
            <Input
              id="antispam-caps-min"
              type="number"
              data-testid="antispam-caps-min"
              {...register('capsMinLetters', { valueAsNumber: true })}
            />
          </div>
        </div>
      </section>

      <section className="space-y-3" data-testid="antispam-form-repeat">
        <h3 className="text-sm font-semibold">{t('repeatTitle')}</h3>
        <CheckboxRow
          label={t('repeatEnabled')}
          {...register('repeatEnabled')}
          data-testid="antispam-repeat-enabled"
        />
        <div className="space-y-1">
          <label htmlFor="antispam-repeat-window" className="text-sm font-medium">
            {t('repeatWindowHours')}
          </label>
          <Input
            id="antispam-repeat-window"
            type="number"
            data-testid="antispam-repeat-window"
            {...register('repeatWindowHours', { valueAsNumber: true })}
          />
        </div>
      </section>

      <section className="space-y-3" data-testid="antispam-form-authors">
        <h3 className="text-sm font-semibold">{t('authorsTitle')}</h3>
        <CheckboxRow
          label={t('authorsCommunities')}
          {...register('authorsCommunities')}
          data-testid="antispam-authors-communities"
        />
        <CheckboxRow
          label={t('authorsNonMembers')}
          {...register('authorsNonMembers')}
          data-testid="antispam-authors-non-members"
        />
        {network === 'tg' ? (
          <CheckboxRow
            label={t('authorsSenderChat')}
            hint={t('authorsSenderChatHint')}
            {...register('authorsSenderChat')}
            data-testid="antispam-authors-sender-chat"
          />
        ) : null}
      </section>

      <section className="space-y-3" data-testid="antispam-form-context">
        <h3 className="text-sm font-semibold">{t('contextTitle')}</h3>
        <CheckboxRow
          label={t('contextUnderAds')}
          {...register('contextUnderAds')}
          data-testid="antispam-context-under-ads"
        />
        <CheckboxRow
          label={t('contextUnderPinned')}
          {...register('contextUnderPinned')}
          data-testid="antispam-context-under-pinned"
        />
      </section>

      <section className="space-y-3" data-testid="antispam-form-actions">
        <h3 className="text-sm font-semibold">{t('actionsTitle')}</h3>
        <CheckboxRow
          label={t('actionDelete')}
          {...register('actionDelete')}
          data-testid="antispam-action-delete"
        />
        <CheckboxRow
          label={t('banEnabled')}
          {...register('banEnabled')}
          data-testid="antispam-ban-enabled"
        />
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="space-y-1">
            <label htmlFor="antispam-ban-days" className="text-sm font-medium">
              {t('banDays')}
            </label>
            <Input
              id="antispam-ban-days"
              type="number"
              data-testid="antispam-ban-days"
              {...register('banDays', { valueAsNumber: true })}
            />
          </div>
          <div className="space-y-1">
            <label htmlFor="antispam-ban-reason" className="text-sm font-medium">
              {t('banReason')}
            </label>
            <Input id="antispam-ban-reason" data-testid="antispam-ban-reason" {...register('banReason')} />
          </div>
        </div>
        <CheckboxRow
          label={t('banCommentVisible')}
          {...register('banCommentVisible')}
          data-testid="antispam-ban-comment-visible"
        />
        <CheckboxRow
          label={t('replyEnabled')}
          {...register('replyEnabled')}
          data-testid="antispam-reply-enabled"
        />
        {replyEnabled ? (
          <div className="space-y-2">
            <label htmlFor="antispam-reply-template" className="text-sm font-medium">
              {t('replyTemplate')}
            </label>
            <textarea
              id="antispam-reply-template"
              className="min-h-20 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
              data-testid="antispam-reply-template"
              {...register('replyTemplate')}
            />
            <p className="text-xs text-muted-foreground" data-testid="antispam-reply-preview">
              {t('replyPreview')}: {previewReplyTemplate(replyTemplate, ['ссылка', 'CAPS'])}
            </p>
          </div>
        ) : null}
      </section>

      <section className="space-y-3" data-testid="antispam-form-exceptions">
        <h3 className="text-sm font-semibold">{t('exceptionsTitle')}</h3>
        <LinesField
          id="antispam-admin-ids"
          label={t('adminIds')}
          testId="antispam-admin-ids"
          initial={watch('adminIds')}
          onParsed={(lines) => setValue('adminIds', lines, { shouldDirty: true })}
        />
        <LinesField
          id="antispam-user-whitelist"
          label={t('userWhitelist')}
          testId="antispam-user-whitelist"
          initial={watch('userWhitelist')}
          onParsed={(lines) => setValue('userWhitelist', lines, { shouldDirty: true })}
        />
        <CheckboxRow
          label={t('excludeOwnCommunity')}
          {...register('excludeOwnCommunity')}
          data-testid="antispam-exclude-own"
        />
      </section>

      <section className="space-y-3" data-testid="antispam-form-dry-run">
        <CheckboxRow
          label={t('dryRun')}
          hint={t('dryRunHint')}
          {...register('dryRun')}
          data-testid="antispam-dry-run"
        />
        {dryRun ? (
          <p className="text-sm text-muted-foreground" data-testid="antispam-dry-run-explain">
            {t('dryRunExplain')}
          </p>
        ) : null}
        {!rule ? (
          <CheckboxRow
            label={t('activate')}
            {...register('activate')}
            data-testid="antispam-activate-on-create"
          />
        ) : null}
      </section>

      <div className="flex flex-wrap gap-2">
        <Button type="submit" disabled={saveMutation.isPending} data-testid="antispam-rule-submit">
          {rule ? t('save') : t('create')}
        </Button>
      </div>
    </form>
  );
}

function LinesField({
  id,
  label,
  testId,
  initial,
  onParsed,
}: {
  id: string;
  label: string;
  testId: string;
  initial: string[];
  onParsed: (lines: string[]) => void;
}) {
  const [raw, setRaw] = useState(() => initial.join('\n'));
  return (
    <div className="space-y-1">
      <label htmlFor={id} className="text-sm font-medium">
        {label}
      </label>
      <textarea
        id={id}
        className="min-h-20 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
        data-testid={testId}
        value={raw}
        onChange={(event) => {
          const next = event.target.value;
          setRaw(next);
          onParsed(parseKeywordPaste(next));
        }}
      />
    </div>
  );
}

function WordListPickers({
  title,
  lists,
  selected,
  onToggle,
  testIdPrefix,
}: {
  title: string;
  lists: AntispamWordList[];
  selected: string[];
  onToggle: (id: string, checked: boolean) => void;
  testIdPrefix: string;
}) {
  if (lists.length === 0) return null;
  return (
    <div className="space-y-2">
      <p className="text-sm font-medium">{title}</p>
      <div className="flex flex-col gap-2">
        {lists.map((list) => (
          <CheckboxRow
            key={list.id}
            id={`${testIdPrefix}-${list.id}`}
            label={`${list.name} (${list.words?.length ?? 0})`}
            checked={selected.includes(list.id!)}
            data-testid={`${testIdPrefix}-${list.id}`}
            onChange={(event) => onToggle(list.id!, event.target.checked)}
          />
        ))}
      </div>
    </div>
  );
}
