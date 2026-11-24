'use client';

import { AdMarkingSlot } from '@posts/ui';
import { useTranslations } from 'next-intl';

import { hasPlayback, MediaPlayback } from '@/features/media/components/media-playback';
import type { EditorMediaItem } from '@/features/media/model/types';

import { STORY_LINK_TEXTS } from '../model/link-texts';

type StoryPreviewProps = {
  media: EditorMediaItem | null;
  linkText: string;
  channelName?: string;
  adEnabled: boolean;
  erid?: string;
  advertiserName?: string;
  ttlLabel: string;
};

export function StoryPreview({
  media,
  linkText,
  channelName,
  adEnabled,
  erid,
  advertiserName,
  ttlLabel,
}: StoryPreviewProps) {
  const t = useTranslations('stories.preview');
  const cta = STORY_LINK_TEXTS.find((item) => item.key === linkText)?.label;

  return (
    <aside
      className="flex flex-col gap-3 rounded-lg border border-border bg-card/60 p-4"
      data-testid="story-preview"
      aria-label={t('label')}
    >
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-sm font-semibold">{t('title')}</h2>
        <span className="text-xs text-muted-foreground">{ttlLabel}</span>
      </div>
      <div className="mx-auto w-full max-w-[240px]">
        <div className="relative aspect-[9/16] overflow-hidden rounded-[1.5rem] border border-border bg-zinc-900 shadow-inner">
          {media && hasPlayback(media.kind, media.playbackUrl) ? (
            <MediaPlayback
              src={media.playbackUrl}
              poster={media.previewUrl}
              label={media.filename}
              className="h-full w-full object-cover"
            />
          ) : media?.previewUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={media.previewUrl} alt="" className="h-full w-full object-cover" />
          ) : (
            <div className="flex h-full items-center justify-center px-4 text-center text-sm text-zinc-400">
              {t('empty')}
            </div>
          )}
          <div className="pointer-events-none absolute inset-x-0 top-0 bg-gradient-to-b from-black/50 to-transparent p-3">
            <p className="truncate text-xs font-medium text-white">{channelName ?? t('channel')}</p>
          </div>
          {adEnabled ? (
            <div className="pointer-events-none absolute inset-x-0 top-10 px-3">
              <AdMarkingSlot
                enabled
                erid={erid}
                advertiserName={advertiserName}
                className="bg-black/40 text-white"
              />
            </div>
          ) : null}
          {cta ? (
            <div className="pointer-events-none absolute inset-x-0 bottom-4 flex justify-center px-4">
              <span className="rounded-full bg-white/95 px-4 py-1.5 text-xs font-semibold text-zinc-900">
                {cta}
              </span>
            </div>
          ) : null}
        </div>
      </div>
    </aside>
  );
}
