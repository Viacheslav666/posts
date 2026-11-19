'use client';

import type { Channel, SuggestSettings, SuggestSettingsCopyResult } from '@posts/api-client';
import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  toast,
} from '@posts/ui';
import { useMutation } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { useTranslations } from 'next-intl';

import { getErrorMessage } from '@/shared/lib/errors';

import { copySuggestSettings } from '../api/suggest-api';

type CopySettingsDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  settings: SuggestSettings;
  channels: Channel[];
  onCopied?: () => void;
};

export function CopySettingsDialog({
  open,
  onOpenChange,
  settings,
  channels,
  onCopied,
}: CopySettingsDialogProps) {
  const t = useTranslations('automations.suggest.copy');
  const [selected, setSelected] = useState<string[]>([]);
  const [report, setReport] = useState<SuggestSettingsCopyResult | null>(null);

  const targets = useMemo(
    () =>
      channels.filter(
        (channel) =>
          channel.network === 'vk' &&
          channel.status === 'active' &&
          channel.id &&
          channel.id !== settings.channelId,
      ),
    [channels, settings.channelId],
  );

  const copyMutation = useMutation({
    mutationFn: () =>
      copySuggestSettings(settings.id!, {
        channelIds: selected,
      }),
    onSuccess: (result) => {
      setReport(result);
      onCopied?.();
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
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) {
          setReport(null);
          setSelected([]);
        }
        onOpenChange(next);
      }}
    >
      <DialogContent data-testid="suggest-copy-dialog">
        <DialogHeader>
          <DialogTitle>{t('title')}</DialogTitle>
          <DialogDescription>{t('description')}</DialogDescription>
        </DialogHeader>

        {report ? (
          <ul className="space-y-2" data-testid="suggest-copy-report">
            {(report.results ?? []).map((row) => (
              <li
                key={row.channelId}
                className="rounded-md border border-border px-3 py-2 text-sm"
                data-testid={`suggest-copy-result-${row.channelId}`}
              >
                {row.ok
                  ? t('ok', { channelId: row.channelId ?? '' })
                  : t('fail', {
                      channelId: row.channelId ?? '',
                      error: row.error ?? 'error',
                    })}
              </li>
            ))}
          </ul>
        ) : (
          <ul className="max-h-64 space-y-1 overflow-y-auto" data-testid="suggest-copy-channels">
            {targets.length === 0 ? (
              <li className="text-sm text-muted-foreground">{t('empty')}</li>
            ) : (
              targets.map((channel) => {
                const checked = selected.includes(channel.id!);
                return (
                  <li key={channel.id}>
                    <label className="flex items-center gap-2 rounded-md px-2 py-1.5 text-sm hover:bg-muted/40">
                      <input
                        type="checkbox"
                        checked={checked}
                        onChange={() =>
                          setSelected((prev) =>
                            checked
                              ? prev.filter((id) => id !== channel.id)
                              : [...prev, channel.id!],
                          )
                        }
                        data-testid={`suggest-copy-channel-${channel.id}`}
                      />
                      <span>{channel.title ?? channel.username ?? channel.id}</span>
                    </label>
                  </li>
                );
              })
            )}
          </ul>
        )}

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('close')}
          </Button>
          {!report ? (
            <Button
              disabled={selected.length === 0 || copyMutation.isPending}
              onClick={() => copyMutation.mutate()}
              data-testid="suggest-copy-submit"
            >
              {t('submit')}
            </Button>
          ) : null}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
