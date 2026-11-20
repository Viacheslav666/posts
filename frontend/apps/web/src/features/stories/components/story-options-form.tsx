'use client';

import { Input, Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@posts/ui';
import { useTranslations } from 'next-intl';

import type { StoryTtl } from '@posts/api-client';

import { FieldLabelTooltip } from '@/features/editor/components/field-label-tooltip';

import { STORY_LINK_TEXTS, STORY_TTL_OPTIONS } from '../model/link-texts';
import type { StoryDraft } from '../model/types';

type StoryOptionsFormProps = {
  draft: StoryDraft;
  showOkTtlWarning: boolean;
  showVkTipsOption?: boolean;
  onChange: (patch: Partial<StoryDraft>) => void;
};

export function StoryOptionsForm({
  draft,
  showOkTtlWarning,
  showVkTipsOption = false,
  onChange,
}: StoryOptionsFormProps) {
  const t = useTranslations('stories.options');

  return (
    <section className="flex flex-col gap-4" data-testid="story-options-form" aria-label={t('label')}>
      <h2 className="text-sm font-semibold text-foreground">{t('title')}</h2>

      <label className="flex flex-col gap-1.5 text-sm">
        <span className="font-medium">{t('internalTitle')}</span>
        <Input
          value={draft.title}
          onChange={(event) => onChange({ title: event.target.value })}
          placeholder={t('internalTitlePlaceholder')}
          data-testid="story-title"
        />
      </label>

      <label className="flex flex-col gap-1.5 text-sm">
        <span className="font-medium">{t('scheduledAt')}</span>
        <Input
          type="datetime-local"
          value={toLocalInputValue(draft.scheduledAt)}
          onChange={(event) =>
            onChange({
              scheduledAt: event.target.value
                ? new Date(event.target.value).toISOString()
                : draft.scheduledAt,
            })
          }
          data-testid="story-scheduled-at"
        />
      </label>

      <label className="flex flex-col gap-1.5 text-sm">
        <span className="font-medium">{t('ttl')}</span>
        <Select
          value={draft.story.ttl ?? 'ttl_24h'}
          onValueChange={(value: string) =>
            onChange({ story: { ...draft.story, ttl: value as StoryTtl } })
          }
        >
          <SelectTrigger data-testid="story-ttl" aria-label={t('ttl')}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {STORY_TTL_OPTIONS.map((option) => (
              <SelectItem key={option.value} value={option.value}>
                {option.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        {showOkTtlWarning && draft.story.ttl !== 'ttl_24h' ? (
          <p className="text-xs text-amber-700 dark:text-amber-400" role="status" data-testid="story-ok-ttl-warning">
            {t('okTtlWarning')}
          </p>
        ) : null}
      </label>

      <div className="grid gap-3 sm:grid-cols-2">
        <label className="flex flex-col gap-1.5 text-sm">
          <span className="font-medium">{t('linkText')}</span>
          <Select
            value={draft.linkText || '__none__'}
            onValueChange={(value: string) =>
              onChange({ linkText: value === '__none__' ? '' : value })
            }
          >
            <SelectTrigger data-testid="story-link-text" aria-label={t('linkText')}>
              <SelectValue placeholder={t('linkTextPlaceholder')} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="__none__">{t('linkNone')}</SelectItem>
              {STORY_LINK_TEXTS.map((item) => (
                <SelectItem key={item.key} value={item.key}>
                  {item.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </label>
        <label className="flex flex-col gap-1.5 text-sm">
          <span className="font-medium">{t('linkUrl')}</span>
          <Input
            value={draft.linkUrl}
            onChange={(event) => onChange({ linkUrl: event.target.value })}
            placeholder={t('linkUrlPlaceholder')}
            data-testid="story-link-url"
          />
          <span className="text-xs text-muted-foreground">{t('linkUrlHint')}</span>
        </label>
      </div>

      {showVkTipsOption ? (
        <label className="flex items-center gap-2 text-sm" data-testid="story-add-to-news">
          <input
            type="checkbox"
            checked={draft.story.addToNews !== false}
            onChange={(event) =>
              onChange({
                story: { ...draft.story, addToNews: event.target.checked },
              })
            }
          />
          <FieldLabelTooltip label={t('addToNews')} tooltip={t('addToNewsTooltip')} />
        </label>
      ) : null}

      <label className="flex items-center gap-2 text-sm">
        <input
          type="checkbox"
          checked={Boolean(draft.story.deleteAfterPublish)}
          onChange={(event) =>
            onChange({
              story: { ...draft.story, deleteAfterPublish: event.target.checked },
            })
          }
          data-testid="story-delete-after-publish"
        />
        <span>{t('deleteAfterPublish')}</span>
      </label>

      <label className="flex flex-col gap-1.5 text-sm">
        <span className="font-medium">{t('autoDeleteAfter')}</span>
        <Input
          type="number"
          min={0}
          max={262920}
          value={draft.autoDeleteAfterMinutes ?? ''}
          onChange={(event) =>
            onChange({
              autoDeleteAfterMinutes:
                event.target.value === '' ? null : Number(event.target.value),
            })
          }
          placeholder={t('autoDeleteAfterPlaceholder')}
          data-testid="story-auto-delete-after"
        />
      </label>
    </section>
  );
}

function toLocalInputValue(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '';
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
