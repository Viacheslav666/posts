'use client';

import { Button, Input } from '@posts/ui';
import { Copy, Pause, Play, Trash2, CalendarClock } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useState } from 'react';

import type { BulkAction } from '../model/bulk';

type BulkActionsBarProps = {
  selectedCount: number;
  canBulk: boolean;
  canDelete: boolean;
  busy?: boolean;
  onAction: (action: BulkAction, scheduledAt?: string) => void;
  onClear: () => void;
};

export function BulkActionsBar({
  selectedCount,
  canBulk,
  canDelete,
  busy,
  onAction,
  onClear,
}: BulkActionsBarProps) {
  const t = useTranslations('posts.bulk');
  const [rescheduleAt, setRescheduleAt] = useState('');

  if (selectedCount === 0) return null;

  return (
    <div
      className="sticky bottom-3 z-10 flex flex-wrap items-center gap-2 rounded-lg border border-border bg-card/95 p-3 shadow-md backdrop-blur"
      role="toolbar"
      aria-label={t('toolbarLabel')}
      data-testid="posts-bulk-bar"
    >
      <span className="text-sm font-medium">{t('selected', { count: selectedCount })}</span>
      <div className="ml-auto flex flex-wrap items-center gap-2">
        {canBulk ? (
          <>
            <Button
              type="button"
              size="sm"
              variant="secondary"
              disabled={busy}
              onClick={() => onAction('pause')}
              data-testid="bulk-pause"
            >
              <Pause className="h-4 w-4" aria-hidden />
              {t('pause')}
            </Button>
            <Button
              type="button"
              size="sm"
              variant="secondary"
              disabled={busy}
              onClick={() => onAction('resume')}
              data-testid="bulk-resume"
            >
              <Play className="h-4 w-4" aria-hidden />
              {t('resume')}
            </Button>
            <Button
              type="button"
              size="sm"
              variant="secondary"
              disabled={busy}
              onClick={() => onAction('duplicate')}
              data-testid="bulk-duplicate"
            >
              <Copy className="h-4 w-4" aria-hidden />
              {t('duplicate')}
            </Button>
            <div className="flex items-center gap-1">
              <Input
                type="datetime-local"
                value={rescheduleAt}
                onChange={(e) => setRescheduleAt(e.target.value)}
                aria-label={t('rescheduleAt')}
                className="h-8 w-[11.5rem]"
                data-testid="bulk-reschedule-input"
              />
              <Button
                type="button"
                size="sm"
                variant="secondary"
                disabled={busy || !rescheduleAt}
                onClick={() => {
                  const iso = new Date(rescheduleAt).toISOString();
                  onAction('reschedule', iso);
                }}
                data-testid="bulk-reschedule"
              >
                <CalendarClock className="h-4 w-4" aria-hidden />
                {t('reschedule')}
              </Button>
            </div>
          </>
        ) : null}
        {canDelete || canBulk ? (
          <Button
            type="button"
            size="sm"
            variant="destructive"
            disabled={busy}
            onClick={() => onAction('delete')}
            data-testid="bulk-delete"
          >
            <Trash2 className="h-4 w-4" aria-hidden />
            {t('delete')}
          </Button>
        ) : null}
        <Button type="button" size="sm" variant="ghost" onClick={onClear} disabled={busy}>
          {t('clear')}
        </Button>
      </div>
    </div>
  );
}
