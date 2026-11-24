'use client';

import { Button, Input, cn } from '@posts/ui';
import { Plus, Trash2 } from 'lucide-react';
import { useTranslations } from 'next-intl';

import {
  isValidTime,
  toggleWeekday,
  WEEKDAYS,
  type ScheduleWindowValue,
  type TimeInterval,
  type Weekday,
} from '../model/schedule';

export type ScheduleWindowEditorProps = {
  value: ScheduleWindowValue;
  onChange: (next: ScheduleWindowValue) => void;
  disabled?: boolean;
  className?: string;
};

export function ScheduleWindowEditor({
  value,
  onChange,
  disabled,
  className,
}: ScheduleWindowEditorProps) {
  const t = useTranslations('automations.common.schedule');

  const updateInterval = (index: number, patch: Partial<TimeInterval>) => {
    const intervals = value.intervals.map((item, i) => (i === index ? { ...item, ...patch } : item));
    onChange({ ...value, intervals });
  };

  return (
    <div
      className={cn('space-y-4', className)}
      data-testid="schedule-window-editor"
      role="group"
      aria-label={t('label')}
    >
      <div>
        <p className="text-sm font-medium">{t('days')}</p>
        <p className="mt-0.5 text-xs text-muted-foreground">
          {t('timezone', { timezone: value.timezone })}
        </p>
        <div className="mt-2 flex flex-wrap gap-2">
          {WEEKDAYS.map((day) => {
            const selected = value.days.includes(day);
            return (
              <button
                key={day}
                type="button"
                disabled={disabled}
                aria-pressed={selected}
                data-testid={`schedule-day-${day}`}
                className={cn(
                  'rounded-md border px-2.5 py-1 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                  selected
                    ? 'border-primary bg-primary/10 text-primary'
                    : 'border-border text-muted-foreground hover:bg-muted',
                )}
                onClick={() =>
                  onChange({ ...value, days: toggleWeekday(value.days, day as Weekday) })
                }
              >
                {t(`weekdays.${day}`)}
              </button>
            );
          })}
        </div>
      </div>

      <div className="space-y-2">
        <div className="flex items-center justify-between gap-2">
          <p className="text-sm font-medium">{t('intervals')}</p>
          <Button
            type="button"
            size="sm"
            variant="outline"
            disabled={disabled}
            data-testid="schedule-add-interval"
            onClick={() =>
              onChange({
                ...value,
                intervals: [...value.intervals, { start: '09:00', end: '18:00' }],
              })
            }
          >
            <Plus className="h-3.5 w-3.5" aria-hidden />
            {t('addInterval')}
          </Button>
        </div>

        {value.intervals.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t('emptyIntervals')}</p>
        ) : null}

        <ul className="space-y-2">
          {value.intervals.map((interval, index) => {
            const invalid =
              !isValidTime(interval.start) ||
              !isValidTime(interval.end) ||
              interval.start >= interval.end;
            return (
              <li
                key={`${interval.start}-${interval.end}-${index}`}
                className="flex flex-wrap items-center gap-2"
                data-testid={`schedule-interval-${index}`}
              >
                <Input
                  type="time"
                  value={interval.start}
                  disabled={disabled}
                  aria-label={t('start')}
                  aria-invalid={invalid}
                  className="w-32"
                  onChange={(event) => updateInterval(index, { start: event.target.value })}
                />
                <span className="text-sm text-muted-foreground">-</span>
                <Input
                  type="time"
                  value={interval.end}
                  disabled={disabled}
                  aria-label={t('end')}
                  aria-invalid={invalid}
                  className="w-32"
                  onChange={(event) => updateInterval(index, { end: event.target.value })}
                />
                <Button
                  type="button"
                  size="icon"
                  variant="ghost"
                  disabled={disabled}
                  aria-label={t('removeInterval')}
                  onClick={() =>
                    onChange({
                      ...value,
                      intervals: value.intervals.filter((_, i) => i !== index),
                    })
                  }
                >
                  <Trash2 className="h-4 w-4" aria-hidden />
                </Button>
                {invalid ? (
                  <span className="text-xs text-destructive" role="alert">
                    {t('invalidInterval')}
                  </span>
                ) : null}
              </li>
            );
          })}
        </ul>
      </div>
    </div>
  );
}
