'use client';

import { Input, Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@posts/ui';
import type { Control, UseFormRegister, UseFormSetValue, UseFormWatch } from 'react-hook-form';
import { Controller } from 'react-hook-form';
import { useTranslations } from 'next-intl';

import {
  KeywordListEditor,
  LimitInput,
  ScheduleWindowEditor,
} from '@/features/automations-common';

import type { SuggestSettingsFormValues } from '../model/schemas';
import { FORBID_ATTACHMENT_TYPES } from '../model/types';
import { CheckboxRow } from './checkbox-row';

type SettingsSectionsProps = {
  control: Control<SuggestSettingsFormValues>;
  register: UseFormRegister<SuggestSettingsFormValues>;
  watch: UseFormWatch<SuggestSettingsFormValues>;
  setValue: UseFormSetValue<SuggestSettingsFormValues>;
  disabled?: boolean;
};

export function SettingsSections({
  control,
  register,
  watch,
  setValue,
  disabled,
}: SettingsSectionsProps) {
  const t = useTranslations('automations.suggest.form');

  const scheduleEnabled = watch('scheduleEnabled');
  const scheduleDays = watch('scheduleDays');
  const scheduleIntervals = watch('scheduleIntervals');
  const scheduleTimezone = watch('scheduleTimezone');
  const forbidAttachments = watch('forbidAttachments');

  return (
    <div className="space-y-8">
      <section className="space-y-3" data-testid="suggest-section-mode">
        <h3 className="text-base font-semibold">{t('sections.mode')}</h3>
        <Controller
          control={control}
          name="mode"
          render={({ field }) => (
            <Select
              value={field.value}
              onValueChange={field.onChange}
              disabled={disabled}
            >
              <SelectTrigger data-testid="suggest-mode" aria-label={t('mode')}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="manual">{t('modes.manual')}</SelectItem>
                <SelectItem value="auto">{t('modes.auto')}</SelectItem>
                <SelectItem value="hybrid">{t('modes.hybrid')}</SelectItem>
              </SelectContent>
            </Select>
          )}
        />
      </section>

      <section className="space-y-4" data-testid="suggest-section-filters">
        <h3 className="text-base font-semibold">{t('sections.filters')}</h3>
        <Controller
          control={control}
          name="keywordInclude"
          render={({ field }) => (
            <div>
              <p className="mb-2 text-sm font-medium">{t('keywordInclude')}</p>
              <KeywordListEditor
                value={field.value}
                onChange={field.onChange}
                matchMode={watch('keywordMatchMode')}
                onMatchModeChange={(mode) => setValue('keywordMatchMode', mode)}
                disabled={disabled}
                id="suggest-keyword-include"
              />
            </div>
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
                matchMode={watch('keywordMatchMode')}
                onMatchModeChange={(mode) => setValue('keywordMatchMode', mode)}
                disabled={disabled}
                id="suggest-keyword-exclude"
              />
            </div>
          )}
        />
        <Controller
          control={control}
          name="stopWords"
          render={({ field }) => (
            <div>
              <p className="mb-2 text-sm font-medium">{t('stopWords')}</p>
              <KeywordListEditor
                value={field.value}
                onChange={field.onChange}
                matchMode={watch('keywordMatchMode')}
                onMatchModeChange={(mode) => setValue('keywordMatchMode', mode)}
                disabled={disabled}
                id="suggest-stop-words"
              />
            </div>
          )}
        />
        <div className="flex flex-wrap gap-4">
          <CheckboxRow
            label={t('requireText')}
            disabled={disabled}
            {...register('requireText')}
          />
          <CheckboxRow
            label={t('requireImage')}
            disabled={disabled}
            {...register('requireImage')}
          />
          <CheckboxRow
            label={t('requireMessagesAllowed')}
            disabled={disabled}
            {...register('requireMessagesAllowed')}
          />
          <CheckboxRow
            label={t('forbidClosedProfile')}
            disabled={disabled}
            {...register('forbidClosedProfile')}
          />
        </div>
        <div>
          <p className="mb-2 text-sm font-medium">{t('forbidAttachments')}</p>
          <div className="flex flex-wrap gap-3">
            {FORBID_ATTACHMENT_TYPES.map((type) => {
              const checked = forbidAttachments.includes(type);
              return (
                <CheckboxRow
                  key={type}
                  label={t(`attachmentTypes.${type}`)}
                  checked={checked}
                  disabled={disabled}
                  onChange={() => {
                    const next = checked
                      ? forbidAttachments.filter((item) => item !== type)
                      : [...forbidAttachments, type];
                    setValue('forbidAttachments', next, { shouldDirty: true });
                  }}
                />
              );
            })}
          </div>
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="space-y-1 text-sm">
            <span className="font-medium">{t('authorBlacklist')}</span>
            <Input
              disabled={disabled}
              data-testid="suggest-author-blacklist"
              value={watch('authorBlacklist').join(', ')}
              onChange={(event) =>
                setValue(
                  'authorBlacklist',
                  event.target.value
                    .split(/[,;\s]+/)
                    .map((part) => part.trim())
                    .filter(Boolean),
                  { shouldDirty: true },
                )
              }
            />
          </label>
          <label className="space-y-1 text-sm">
            <span className="font-medium">{t('authorWhitelist')}</span>
            <Input
              disabled={disabled}
              data-testid="suggest-author-whitelist"
              value={watch('authorWhitelist').join(', ')}
              onChange={(event) =>
                setValue(
                  'authorWhitelist',
                  event.target.value
                    .split(/[,;\s]+/)
                    .map((part) => part.trim())
                    .filter(Boolean),
                  { shouldDirty: true },
                )
              }
            />
          </label>
        </div>
      </section>

      <section className="space-y-3" data-testid="suggest-section-limits">
        <h3 className="text-base font-semibold">{t('sections.limits')}</h3>
        <p className="text-sm text-muted-foreground">{t('limitsHint')}</p>
        <div className="grid gap-3 sm:grid-cols-2">
          <Controller
            control={control}
            name="perAuthorDay"
            render={({ field }) => (
              <LimitInput
                id="suggest-per-author"
                aria-label={t('perAuthor')}
                value={{ amount: field.value || null, period: 'day' }}
                disabled={disabled}
                onChange={(next) => {
                  field.onChange(next.amount ?? 0);
                  if (next.period === 'hour') setValue('perAuthorHour', next.amount ?? 0);
                  if (next.period === 'week') setValue('perAuthorWeek', next.amount ?? 0);
                }}
              />
            )}
          />
          <div className="grid grid-cols-3 gap-2">
            <label className="text-xs">
              {t('hour')}
              <Input type="number" min={0} disabled={disabled} {...register('perAuthorHour', { valueAsNumber: true })} />
            </label>
            <label className="text-xs">
              {t('day')}
              <Input type="number" min={0} disabled={disabled} {...register('perAuthorDay', { valueAsNumber: true })} />
            </label>
            <label className="text-xs">
              {t('week')}
              <Input type="number" min={0} disabled={disabled} {...register('perAuthorWeek', { valueAsNumber: true })} />
            </label>
          </div>
          <div className="grid grid-cols-3 gap-2 sm:col-span-2">
            <label className="text-xs">
              {t('globalHour')}
              <Input type="number" min={0} disabled={disabled} {...register('globalHour', { valueAsNumber: true })} />
            </label>
            <label className="text-xs">
              {t('globalDay')}
              <Input type="number" min={0} disabled={disabled} {...register('globalDay', { valueAsNumber: true })} />
            </label>
            <label className="text-xs">
              {t('globalWeek')}
              <Input type="number" min={0} disabled={disabled} {...register('globalWeek', { valueAsNumber: true })} />
            </label>
          </div>
        </div>
      </section>

      <section className="space-y-3" data-testid="suggest-section-schedule">
        <h3 className="text-base font-semibold">{t('sections.schedule')}</h3>
        <CheckboxRow
          label={t('scheduleEnabled')}
          disabled={disabled}
          {...register('scheduleEnabled')}
        />
        {scheduleEnabled ? (
          <>
            <ScheduleWindowEditor
              disabled={disabled}
              value={{
                days: scheduleDays as never,
                intervals: scheduleIntervals,
                timezone: scheduleTimezone,
              }}
              onChange={(next) => {
                setValue('scheduleDays', next.days, { shouldDirty: true });
                setValue('scheduleIntervals', next.intervals, { shouldDirty: true });
                setValue('scheduleTimezone', next.timezone, { shouldDirty: true });
              }}
            />
            <label className="block text-sm">
              {t('minIntervalMinutes')}
              <Input
                type="number"
                min={0}
                disabled={disabled}
                {...register('minIntervalMinutes', { valueAsNumber: true })}
              />
            </label>
          </>
        ) : null}
      </section>

      <section className="space-y-3" data-testid="suggest-section-dedup">
        <h3 className="text-base font-semibold">{t('sections.dedup')}</h3>
        <CheckboxRow label={t('dedupEnabled')} disabled={disabled} {...register('dedupEnabled')} />
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="text-sm">
            {t('dedupLookbackDays')}
            <Input
              type="number"
              min={1}
              disabled={disabled}
              {...register('dedupLookbackDays', { valueAsNumber: true })}
            />
          </label>
          <label className="text-sm">
            {t('dedupSimilarityThreshold')}
            <Input
              type="number"
              min={0}
              max={1}
              step={0.01}
              disabled={disabled}
              {...register('dedupSimilarityThreshold', { valueAsNumber: true })}
            />
          </label>
        </div>
      </section>

      <section className="space-y-3" data-testid="suggest-section-reject">
        <h3 className="text-base font-semibold">{t('sections.reject')}</h3>
        <Controller
          control={control}
          name="rejectPolicy"
          render={({ field }) => (
            <Select value={field.value} onValueChange={field.onChange} disabled={disabled}>
              <SelectTrigger data-testid="suggest-reject-policy">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="delete">{t('rejectPolicies.delete')}</SelectItem>
                <SelectItem value="keep">{t('rejectPolicies.keep')}</SelectItem>
                <SelectItem value="delete_after">{t('rejectPolicies.delete_after')}</SelectItem>
              </SelectContent>
            </Select>
          )}
        />
        <label className="block text-sm">
          {t('deleteAfterHours')}
          <Input
            type="number"
            min={0}
            disabled={disabled}
            {...register('deleteAfterHours', { valueAsNumber: true })}
          />
        </label>
        <CheckboxRow label={t('notifyAuthor')} disabled={disabled} {...register('notifyAuthor')} />
      </section>

      <section className="space-y-3" data-testid="suggest-section-publish">
        <h3 className="text-base font-semibold">{t('sections.publish')}</h3>
        <CheckboxRow label={t('signed')} disabled={disabled} {...register('signed')} />
        <Controller
          control={control}
          name="publishEditMode"
          render={({ field }) => (
            <Select value={field.value} onValueChange={field.onChange} disabled={disabled}>
              <SelectTrigger data-testid="suggest-publish-edit-mode">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="wall_post_edit">{t('publishEditModes.wall_post_edit')}</SelectItem>
                <SelectItem value="fallback_new_post">
                  {t('publishEditModes.fallback_new_post')}
                </SelectItem>
              </SelectContent>
            </Select>
          )}
        />
        <CheckboxRow
          label={t('firstCommentEnabled')}
          disabled={disabled}
          {...register('firstCommentEnabled')}
        />
        {watch('firstCommentEnabled') ? (
          <label className="block text-sm">
            {t('firstCommentText')}
            <Input disabled={disabled} {...register('firstCommentText')} />
          </label>
        ) : null}
        <label className="block text-sm">
          {t('enrichmentAppendText')}
          <Input disabled={disabled} {...register('enrichmentAppendText')} />
        </label>
        <label className="block text-sm">
          {t('enrichmentHashtags')}
          <Input
            disabled={disabled}
            data-testid="suggest-hashtags"
            value={watch('enrichmentHashtags').join(' ')}
            onChange={(event) =>
              setValue(
                'enrichmentHashtags',
                event.target.value
                  .split(/[\s,]+/)
                  .map((part) => part.trim())
                  .filter(Boolean),
                { shouldDirty: true },
              )
            }
          />
        </label>
      </section>

      <section className="space-y-3" data-testid="suggest-section-ads">
        <h3 className="text-base font-semibold">{t('sections.ads')}</h3>
        <CheckboxRow
          label={t('adsPauseEnabled')}
          disabled={disabled}
          {...register('adsPauseEnabled')}
        />
        <CheckboxRow
          label={t('adsDetectMarkedAsAds')}
          disabled={disabled}
          {...register('adsDetectMarkedAsAds')}
        />
        <CheckboxRow
          label={t('adsDetectErid')}
          disabled={disabled}
          {...register('adsDetectErid')}
        />
        <label className="block text-sm">
          {t('adsPauseAfterMinutes')}
          <Input
            type="number"
            min={0}
            disabled={disabled}
            {...register('adsPauseAfterMinutes', { valueAsNumber: true })}
          />
        </label>
      </section>
    </div>
  );
}
