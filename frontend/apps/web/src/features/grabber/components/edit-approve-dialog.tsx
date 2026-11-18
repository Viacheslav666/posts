'use client';

import type { GrabberItem } from '@posts/api-client';
import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@posts/ui';
import { useTranslations } from 'next-intl';
import { useEffect, useState } from 'react';

type EditApproveDialogProps = {
  item: GrabberItem | null;
  open: boolean;
  busy?: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (contentText: string) => void;
};

export function EditApproveDialog({
  item,
  open,
  busy,
  onOpenChange,
  onSubmit,
}: EditApproveDialogProps) {
  const t = useTranslations('grabber.editApprove');
  const [text, setText] = useState(item?.contentText ?? '');

  useEffect(() => {
    setText(item?.contentText ?? '');
  }, [item]);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-xl" data-testid="grabber-edit-approve-dialog">
        <DialogHeader>
          <DialogTitle>{t('title')}</DialogTitle>
          <DialogDescription>{t('description')}</DialogDescription>
        </DialogHeader>
        <label className="space-y-1.5 text-sm">
          <span className="font-medium">{t('label')}</span>
          <textarea
            className="min-h-[180px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
            value={text}
            onChange={(event) => setText(event.target.value)}
            aria-label={t('label')}
            data-testid="grabber-edit-text"
          />
        </label>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
            {t('cancel')}
          </Button>
          <Button
            type="button"
            disabled={busy || !text.trim()}
            onClick={() => onSubmit(text)}
            data-testid="grabber-edit-submit"
          >
            {t('submit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
