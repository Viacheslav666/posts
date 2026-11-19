'use client';

import type { SuggestItem } from '@posts/api-client';
import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@posts/ui';
import { useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';

type ApproveEditDialogProps = {
  item: SuggestItem | null;
  open: boolean;
  busy?: boolean;
  warnFallbackNewPost?: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (message: string) => void;
};

export function ApproveEditDialog({
  item,
  open,
  busy,
  warnFallbackNewPost,
  onOpenChange,
  onSubmit,
}: ApproveEditDialogProps) {
  const t = useTranslations('automations.suggest.queue');
  const [message, setMessage] = useState('');

  useEffect(() => {
    if (open && item) {
      setMessage(item.text ?? '');
    }
  }, [open, item]);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-xl" data-testid="suggest-approve-edit-dialog">
        <DialogHeader>
          <DialogTitle>{t('editTitle')}</DialogTitle>
          <DialogDescription>{t('editDescription')}</DialogDescription>
        </DialogHeader>
        {warnFallbackNewPost ? (
          <p
            className="rounded-md border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-sm"
            data-testid="suggest-fallback-new-post-warn"
            role="status"
          >
            {t('fallbackNewPostWarn')}
          </p>
        ) : null}
        <textarea
          className="min-h-[180px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
          value={message}
          onChange={(event) => setMessage(event.target.value)}
          aria-label={t('editMessage')}
          data-testid="suggest-approve-edit-text"
        />
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={busy}>
            {t('cancel')}
          </Button>
          <Button
            disabled={busy || !message.trim()}
            onClick={() => onSubmit(message)}
            data-testid="suggest-approve-edit-submit"
          >
            {t('approve')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
