'use client';

import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  Input,
} from '@posts/ui';
import { useState } from 'react';
import { useTranslations } from 'next-intl';

type ImportUrlDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onImport: (url: string, filename?: string) => Promise<void>;
};

export function ImportUrlDialog({ open, onOpenChange, onImport }: ImportUrlDialogProps) {
  const t = useTranslations('media.import');
  const [url, setUrl] = useState('');
  const [filename, setFilename] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      await onImport(url.trim(), filename.trim() || undefined);
      setUrl('');
      setFilename('');
      onOpenChange(false);
    } catch {
      setError(t('error'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent data-testid="media-import-url">
        <DialogHeader>
          <DialogTitle>{t('title')}</DialogTitle>
          <DialogDescription>{t('description')}</DialogDescription>
        </DialogHeader>
        <div className="space-y-3">
          <Input
            value={url}
            onChange={(event) => setUrl(event.target.value)}
            placeholder="https://"
            aria-label={t('url')}
          />
          <Input
            value={filename}
            onChange={(event) => setFilename(event.target.value)}
            placeholder={t('filename')}
            aria-label={t('filename')}
          />
          {error ? <p className="text-sm text-destructive">{error}</p> : null}
        </div>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
            {t('cancel')}
          </Button>
          <Button type="button" disabled={!url.trim() || busy} onClick={() => void submit()}>
            {t('submit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
