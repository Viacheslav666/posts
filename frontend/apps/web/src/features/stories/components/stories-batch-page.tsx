'use client';

import type { StoryOptions } from '@posts/api-client';
import { Button, EmptyState, Input, toast } from '@posts/ui';
import Link from 'next/link';
import { useMemo, useState } from 'react';
import { useTranslations } from 'next-intl';

import { useWorkspaceChannels } from '@/features/channels/hooks/use-workspace-channels';
import { defaultScheduledAt } from '@/features/editor/model/defaults';
import { MediaPicker } from '@/features/media/components/media-picker';
import type { EditorMediaItem, MediaLibraryItem } from '@/features/media/model/types';
import { useCan } from '@/shared/hooks/use-can';

import { batchCreateStories } from '../api/stories-api';
import { createDefaultStoryOptions } from '../model/defaults';
import { isVkStoryReady, isStorySupportedNetwork } from '../model/networks';
import { StoryChannelPicker } from './story-channel-picker';

const BATCH_MAX = 50;

type StoriesBatchPageProps = {
  workspaceId: string;
};

function toEditorItem(asset: MediaLibraryItem): EditorMediaItem {
  return {
    id: crypto.randomUUID(),
    mediaId: asset.id!,
    kind: asset.kind ?? 'image',
    mime: asset.mime ?? 'application/octet-stream',
    filename: asset.filename ?? asset.id!,
    previewUrl: asset.previewUrl,
    width: asset.width,
    height: asset.height,
    sizeBytes: asset.sizeBytes,
  };
}

export function StoriesBatchPage({ workspaceId }: StoriesBatchPageProps) {
  const t = useTranslations('stories.batch');
  const canBulk = useCan('posts.bulk', workspaceId);
  const canSchedule = useCan('posts.schedule', workspaceId);
  const { channels, isLoading, refetch } = useWorkspaceChannels(workspaceId);

  const [selectedChannelIds, setSelectedChannelIds] = useState<string[]>([]);
  const [mediaItems, setMediaItems] = useState<EditorMediaItem[]>([]);
  const [scheduledAt, setScheduledAt] = useState(defaultScheduledAt());
  const [stepMinutes, setStepMinutes] = useState(5);
  const [ttl, setTtl] = useState<NonNullable<StoryOptions['ttl']>>('ttl_24h');
  const [pickerOpen, setPickerOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const readyChannels = useMemo(
    () =>
      channels.filter(
        (channel) =>
          isStorySupportedNetwork(channel.network) &&
          isVkStoryReady(channel.network, channel.storyConnectionStatus),
      ),
    [channels],
  );

  if (!canBulk || !canSchedule) {
    return (
      <EmptyState
        title={t('forbiddenTitle')}
        description={t('forbiddenBody')}
        action={
          <Button asChild variant="outline">
            <Link href={`/app/${workspaceId}/stories`}>{t('back')}</Link>
          </Button>
        }
      />
    );
  }

  const submit = async () => {
    if (selectedChannelIds.length === 0) {
      toast({ variant: 'destructive', title: t('needChannels') });
      return;
    }
    if (mediaItems.length === 0) {
      toast({ variant: 'destructive', title: t('needMedia') });
      return;
    }
    if (mediaItems.length > BATCH_MAX) {
      toast({ variant: 'destructive', title: t('limit', { max: BATCH_MAX }) });
      return;
    }

    setSubmitting(true);
    try {
      const story: StoryOptions = {
        ...createDefaultStoryOptions(),
        ttl,
      };
      const result = await batchCreateStories(
        {
          workspaceId,
          channelIds: selectedChannelIds,
          mediaIds: mediaItems.map((item) => item.mediaId),
          scheduledAt,
          stepMinutes,
          timezone: 'Europe/Moscow',
          story,
        },
        crypto.randomUUID(),
      );
      toast({
        title: t('successTitle'),
        description: t('successBody', {
          created: result.created ?? 0,
          failed: result.failures?.length ?? 0,
        }),
      });
      setMediaItems([]);
    } catch (error) {
      toast({
        variant: 'destructive',
        title: t('errorTitle'),
        description: error instanceof Error ? error.message : undefined,
      });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex flex-col gap-4" data-testid="stories-batch-page">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">{t('title')}</h1>
          <p className="text-sm text-muted-foreground">{t('description', { max: BATCH_MAX })}</p>
        </div>
        <Button asChild variant="outline">
          <Link href={`/app/${workspaceId}/stories`}>{t('back')}</Link>
        </Button>
      </header>

      <StoryChannelPicker
        workspaceId={workspaceId}
        channels={channels}
        loading={isLoading}
        selectedIds={selectedChannelIds}
        onChange={setSelectedChannelIds}
        onConnected={() => void refetch()}
      />

      <section className="flex flex-col gap-3" aria-label={t('mediaLabel')}>
        <div className="flex items-center justify-between gap-2">
          <h2 className="text-sm font-semibold">{t('mediaTitle')}</h2>
          <Button type="button" variant="outline" onClick={() => setPickerOpen(true)}>
            {t('addMedia')}
          </Button>
        </div>
        {mediaItems.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t('mediaEmpty')}</p>
        ) : (
          <ul className="space-y-2">
            {mediaItems.map((item, index) => (
              <li
                key={item.id}
                className="flex items-center justify-between gap-2 rounded-md border border-border px-3 py-2 text-sm"
              >
                <span className="truncate">
                  {index + 1}. {item.filename}
                </span>
                <Button
                  type="button"
                  size="sm"
                  variant="ghost"
                  onClick={() => setMediaItems((current) => current.filter((row) => row.id !== item.id))}
                >
                  {t('remove')}
                </Button>
              </li>
            ))}
          </ul>
        )}
      </section>

      <div className="grid gap-3 sm:grid-cols-3">
        <label className="flex flex-col gap-1.5 text-sm">
          <span className="font-medium">{t('scheduledAt')}</span>
          <Input
            type="datetime-local"
            value={toLocalInputValue(scheduledAt)}
            onChange={(event) =>
              setScheduledAt(
                event.target.value ? new Date(event.target.value).toISOString() : scheduledAt,
              )
            }
          />
        </label>
        <label className="flex flex-col gap-1.5 text-sm">
          <span className="font-medium">{t('stepMinutes')}</span>
          <Input
            type="number"
            min={0}
            value={stepMinutes}
            onChange={(event) => setStepMinutes(Number(event.target.value) || 0)}
            data-testid="story-batch-step"
          />
        </label>
        <label className="flex flex-col gap-1.5 text-sm">
          <span className="font-medium">{t('ttl')}</span>
          <select
            className="h-10 rounded-md border border-input bg-background px-3 text-sm"
            value={ttl}
            onChange={(event) => setTtl(event.target.value as typeof ttl)}
          >
            <option value="ttl_1h">1 час</option>
            <option value="ttl_12h">12 часов</option>
            <option value="ttl_24h">24 часа</option>
            <option value="ttl_48h">48 часов</option>
          </select>
        </label>
      </div>

      <p className="text-xs text-muted-foreground">
        {t('readyHint', { channels: readyChannels.length, files: mediaItems.length })}
      </p>

      <Button
        type="button"
        disabled={submitting}
        onClick={() => void submit()}
        data-testid="story-batch-submit"
      >
        {submitting ? t('submitting') : t('submit')}
      </Button>

      <MediaPicker
        workspaceId={workspaceId}
        open={pickerOpen}
        onOpenChange={setPickerOpen}
        acceptKinds={['image', 'video']}
        preferAspect="9:16"
        multiple
        onConfirm={(items) => {
          const next = [...mediaItems, ...items.map(toEditorItem)].slice(0, BATCH_MAX);
          setMediaItems(next);
          setPickerOpen(false);
        }}
      />
    </div>
  );
}

function toLocalInputValue(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '';
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
