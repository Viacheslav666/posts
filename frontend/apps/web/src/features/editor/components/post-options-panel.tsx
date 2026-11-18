'use client';

import type { PollOptions, PostVariantOptions } from '@posts/api-client';
import type { NetworkId } from '@posts/domain';
import { Input } from '@posts/ui';
import { useTranslations } from 'next-intl';

import type { EditorPostOptions, PublishMode } from '../model/types';
import { isOptionSupported } from '../model/option-support';
import {
  presetFromRecurrence,
  recurrenceFromPreset,
  type RecurrencePresetId,
} from '../model/recurrence';

import { OptionField } from './option-field';

type PostOptionsPanelProps = {
  options: EditorPostOptions;
  networks: NetworkId[];
  onChange: (next: EditorPostOptions) => void;
};

const PRESETS: RecurrencePresetId[] = [
  'once',
  'daily',
  'weekdays',
  'weekends',
  'every_n_days',
  'weekly',
  'every_2_weeks',
  'every_3_weeks',
  'monthly',
  'every_2_months',
  'every_3_months',
  'every_6_months',
  'every_12_months',
];

function toLocalInputValue(iso: string): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function fromLocalInputValue(value: string): string {
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? value : d.toISOString();
}

export function PostOptionsPanel({ options, networks, onChange }: PostOptionsPanelProps) {
  const t = useTranslations('editor.options');
  const preset = presetFromRecurrence(options.recurrence);

  const patchVariant = (patch: Partial<PostVariantOptions>) => {
    onChange({ ...options, variant: { ...options.variant, ...patch } });
  };

  const unsupported = t('unsupportedNetwork');

  return (
    <section
      aria-label={t('label')}
      className="space-y-4 rounded-lg border border-border bg-card/40 p-4"
      data-testid="post-options-panel"
    >
      <h2 className="text-sm font-semibold">{t('title')}</h2>

      <div className="grid gap-3 sm:grid-cols-2">
        <label className="flex flex-col gap-1 text-sm">
          <span>{t('titleField')}</span>
          <Input
            value={options.title}
            onChange={(event) => onChange({ ...options, title: event.target.value.slice(0, 200) })}
            maxLength={200}
            aria-label={t('titleField')}
          />
        </label>

        <label className="flex flex-col gap-1 text-sm">
          <span>{t('publishMode')}</span>
          <select
            className="h-10 rounded-md border border-input bg-background px-3 text-sm"
            value={options.publishMode}
            aria-label={t('publishMode')}
            onChange={(event) =>
              onChange({ ...options, publishMode: event.target.value as PublishMode })
            }
          >
            <option value="schedule">{t('mode.schedule')}</option>
            <option value="now">{t('mode.now')}</option>
          </select>
        </label>

        <label className="flex flex-col gap-1 text-sm sm:col-span-2">
          <span>{t('scheduledAt')}</span>
          <Input
            type="datetime-local"
            value={toLocalInputValue(options.scheduledAt)}
            aria-label={t('scheduledAt')}
            disabled={options.publishMode === 'now'}
            onChange={(event) =>
              onChange({ ...options, scheduledAt: fromLocalInputValue(event.target.value) })
            }
          />
        </label>

        <label className="flex flex-col gap-1 text-sm">
          <span>{t('recurrence')}</span>
          <select
            className="h-10 rounded-md border border-input bg-background px-3 text-sm"
            value={preset}
            aria-label={t('recurrence')}
            data-testid="recurrence-select"
            onChange={(event) => {
              const next = event.target.value as RecurrencePresetId;
              onChange({
                ...options,
                recurrence: recurrenceFromPreset(
                  next,
                  next === 'every_n_days' ? options.recurrence.interval ?? 2 : undefined,
                  options.recurrence.until,
                ),
              });
            }}
          >
            {PRESETS.map((id) => (
              <option key={id} value={id}>
                {t(`recurrencePreset.${id}`)}
              </option>
            ))}
          </select>
        </label>

        {preset === 'every_n_days' ? (
          <label className="flex flex-col gap-1 text-sm">
            <span>{t('everyNDays')}</span>
            <Input
              type="number"
              min={2}
              max={12}
              value={options.recurrence.interval ?? 2}
              aria-label={t('everyNDays')}
              onChange={(event) =>
                onChange({
                  ...options,
                  recurrence: recurrenceFromPreset(
                    'every_n_days',
                    Number(event.target.value) || 2,
                    options.recurrence.until,
                  ),
                })
              }
            />
          </label>
        ) : null}
      </div>

      <div className="grid gap-3 sm:grid-cols-2" data-testid="post-options-flags">
        <OptionField supported={isOptionSupported('signed', networks)} unsupportedHint={unsupported}>
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={Boolean(options.variant.signed)}
              onChange={(event) => patchVariant({ signed: event.target.checked })}
              data-testid="option-signed"
            />
            {t('signed')}
          </label>
        </OptionField>

        <OptionField supported={isOptionSupported('mute', networks)} unsupportedHint={unsupported}>
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={Boolean(options.variant.mute)}
              onChange={(event) => patchVariant({ mute: event.target.checked })}
            />
            {t('mute')}
          </label>
        </OptionField>

        <OptionField
          supported={isOptionSupported('closeComments', networks)}
          unsupportedHint={unsupported}
        >
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={Boolean(options.variant.closeComments)}
              onChange={(event) => patchVariant({ closeComments: event.target.checked })}
            />
            {t('closeComments')}
          </label>
        </OptionField>

        <OptionField supported={isOptionSupported('pin', networks)} unsupportedHint={unsupported}>
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={Boolean(options.variant.pin)}
              onChange={(event) => patchVariant({ pin: event.target.checked })}
            />
            {t('pin')}
          </label>
        </OptionField>

        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={options.removeAfterPublish}
            onChange={(event) =>
              onChange({ ...options, removeAfterPublish: event.target.checked })
            }
          />
          {t('removeAfterPublish')}
        </label>
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <OptionField supported={isOptionSupported('donutDays', networks)} unsupportedHint={unsupported}>
          <label className="flex flex-col gap-1 text-sm">
            <span>{t('donutDays')}</span>
            <Input
              type="number"
              min={0}
              max={7}
              value={options.variant.donutDays ?? 0}
              aria-label={t('donutDays')}
              data-testid="option-donut"
              onChange={(event) =>
                patchVariant({
                  donutDays: Math.min(7, Math.max(0, Number(event.target.value) || 0)),
                })
              }
            />
          </label>
        </OptionField>

        <OptionField
          supported={isOptionSupported('coOwnerIds', networks)}
          unsupportedHint={unsupported}
        >
          <label className="flex flex-col gap-1 text-sm">
            <span>{t('coOwners')}</span>
            <Input
              value={(options.variant.coOwnerIds ?? []).join(',')}
              aria-label={t('coOwners')}
              data-testid="option-coowners"
              placeholder="-123456"
              onChange={(event) =>
                patchVariant({
                  coOwnerIds: event.target.value
                    .split(',')
                    .map((item) => item.trim())
                    .filter(Boolean)
                    .slice(0, 4),
                })
              }
            />
          </label>
        </OptionField>

        <OptionField
          supported={isOptionSupported('firstComment', networks)}
          unsupportedHint={unsupported}
        >
          <label className="flex flex-col gap-1 text-sm sm:col-span-2">
            <span>{t('firstComment')}</span>
            <Input
              value={options.variant.firstComment ?? ''}
              aria-label={t('firstComment')}
              onChange={(event) => patchVariant({ firstComment: event.target.value })}
            />
          </label>
        </OptionField>

        <OptionField
          supported={isOptionSupported('copyrightUrl', networks)}
          unsupportedHint={unsupported}
        >
          <label className="flex flex-col gap-1 text-sm">
            <span>{t('copyrightUrl')}</span>
            <Input
              type="url"
              value={options.variant.copyrightUrl ?? ''}
              aria-label={t('copyrightUrl')}
              onChange={(event) => patchVariant({ copyrightUrl: event.target.value })}
            />
          </label>
        </OptionField>

        <OptionField
          supported={isOptionSupported('primaryAttachmentsMode', networks)}
          unsupportedHint={unsupported}
        >
          <label className="flex flex-col gap-1 text-sm">
            <span>{t('attachmentsMode')}</span>
            <select
              className="h-10 rounded-md border border-input bg-background px-3 text-sm"
              value={options.variant.primaryAttachmentsMode ?? 'grid'}
              aria-label={t('attachmentsMode')}
              onChange={(event) => patchVariant({ primaryAttachmentsMode: event.target.value })}
            >
              <option value="grid">{t('attachments.grid')}</option>
              <option value="carousel">{t('attachments.carousel')}</option>
            </select>
          </label>
        </OptionField>
      </div>

      <OptionField supported={isOptionSupported('poll', networks)} unsupportedHint={unsupported}>
        <PollEditor
          value={options.variant.poll}
          onChange={(poll) => patchVariant({ poll })}
          label={t('poll')}
          questionLabel={t('pollQuestion')}
          answersLabel={t('pollAnswers')}
        />
      </OptionField>

      <OptionField supported={isOptionSupported('repost', networks)} unsupportedHint={unsupported}>
        <div className="grid gap-2 sm:grid-cols-2" data-testid="option-repost">
          <label className="flex flex-col gap-1 text-sm sm:col-span-2">
            <span>{t('repostUrl')}</span>
            <Input
              type="url"
              value={options.repost?.url ?? ''}
              aria-label={t('repostUrl')}
              onChange={(event) => {
                const url = event.target.value.trim();
                onChange({
                  ...options,
                  repost: url
                    ? {
                        url,
                        comment: options.repost?.comment ?? '',
                        network: networks.includes('vk')
                          ? 'vk'
                          : networks.includes('tg')
                            ? 'tg'
                            : 'ok',
                      }
                    : null,
                });
              }}
            />
          </label>
          <label className="flex flex-col gap-1 text-sm sm:col-span-2">
            <span>{t('repostComment')}</span>
            <Input
              value={options.repost?.comment ?? ''}
              aria-label={t('repostComment')}
              disabled={!options.repost?.url}
              onChange={(event) =>
                onChange({
                  ...options,
                  repost: options.repost
                    ? { ...options.repost, comment: event.target.value }
                    : null,
                })
              }
            />
          </label>
        </div>
      </OptionField>

      <OptionField supported={isOptionSupported('buttons', networks)} unsupportedHint={unsupported}>
        <label className="flex flex-col gap-1 text-sm">
          <span>{t('buttons')}</span>
          <Input
            value={(options.variant.buttons ?? [])
              .map((button) => `${button.text ?? ''}|${button.url ?? ''}`)
              .join('; ')}
            aria-label={t('buttons')}
            data-testid="option-buttons"
            placeholder="Купить|https://example.com"
            onChange={(event) => {
              const buttons = event.target.value
                .split(';')
                .map((chunk) => chunk.trim())
                .filter(Boolean)
                .slice(0, 10)
                .map((chunk) => {
                  const [text, url] = chunk.split('|').map((part) => part.trim());
                  return { text: text ?? '', url: url ?? '' };
                });
              patchVariant({ buttons });
            }}
          />
        </label>
      </OptionField>

      <div className="grid gap-3 sm:grid-cols-2">
        <OptionField
          supported={isOptionSupported('deleteAfter', networks)}
          unsupportedHint={unsupported}
        >
          <label className="flex flex-col gap-1 text-sm">
            <span>{t('deleteAfter')}</span>
            <Input
              type="datetime-local"
              value={toLocalInputValue(options.variant.deleteAfter ?? '')}
              aria-label={t('deleteAfter')}
              onChange={(event) =>
                patchVariant({
                  deleteAfter: event.target.value
                    ? fromLocalInputValue(event.target.value)
                    : undefined,
                })
              }
            />
          </label>
        </OptionField>

        <OptionField supported={isOptionSupported('unpinAt', networks)} unsupportedHint={unsupported}>
          <label className="flex flex-col gap-1 text-sm">
            <span>{t('unpinAt')}</span>
            <Input
              type="datetime-local"
              value={toLocalInputValue(options.variant.unpinAt ?? '')}
              aria-label={t('unpinAt')}
              disabled={!options.variant.pin}
              onChange={(event) =>
                patchVariant({
                  unpinAt: event.target.value
                    ? fromLocalInputValue(event.target.value)
                    : undefined,
                })
              }
            />
          </label>
        </OptionField>

        <label className="flex flex-col gap-1 text-sm">
          <span>{t('stopAt')}</span>
          <Input
            type="datetime-local"
            value={toLocalInputValue(options.stopAt ?? '')}
            aria-label={t('stopAt')}
            onChange={(event) =>
              onChange({
                ...options,
                stopAt: event.target.value ? fromLocalInputValue(event.target.value) : null,
              })
            }
          />
        </label>
      </div>
    </section>
  );
}

function PollEditor({
  value,
  onChange,
  label,
  questionLabel,
  answersLabel,
}: {
  value?: PollOptions;
  onChange: (poll: PollOptions | undefined) => void;
  label: string;
  questionLabel: string;
  answersLabel: string;
}) {
  const poll = value ?? { question: '', answers: ['', ''] };
  return (
    <div className="space-y-2 rounded-md border border-dashed border-border p-3" data-testid="option-poll">
      <p className="text-sm font-medium">{label}</p>
      <label className="flex flex-col gap-1 text-sm">
        <span>{questionLabel}</span>
        <Input
          value={poll.question ?? ''}
          aria-label={questionLabel}
          onChange={(event) => {
            const question = event.target.value;
            if (!question && !(poll.answers ?? []).some(Boolean)) {
              onChange(undefined);
              return;
            }
            onChange({ ...poll, question });
          }}
        />
      </label>
      <label className="flex flex-col gap-1 text-sm">
        <span>{answersLabel}</span>
        <Input
          value={(poll.answers ?? []).join(' | ')}
          aria-label={answersLabel}
          placeholder="Вариант 1 | Вариант 2"
          onChange={(event) => {
            const answers = event.target.value
              .split('|')
              .map((item) => item.trim())
              .filter(Boolean);
            if (!poll.question && answers.length === 0) {
              onChange(undefined);
              return;
            }
            onChange({ question: poll.question ?? '', answers: answers.length >= 2 ? answers : ['', ''] });
          }}
        />
      </label>
    </div>
  );
}
