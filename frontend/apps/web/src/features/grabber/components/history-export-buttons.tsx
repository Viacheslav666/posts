'use client';

import { Button, toast } from '@posts/ui';
import { useMutation } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';

import { getErrorMessage } from '@/shared/lib/errors';

import { exportIngestItems } from '../api/grabber-api';

type HistoryExportButtonsProps = {
  ruleId: string;
};

function triggerDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.rel = 'noopener';
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

export function HistoryExportButtons({ ruleId }: HistoryExportButtonsProps) {
  const t = useTranslations('grabber.export');

  const mutation = useMutation({
    mutationFn: (format: 'csv' | 'xlsx') => exportIngestItems(ruleId, { format }),
    onSuccess: ({ blob, filename }) => {
      triggerDownload(blob, filename);
      toast({ title: t('success') });
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('errorTitle'),
        description: getErrorMessage(error),
      });
    },
  });

  return (
    <div className="flex flex-wrap gap-2" data-testid="grabber-history-export">
      <Button
        type="button"
        size="sm"
        variant="outline"
        disabled={mutation.isPending}
        onClick={() => mutation.mutate('csv')}
        data-testid="grabber-export-csv"
      >
        {t('csv')}
      </Button>
      <Button
        type="button"
        size="sm"
        variant="outline"
        disabled={mutation.isPending}
        onClick={() => mutation.mutate('xlsx')}
        data-testid="grabber-export-xlsx"
      >
        {t('xlsx')}
      </Button>
    </div>
  );
}
