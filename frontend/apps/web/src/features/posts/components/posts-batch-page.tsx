'use client';

import { Button, EmptyState, Input, toast } from '@posts/ui';
import Link from 'next/link';
import { useMemo, useState } from 'react';
import { useTranslations } from 'next-intl';

import { batchCreatePosts } from '@/features/editor/api/posts-api';
import { ChannelPicker } from '@/features/editor/components/channel-picker';
import { defaultScheduledAt } from '@/features/editor/model/defaults';
import { useWorkspaceChannels } from '@/features/channels/hooks/use-workspace-channels';
import { MediaPicker } from '@/features/media/components/media-picker';
import type { EditorMediaItem, MediaLibraryItem } from '@/features/media/model/types';
import { useCan } from '@/shared/hooks/use-can';

const BATCH_MAX = 50;

type PostsBatchPageProps = {
  workspaceId: string;
};

function toEditorItem(asset: MediaLibraryItem, asClip: boolean): EditorMediaItem {
  const kind =
    asClip && (asset.kind === 'video' || asset.kind === 'clip')
      ? 'clip'
      : (asset.kind ?? 'image');
  return {
    id: crypto.randomUUID(),
    mediaId: asset.id!,
    kind,
    mime: asset.mime ?? 'application/octet-stream',
    filename: asset.filename ?? asset.id!,
    previewUrl: asset.previewUrl,
    playbackUrl: asset.playbackUrl || undefined,
    width: asset.width,
    height: asset.height,
    sizeBytes: asset.sizeBytes,
  };
}

export function PostsBatchPage({ workspaceId }: PostsBatchPageProps) {
  const t = useTranslations('posts.batchPage');
  const canBulk = useCan('posts.bulk', workspaceId);
  const canSchedule = useCan('posts.schedule', workspaceId);
  const { channels, isLoading } = useWorkspaceChannels(workspaceId);

  const [selectedChannelIds, setSelectedChannelIds] = useState<string[]>([]);
  const [mediaItems, setMediaItems] = useState<EditorMediaItem[]>([]);
  const [scheduledAt, setScheduledAt] = useState(defaultScheduledAt());
  const [stepMinutes, setStepMinutes] = useState(5);
  const [text, setText] = useState('');
  const [asClips, setAsClips] = useState(false);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const hasVk = useMemo(
    () =>
      selectedChannelIds.some((id) => channels.find((c) => c.id === id)?.network === 'vk'),
    [selectedChannelIds, channels],
  );

  if (!canBulk || !canSchedule) {
    return (
      <EmptyState
        title={t('forbiddenTitle')}
        description={t('forbiddenBody')}
        action={
          <Button asChild variant="outline">
            <Link href={`/app/${workspaceId}/posts`}>{t('back')}</Link>
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
      const result = await batchCreatePosts(
        {
          workspaceId,
          channelIds: selectedChannelIds,
          mediaIds: mediaItems.map((item) => item.mediaId),
          scheduledAt,
          stepMinutes,
          timezone: 'Europe/Moscow',
          text: text.trim() || undefined,
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
    <div className="flex flex-col gap-4" data-testid="posts-batch-page">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">{t('title')}</h1>
          <p className="text-sm text-muted-foreground">{t('description', { max: BATCH_MAX })}</p>
        </div>
        <Button asChild variant="outline">
          <Link href={`/app/${workspaceId}/posts`}>{t('back')}</Link>
        </Button>
      </header>

      <ChannelPicker
        channels={channels}
        loading={isLoading}
        selectedIds={selectedChannelIds}
        onChange={setSelectedChannelIds}
      />

      <section className="flex flex-col gap-3" aria-label={t('mediaLabel')}>
        <div className="flex items-center justify-between gap-2">
          <h2 className="text-sm font-semibold">{t('mediaTitle')}</h2>
          <Button
            type="button"
            variant="outline"
            onClick={() => setPickerOpen(true)}
            data-testid="posts-batch-add-media"
          >
            {t('addMedia')}
          </Button>
        </div>
        {mediaItems.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t('mediaEmpty')}</p>
        ) : (
          <ul className="space-y-2" data-testid="posts-batch-media-list">
            {mediaItems.map((item, index) => (
              <li
                key={item.id}
                className="flex items-center justify-between gap-2 rounded-md border border-border px-3 py-2 text-sm"
              >
                <span className="truncate">
                  {index + 1}. {item.filename}
                  {item.kind === 'clip' ? ' · клип' : ''}
                </span>
                <Button
                  type="button"
                  size="sm"
                  variant="ghost"
                  onClick={() =>
                    setMediaItems((current) => current.filter((row) => row.id !== item.id))
                  }
                >
                  {t('remove')}
                </Button>
              </li>
            ))}
          </ul>
        )}
      </section>

      <label className="flex flex-col gap-1.5 text-sm">
        <span className="font-medium">{t('text')}</span>
        <textarea
          className="min-h-[80px] rounded-md border border-input bg-background px-3 py-2 text-sm"
          value={text}
          placeholder={t('textPlaceholder')}
          aria-label={t('text')}
          onChange={(event) => setText(event.target.value)}
        />
      </label>

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
          />
        </label>
        {hasVk ? (
          <label className="flex items-center gap-2 text-sm sm:mt-6">
            <input
              type="checkbox"
              checked={asClips}
              onChange={(event) => {
                const next = event.target.checked;
                setAsClips(next);
                setMediaItems((current) =>
                  current.map((item) =>
                    item.kind === 'video' || item.kind === 'clip'
                      ? { ...item, kind: next ? 'clip' : 'video' }
                      : item,
                  ),
                );
              }}
              data-testid="posts-batch-as-clips"
            />
            {t('asClips')}
          </label>
        ) : null}
      </div>

      <p className="text-sm text-muted-foreground">
        {t('readyHint', {
          channels: selectedChannelIds.length,
          files: mediaItems.length,
        })}
      </p>

      <Button
        type="button"
        disabled={submitting}
        data-testid="posts-batch-submit"
        onClick={() => void submit()}
      >
        {submitting ? t('submitting') : t('submit')}
      </Button>

      <MediaPicker
        workspaceId={workspaceId}
        open={pickerOpen}
        onOpenChange={setPickerOpen}
        onConfirm={(assets) =>
          setMediaItems((current) => [
            ...current,
            ...assets.map((asset) => toEditorItem(asset, asClips)),
          ])
        }
      />
    </div>
  );
}

function toLocalInputValue(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
