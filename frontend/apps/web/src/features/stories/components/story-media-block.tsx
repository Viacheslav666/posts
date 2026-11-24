'use client';

import { Button } from '@posts/ui';
import { ImagePlus, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { useTranslations } from 'next-intl';

import { hasPlayback, MediaPlayback } from '@/features/media/components/media-playback';
import { MediaPicker } from '@/features/media/components/media-picker';
import type { EditorMediaItem, MediaLibraryItem } from '@/features/media/model/types';
import { useCan } from '@/shared/hooks/use-can';

type StoryMediaBlockProps = {
  workspaceId: string;
  media: EditorMediaItem | null;
  onChange: (media: EditorMediaItem | null) => void;
};

function toEditorItem(asset: MediaLibraryItem): EditorMediaItem {
  return {
    id: crypto.randomUUID(),
    mediaId: asset.id!,
    kind: asset.kind ?? 'image',
    mime: asset.mime ?? 'application/octet-stream',
    filename: asset.filename ?? asset.id!,
    previewUrl: asset.previewUrl,
    playbackUrl: asset.playbackUrl || undefined,
    width: asset.width,
    height: asset.height,
    sizeBytes: asset.sizeBytes,
  };
}

export function StoryMediaBlock({ workspaceId, media, onChange }: StoryMediaBlockProps) {
  const t = useTranslations('stories.media');
  const canManage = useCan('media.manage', workspaceId);
  const [pickerOpen, setPickerOpen] = useState(false);

  return (
    <section className="flex flex-col gap-3" data-testid="story-media-block" aria-label={t('label')}>
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-sm font-semibold text-foreground">{t('title')}</h2>
        <p className="text-xs text-muted-foreground">{t('hint')}</p>
      </div>

      {media ? (
        <div className="flex items-start gap-3 rounded-md border border-border bg-muted/30 p-3">
          <div className="relative aspect-[9/16] w-28 overflow-hidden rounded-md bg-background">
            {hasPlayback(media.kind, media.playbackUrl) ? (
              <MediaPlayback
                src={media.playbackUrl}
                poster={media.previewUrl}
                label={media.filename}
                className="h-full w-full object-cover"
              />
            ) : media.previewUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                src={media.previewUrl}
                alt={media.filename}
                className="h-full w-full object-cover"
              />
            ) : (
              <div className="flex h-full items-center justify-center text-xs text-muted-foreground">
                {media.kind === 'video' ? t('video') : t('photo')}
              </div>
            )}
          </div>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium">{media.filename}</p>
            <p className="text-xs text-muted-foreground">
              {media.kind === 'video' ? t('video') : t('photo')}
              {media.width && media.height ? ` · ${media.width}×${media.height}` : ''}
            </p>
            <div className="mt-2 flex flex-wrap gap-2">
              <Button
                type="button"
                size="sm"
                variant="outline"
                onClick={() => setPickerOpen(true)}
                disabled={!canManage}
              >
                {t('replace')}
              </Button>
              <Button
                type="button"
                size="sm"
                variant="ghost"
                onClick={() => onChange(null)}
                aria-label={t('remove')}
              >
                <Trash2 className="h-4 w-4" aria-hidden />
              </Button>
            </div>
          </div>
        </div>
      ) : (
        <Button
          type="button"
          variant="outline"
          className="h-28 justify-center gap-2"
          onClick={() => setPickerOpen(true)}
          disabled={!canManage}
          data-testid="story-media-pick"
        >
          <ImagePlus className="h-4 w-4" aria-hidden />
          {t('pick')}
        </Button>
      )}

      <MediaPicker
        workspaceId={workspaceId}
        open={pickerOpen}
        onOpenChange={setPickerOpen}
        acceptKinds={['image', 'video']}
        preferAspect="9:16"
        multiple={false}
        onConfirm={(items) => {
          const first = items[0];
          onChange(first ? toEditorItem(first) : null);
          setPickerOpen(false);
        }}
      />
    </section>
  );
}
