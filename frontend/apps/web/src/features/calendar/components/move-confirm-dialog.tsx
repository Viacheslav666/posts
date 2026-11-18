'use client';

import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@posts/ui';
import { format } from 'date-fns';
import { ru } from 'date-fns/locale';
import { useTranslations } from 'next-intl';

import type { MoveConfirmChoice, PendingMove } from '../model/dnd';

type MoveConfirmDialogProps = {
  pending: PendingMove | null;
  open: boolean;
  busy?: boolean;
  onChoice: (choice: MoveConfirmChoice) => void;
};

function formatWhen(iso?: string): string {
  if (!iso) return '—';
  try {
    return format(new Date(iso), 'd MMM yyyy, HH:mm', { locale: ru });
  } catch {
    return iso;
  }
}

export function MoveConfirmDialog({
  pending,
  open,
  busy = false,
  onChoice,
}: MoveConfirmDialogProps) {
  const t = useTranslations('calendar');

  return (
    <Dialog
      open={open}
      onOpenChange={(next: boolean) => {
        if (!next && !busy) onChoice('cancel');
      }}
    >
      <DialogContent data-testid="calendar-move-dialog">
        <DialogHeader>
          <DialogTitle>{t('move.title')}</DialogTitle>
          <DialogDescription>
            {pending
              ? t('move.description', {
                  title: pending.title ?? t('untitled'),
                  from: formatWhen(pending.fromScheduledAt),
                  to: formatWhen(pending.toScheduledAt),
                })
              : null}
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          {pending?.recurrence ? (
            <>
              <Button
                type="button"
                variant="outline"
                disabled={busy}
                onClick={() => onChoice('cancel')}
              >
                {t('move.cancel')}
              </Button>
              <Button
                type="button"
                variant="secondary"
                disabled={busy}
                onClick={() => onChoice('this_only')}
              >
                {t('move.thisOnly')}
              </Button>
              <Button
                type="button"
                disabled={busy}
                onClick={() => onChoice('all_following')}
              >
                {t('move.allFollowing')}
              </Button>
            </>
          ) : (
            <>
              <Button
                type="button"
                variant="outline"
                disabled={busy}
                onClick={() => onChoice('cancel')}
              >
                {t('move.cancel')}
              </Button>
              <Button
                type="button"
                disabled={busy}
                onClick={() => onChoice('this_only')}
                data-testid="calendar-move-confirm"
              >
                {t('move.confirm')}
              </Button>
            </>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
