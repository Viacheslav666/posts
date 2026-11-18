'use client';

import { Button, Input } from '@posts/ui';
import { Plus, Trash2 } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useMemo } from 'react';

import {
  addRecurrenceTime,
  RECURRENCE_TIMES_MAX,
  removeRecurrenceTime,
  validateRecurrenceTimes,
} from '../lib/recurrence-times';

type RecurrenceTimesFieldProps = {
  times: string[];
  onChange: (times: string[]) => void;
  /** Hide for one-shot posts if desired — still allowed by API. */
  disabled?: boolean;
};

export function RecurrenceTimesField({
  times,
  onChange,
  disabled = false,
}: RecurrenceTimesFieldProps) {
  const t = useTranslations('editor.options.times');
  const validation = useMemo(() => validateRecurrenceTimes(times), [times]);
  const list = times.length > 0 ? times : [];

  return (
    <div
      className="space-y-2 sm:col-span-2"
      data-testid="recurrence-times"
      aria-label={t('label')}
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm font-medium">{t('title')}</p>
        <Button
          type="button"
          size="sm"
          variant="outline"
          disabled={disabled || list.length >= RECURRENCE_TIMES_MAX}
          data-testid="add-recurrence-time"
          onClick={() => onChange(addRecurrenceTime(list, suggestNext(list)))}
        >
          <Plus className="mr-1 h-4 w-4" aria-hidden />
          {t('add')}
        </Button>
      </div>
      <p className="text-xs text-muted-foreground">{t('hint')}</p>
      {list.length === 0 ? (
        <p className="text-xs text-muted-foreground">{t('empty')}</p>
      ) : (
        <ul className="space-y-2">
          {list.map((time, index) => (
            <li key={`${time}-${index}`} className="flex items-center gap-2">
              <Input
                type="time"
                value={time}
                aria-label={t('itemLabel', { index: index + 1 })}
                data-testid={`recurrence-time-${index}`}
                disabled={disabled}
                onChange={(event) => {
                  const next = [...list];
                  next[index] = event.target.value;
                  onChange(next);
                }}
              />
              <Button
                type="button"
                size="icon"
                variant="ghost"
                aria-label={t('remove')}
                disabled={disabled}
                onClick={() => onChange(removeRecurrenceTime(list, index))}
              >
                <Trash2 className="h-4 w-4" />
              </Button>
            </li>
          ))}
        </ul>
      )}
      {!validation.ok ? (
        <p className="text-sm text-destructive" role="alert" data-testid="recurrence-times-error">
          {validation.duplicates.length
            ? t('duplicateError', { times: validation.duplicates.join(', ') })
            : validation.overLimit
              ? t('limitError', { max: RECURRENCE_TIMES_MAX })
              : t('invalidError', { times: validation.invalid.join(', ') })}
        </p>
      ) : null}
    </div>
  );
}

function suggestNext(times: string[]): string {
  if (times.length === 0) return '09:00';
  const last = times[times.length - 1]!;
  const [h, m] = last.split(':').map(Number);
  const nextH = Math.min(23, (h ?? 9) + 1);
  return `${String(nextH).padStart(2, '0')}:${String(m ?? 0).padStart(2, '0')}`;
}
