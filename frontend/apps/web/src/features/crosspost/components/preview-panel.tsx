'use client';

import type { Channel, CrosspostPreviewResponse } from '@posts/api-client';
import { Button, Input, Skeleton, Tabs, TabsContent, TabsList, TabsTrigger, toast } from '@posts/ui';
import { useMutation } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import { useState } from 'react';

import { ReasonChips } from '@/features/automations-common';
import { getErrorMessage } from '@/shared/lib/errors';

import { previewCrosspostRule } from '../api/crosspost-api';

type PreviewPanelProps = {
  ruleId: string;
  targets: Channel[];
};

export function PreviewPanel({ ruleId, targets }: PreviewPanelProps) {
  const t = useTranslations('automations.crosspost.preview');
  const [sourcePostUrl, setSourcePostUrl] = useState('');
  const [sourceRemoteId, setSourceRemoteId] = useState('');
  const [result, setResult] = useState<CrosspostPreviewResponse | null>(null);

  const previewMutation = useMutation({
    mutationFn: () =>
      previewCrosspostRule(ruleId, {
        sourcePostUrl: sourcePostUrl.trim() || undefined,
        sourceRemoteId: sourceRemoteId.trim() || undefined,
      }),
    onSuccess: (data) => {
      setResult(data);
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

  const firstTargetId = result?.targets?.[0]?.targetChannelId ?? targets[0]?.id ?? 'none';

  return (
    <div className="space-y-4" data-testid="crosspost-preview-panel">
      <div className="grid gap-3 sm:grid-cols-2">
        <div className="space-y-1.5">
          <label htmlFor="crosspost-preview-url" className="text-sm font-medium">
            {t('urlLabel')}
          </label>
          <Input
            id="crosspost-preview-url"
            value={sourcePostUrl}
            onChange={(event) => setSourcePostUrl(event.target.value)}
            placeholder={t('urlPlaceholder')}
            data-testid="crosspost-preview-url"
          />
        </div>
        <div className="space-y-1.5">
          <label htmlFor="crosspost-preview-remote" className="text-sm font-medium">
            {t('remoteIdLabel')}
          </label>
          <Input
            id="crosspost-preview-remote"
            value={sourceRemoteId}
            onChange={(event) => setSourceRemoteId(event.target.value)}
            placeholder={t('remoteIdPlaceholder')}
            data-testid="crosspost-preview-remote"
          />
        </div>
      </div>

      <Button
        type="button"
        onClick={() => previewMutation.mutate()}
        disabled={
          previewMutation.isPending || (!sourcePostUrl.trim() && !sourceRemoteId.trim())
        }
        data-testid="crosspost-preview-submit"
      >
        {previewMutation.isPending ? t('loading') : t('submit')}
      </Button>

      {previewMutation.isPending ? <Skeleton className="h-32 w-full" /> : null}

      {result ? (
        <div className="space-y-3" data-testid="crosspost-preview-result">
          {(result.filterReasons?.length ?? 0) > 0 ? (
            <div className="space-y-1">
              <p className="text-sm font-medium">{t('filterReasons')}</p>
              <ReasonChips
                codes={result.filterReasons ?? []}
                namespace="automations.crosspost.reasons"
              />
            </div>
          ) : null}

          <Tabs defaultValue={firstTargetId}>
            <TabsList aria-label={t('targetsLabel')}>
              {(result.targets ?? []).map((target) => {
                const channel = targets.find((c) => c.id === target.targetChannelId);
                return (
                  <TabsTrigger
                    key={target.targetChannelId}
                    value={target.targetChannelId ?? 'unknown'}
                    data-testid={`crosspost-preview-tab-${target.targetChannelId}`}
                  >
                    {channel?.title ?? target.network ?? target.targetChannelId}
                  </TabsTrigger>
                );
              })}
            </TabsList>
            {(result.targets ?? []).map((target) => {
              const variant = (target.variant ?? {}) as Record<string, unknown>;
              const text =
                typeof variant.text === 'string' ? variant.text : t('noText');
              return (
                <TabsContent
                  key={target.targetChannelId}
                  value={target.targetChannelId ?? 'unknown'}
                  className="mt-3 space-y-2"
                >
                  <p className="text-sm text-muted-foreground">
                    {t('targetStatus', { status: target.status ?? '—' })}
                  </p>
                  {target.reason ? (
                    <ReasonChips
                      codes={[target.reason]}
                      namespace="automations.crosspost.reasons"
                    />
                  ) : null}
                  <pre className="whitespace-pre-wrap rounded-md border border-border bg-muted/30 p-3 text-sm">
                    {text}
                  </pre>
                </TabsContent>
              );
            })}
          </Tabs>
        </div>
      ) : null}
    </div>
  );
}
