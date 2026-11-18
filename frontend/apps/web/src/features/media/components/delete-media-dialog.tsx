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
import { useTranslations } from 'next-intl';

import type { MediaLibraryItem } from '../model/types';

type DeleteMediaDialogProps = {
  item: MediaLibraryItem | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onConfirm: () => void;
  busy?: boolean;
};

export function DeleteMediaDialog({
  item,
  open,
  onOpenChange,
  onConfirm,
  busy,
}: DeleteMediaDialogProps) {
  const t = useTranslations('media.delete');

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent data-testid="media-delete-dialog">
        <DialogHeader>
          <DialogTitle>{t('title')}</DialogTitle>
          <DialogDescription>
            {t('description', { name: item?.filename ?? item?.id ?? '' })}
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
            {t('cancel')}
          </Button>
          <Button
            type="button"
            variant="destructive"
            disabled={busy}
            onClick={onConfirm}
            data-testid="media-delete-confirm"
          >
            {t('confirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
