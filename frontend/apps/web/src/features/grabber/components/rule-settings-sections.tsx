'use client';

import { MacroHints, SettingsSection } from '@posts/ui';
import { useTranslations } from 'next-intl';
import type { Control, FieldErrors, UseFormRegister } from 'react-hook-form';
import { Controller, useWatch } from 'react-hook-form';

import type { GrabberRuleFormValues } from '../model/schemas';
import {
  DAYS_OF_WEEK,
  SKIP_IF_HAS_ATTACHMENT_FLAGS,
  SKIP_IF_NO_ATTACHMENT_FLAGS,
  STRIP_FLAGS,
  TEXT_MACROS,
} from '../model/types';
import { CheckboxRow } from './checkbox-row';

type SectionsProps = {
  register: UseFormRegister<GrabberRuleFormValues>;
  control: Control<GrabberRuleFormValues>;
  errors: FieldErrors<GrabberRuleFormValues>;
  scheduleEnabled: boolean;
  firstCommentEnabled: boolean;
};

export function RuleSettingsSections({
  register,
  control,
  errors,
  scheduleEnabled,
  firstCommentEnabled,
}: SectionsProps) {
  const t = useTranslations('grabber.form');
  const settingError = (key: keyof GrabberRuleFormValues) => {
    const code = errors[key]?.message;
    return code ? t(`errors.${code}`) : undefined;
  };
  const deleteAfterEnabled = useWatch({ control, name: 'deleteAfterEnabled' });
  const aiRewriteEnabled = useWatch({ control, name: 'aiRewriteEnabled' });

  return (
    <div className="space-y-4" data-testid="grabber-settings-sections">
      <SettingsSection
        title={t('sections.doNotPublish')}
        description={t('sections.doNotPublishHint')}
        data-testid="grabber-section-filters"
      >
        <div className="grid gap-2 sm:grid-cols-2">
          <CheckboxRow label={t('filters.excludeAds')} {...register('excludeAds')} />
          <CheckboxRow label={t('filters.excludeReposts')} {...register('excludeReposts')} />
          <CheckboxRow label={t('filters.excludeLinks')} {...register('excludeLinks')} />
          <CheckboxRow label={t('filters.skipIfNoText')} {...register('skipIfNoText')} />
          <CheckboxRow label={t('filters.skipIfNoMedia')} {...register('skipIfNoMedia')} />
          <CheckboxRow label={t('filters.skipIfAnyText')} {...register('skipIfAnyText')} />
          <CheckboxRow label={t('filters.skipIfHomoglyphs')} {...register('skipIfHomoglyphs')} />
          <CheckboxRow label={t('filters.skipIfCaps')} {...register('skipIfCaps')} />
          <CheckboxRow label={t('filters.skipIfPhone')} {...register('skipIfPhone')} />
          <CheckboxRow label={t('filters.onlyGroupPosts')} {...register('onlyGroupPosts')} />
        </div>

        <p className="text-xs font-medium text-muted-foreground">{t('filters.missingTypes')}</p>
        <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
          {SKIP_IF_NO_ATTACHMENT_FLAGS.map((flag) => (
            <CheckboxRow key={flag} label={t(`filters.${flag}`)} {...register(flag)} />
          ))}
        </div>

        <p className="text-xs font-medium text-muted-foreground">{t('filters.hasTypes')}</p>
        <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
          {SKIP_IF_HAS_ATTACHMENT_FLAGS.map((flag) => (
            <CheckboxRow key={flag} label={t(`filters.${flag}`)} {...register(flag)} />
          ))}
        </div>

        <div className="grid gap-3 md:grid-cols-3">
          <label className="space-y-1 text-sm">
            <span>{t('filters.capsThreshold')}</span>
            <input
              type="number"
              step="0.05"
              min={0.1}
              max={1}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
              data-testid="grabber-caps-threshold"
              {...register('capsThreshold', { valueAsNumber: true })}
            />
          </label>
          <label className="space-y-1 text-sm">
            <span>{t('filters.capsMinLetters')}</span>
            <input
              type="number"
              min={1}
              max={200}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
              {...register('capsMinLetters', { valueAsNumber: true })}
            />
          </label>
          <label className="space-y-1 text-sm">
            <span>{t('filters.maxAgeDays')}</span>
            <input
              type="number"
              min={0}
              max={3650}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
              data-testid="grabber-max-age-days"
              {...register('maxAgeDays', { valueAsNumber: true })}
            />
            <span className="block text-xs text-muted-foreground">{t('filters.maxAgeDaysHint')}</span>
          </label>
        </div>

        <label className="block space-y-1 text-sm">
          <span>{t('filters.stopWords')}</span>
          <textarea
            className="min-h-[64px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
            data-testid="grabber-stop-words"
            placeholder={t('filters.keywordsPlaceholder')}
            {...register('stopWordsText')}
          />
          <span className="block text-xs text-muted-foreground">{t('filters.stopWordsHint')}</span>
        </label>
        <div className="space-y-2 rounded-md border border-border p-3" data-testid="grabber-only-these">
          <p className="text-sm font-medium">{t('filters.onlyThese')}</p>
          <p className="text-xs text-muted-foreground">{t('filters.onlyTheseHint')}</p>
          <CheckboxRow label={t('filters.requirePhone')} {...register('requirePhone')} />
          <CheckboxRow
            label={t('filters.requirePhoneOrSignature')}
            {...register('requirePhoneOrSignature')}
          />
        </div>
        <div className="grid gap-3 md:grid-cols-2">
          <label className="block space-y-1 text-sm">
            <span>{t('filters.keywordInclude')}</span>
            <textarea
              className="min-h-[64px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
              placeholder={t('filters.keywordsPlaceholder')}
              {...register('keywordIncludeText')}
            />
          </label>
          <label className="block space-y-1 text-sm">
            <span>{t('filters.keywordExclude')}</span>
            <textarea
              className="min-h-[64px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
              placeholder={t('filters.keywordsPlaceholder')}
              {...register('keywordExcludeText')}
            />
          </label>
        </div>

        <div className="grid gap-3 md:grid-cols-3">
          <CheckboxRow
            label={t('filters.similarityDedup')}
            {...register('similarityDedupEnabled')}
          />
          <label className="space-y-1 text-sm">
            <span>{t('filters.similarityThreshold')}</span>
            <input
              type="number"
              step="0.05"
              min={0.5}
              max={1}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
              {...register('similarityThreshold', { valueAsNumber: true })}
            />
          </label>
          <label className="space-y-1 text-sm">
            <span>{t('filters.similarityLookback')}</span>
            <input
              type="number"
              min={1}
              max={100}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
              {...register('similarityLookback', { valueAsNumber: true })}
            />
          </label>
        </div>
      </SettingsSection>

      <SettingsSection
        title={t('sections.strip')}
        description={t('sections.stripHint')}
        data-testid="grabber-section-strip"
      >
        <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
          {STRIP_FLAGS.map((flag) => (
            <CheckboxRow key={flag} label={t(`strip.${flag}`)} {...register(flag)} />
          ))}
        </div>
      </SettingsSection>

      <SettingsSection
        title={t('sections.replacements')}
        description={t('sections.replacementsHint')}
        data-testid="grabber-section-replacements"
      >
        <label className="block space-y-1 text-sm">
          <span>{t('replacements.map')}</span>
          <textarea
            className="min-h-[88px] w-full rounded-md border border-input bg-background px-3 py-2 font-mono text-sm"
            placeholder={t('replacements.mapPlaceholder')}
            data-testid="grabber-word-replacements"
            {...register('wordReplacementsText')}
          />
        </label>
        <label className="block space-y-1 text-sm">
          <span>{t('replacements.removeKeywords')}</span>
          <textarea
            className="min-h-[64px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
            placeholder={t('filters.keywordsPlaceholder')}
            data-testid="grabber-remove-keywords"
            {...register('removeKeywordsText')}
          />
          <span className="block text-xs text-muted-foreground">{t('replacements.removeKeywordsHint')}</span>
        </label>
      </SettingsSection>

      <SettingsSection
        title={t('sections.schedule')}
        description={t('sections.scheduleHint')}
        data-testid="grabber-section-schedule"
      >
        <CheckboxRow label={t('schedule.enabled')} {...register('scheduleEnabled')} />
        {scheduleEnabled ? (
          <div className="space-y-3">
            <label className="block space-y-1 text-sm">
              <span>{t('schedule.timezone')}</span>
              <input
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                data-testid="grabber-schedule-timezone"
                {...register('scheduleTimezone')}
              />
            </label>
            <fieldset>
              <legend className="mb-2 text-sm font-medium">{t('schedule.days')}</legend>
              <div className="flex flex-wrap gap-3">
                {DAYS_OF_WEEK.map((day) => (
                  <Controller
                    key={day}
                    control={control}
                    name="scheduleDays"
                    render={({ field }) => {
                      const checked = field.value.includes(day);
                      return (
                        <label className="inline-flex items-center gap-2 text-sm">
                          <input
                            type="checkbox"
                            checked={checked}
                            onChange={(event) => {
                              if (event.target.checked) {
                                field.onChange([...field.value, day].sort((a, b) => a - b));
                              } else {
                                const next = field.value.filter((d) => d !== day);
                                field.onChange(next.length ? next : [day]);
                              }
                            }}
                          />
                          {t(`schedule.day.${day}`)}
                        </label>
                      );
                    }}
                  />
                ))}
              </div>
            </fieldset>
            <div className="grid gap-3 sm:grid-cols-2">
              <label className="space-y-1 text-sm">
                <span>{t('schedule.hoursFrom')}</span>
                <input
                  type="number"
                  min={0}
                  max={23}
                  className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                  data-testid="grabber-hours-from"
                  {...register('hoursFrom', { valueAsNumber: true })}
                />
              </label>
              <label className="space-y-1 text-sm">
                <span>{t('schedule.hoursTo')}</span>
                <input
                  type="number"
                  min={0}
                  max={24}
                  className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                  data-testid="grabber-hours-to"
                  {...register('hoursTo', { valueAsNumber: true })}
                />
              </label>
            </div>
          </div>
        ) : null}
      </SettingsSection>

      <SettingsSection
        title={t('sections.limits')}
        description={t('sections.limitsHint')}
        data-testid="grabber-section-limits"
      >
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="space-y-1 text-sm">
            <span>{t('limits.perHour')}</span>
            <input
              type="number"
              min={1}
              max={60}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
              {...register('rateLimitPerHour', { valueAsNumber: true })}
            />
          </label>
          <label className="space-y-1 text-sm">
            <span>{t('limits.perDay')}</span>
            <input
              type="number"
              min={1}
              max={500}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
              data-testid="grabber-limit-day"
              {...register('rateLimitPerDay', { valueAsNumber: true })}
            />
          </label>
          <label className="space-y-1 text-sm">
            <span>{t('limits.perSourceDay')}</span>
            <input
              type="number"
              min={0}
              max={500}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
              data-testid="grabber-limit-source-day"
              {...register('rateLimitPerSourcePerDay', { valueAsNumber: true })}
            />
            <span className="block text-xs text-muted-foreground">{t('limits.perSourceDayHint')}</span>
          </label>
          <label className="space-y-1 text-sm">
            <span>{t('limits.minInterval')}</span>
            <input
              type="number"
              min={0}
              max={120}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
              data-testid="grabber-min-interval"
              {...register('minIntervalMinutes', { valueAsNumber: true })}
            />
          </label>
        </div>
        <CheckboxRow label={t('limits.autoPause')} {...register('autoPauseOnErrors')} />
      </SettingsSection>

      <SettingsSection
        title={t('sections.firstComment')}
        description={t('sections.firstCommentHint')}
        data-testid="grabber-section-first-comment"
      >
        <p className="text-xs text-muted-foreground">{t('publish.modeHint')}</p>
        <CheckboxRow label={t('firstComment.enabled')} {...register('firstCommentEnabled')} />
        {firstCommentEnabled ? (
          <div className="space-y-3">
            <label className="block space-y-1 text-sm">
              <span>{t('firstComment.text')}</span>
              <textarea
                className="min-h-[88px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                data-testid="grabber-first-comment-text"
                {...register('firstCommentText')}
              />
              <span className="block text-xs text-muted-foreground">{t('firstComment.textHint')}</span>
              {settingError('firstCommentText') ? (
                <span role="alert" className="block text-xs text-destructive">
                  {settingError('firstCommentText')}
                </span>
              ) : null}
            </label>
            <label className="block space-y-1 text-sm">
              <span>{t('firstComment.attachments')}</span>
              <textarea
                className="min-h-[64px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                data-testid="grabber-first-comment-attachments"
                placeholder={t('firstComment.attachmentsPlaceholder')}
                {...register('firstCommentAttachments')}
              />
              <span className="block text-xs text-muted-foreground">{t('firstComment.attachmentsHint')}</span>
              {settingError('firstCommentAttachments') ? (
                <span role="alert" className="block text-xs text-destructive">
                  {settingError('firstCommentAttachments')}
                </span>
              ) : null}
            </label>
            <MacroHints macros={TEXT_MACROS} label={t('macros.label')} />
          </div>
        ) : null}
      </SettingsSection>

      <SettingsSection
        title={t('sections.vkPublish')}
        description={t('sections.vkPublishHint')}
        data-testid="grabber-section-vk-publish"
      >
        <CheckboxRow label={t('publish.closeComments')} {...register('closeComments')} />
        <CheckboxRow
          label={t('publish.appendAuthorSignature')}
          {...register('appendAuthorSignature')}
        />
        <p className="text-xs text-muted-foreground">{t('publish.authorSignatureHint')}</p>
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="space-y-1 text-sm">
            <span>{t('publish.actionButtonTitle')}</span>
            <input
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
              maxLength={40}
              data-testid="grabber-action-button-title"
              {...register('actionButtonTitle')}
            />
          </label>
          <label className="space-y-1 text-sm">
            <span>{t('publish.actionButtonUrl')}</span>
            <input
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
              data-testid="grabber-action-button-url"
              placeholder="https://"
              {...register('actionButtonUrl')}
            />
          </label>
        </div>
        <p className="text-xs text-muted-foreground">{t('publish.actionButtonHint')}</p>
        <CheckboxRow label={t('publish.deleteAfter')} {...register('deleteAfterEnabled')} />
        {deleteAfterEnabled ? (
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="space-y-1 text-sm">
              <span>{t('publish.deleteAfterAmount')}</span>
              <input
                type="number"
                min={1}
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                data-testid="grabber-delete-after-amount"
                {...register('deleteAfterAmount', { valueAsNumber: true })}
              />
            </label>
            <label className="space-y-1 text-sm">
              <span>{t('publish.deleteAfterUnit')}</span>
              <select
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                data-testid="grabber-delete-after-unit"
                {...register('deleteAfterUnit')}
              >
                <option value="minutes">{t('publish.unitMinutes')}</option>
                <option value="hours">{t('publish.unitHours')}</option>
                <option value="days">{t('publish.unitDays')}</option>
              </select>
            </label>
          </div>
        ) : null}
        <CheckboxRow label={t('publish.aiRewrite')} {...register('aiRewriteEnabled')} />
        {aiRewriteEnabled ? (
          <label className="block space-y-1 text-sm">
            <span>{t('publish.aiModel')}</span>
            <select
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
              data-testid="grabber-ai-model"
              {...register('aiModel')}
            >
              <option value="stub">{t('publish.aiModelStub')}</option>
            </select>
            <span className="block text-xs text-muted-foreground">{t('publish.aiHint')}</span>
          </label>
        ) : null}
      </SettingsSection>

      <SettingsSection
        title={t('sections.attached')}
        description={t('sections.attachedHint')}
        data-testid="grabber-section-attached"
      >
        <label className="block space-y-1 text-sm">
          <span>{t('attached.prepend')}</span>
          <span className="block text-xs text-muted-foreground">{t('attached.prependHint')}</span>
          <textarea
            className="min-h-[64px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
            data-testid="grabber-prepend-text"
            {...register('prependText')}
          />
        </label>
        <label className="block space-y-1 text-sm">
          <span>{t('attached.append')}</span>
          <span className="block text-xs text-muted-foreground">{t('attached.appendHint')}</span>
          <textarea
            className="min-h-[64px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
            data-testid="grabber-append-text"
            {...register('appendText')}
          />
        </label>
        <MacroHints macros={TEXT_MACROS} label={t('macros.label')} />
        <div className="flex flex-wrap gap-4">
          <CheckboxRow
            label={t('attached.attachWhenEmpty')}
            {...register('attachWhenNoAttachments')}
          />
          <CheckboxRow label={t('attached.linkToSource')} {...register('linkToSource')} />
          <CheckboxRow label={t('attached.linkToAuthor')} {...register('linkToAuthor')} />
        </div>
        <fieldset className="space-y-2">
          <legend className="text-sm font-medium">{t('attached.mode')}</legend>
          <Controller
            control={control}
            name="attachmentsMode"
            render={({ field }) => (
              <div className="flex flex-wrap gap-4 text-sm">
                <label className="inline-flex items-center gap-2">
                  <input
                    type="radio"
                    value="grid"
                    checked={field.value === 'grid'}
                    onChange={() => field.onChange('grid')}
                  />
                  {t('attached.modeGrid')}
                </label>
                <label className="inline-flex items-center gap-2">
                  <input
                    type="radio"
                    value="carousel"
                    checked={field.value === 'carousel'}
                    onChange={() => field.onChange('carousel')}
                  />
                  {t('attached.modeCarousel')}
                </label>
              </div>
            )}
          />
        </fieldset>
        <p className="text-xs text-muted-foreground">{t('attached.attachmentsNote')}</p>
      </SettingsSection>
    </div>
  );
}
