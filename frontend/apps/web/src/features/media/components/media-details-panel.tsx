'use client';

import { Badge, Button, Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@posts/ui';
import { useTranslations } from 'next-intl';

import type { MediaLibraryItem } from '../model/types';
import { hasPlayback, MediaPlayback } from './media-playback';

function formatBytes(value?: number): string {
  if (value === undefined) return '—';
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / (1024 * 1024)).toFixed(1)} MB`;
}

function formatDuration(ms?: number): string {
  if (!ms) return '—';
  const seconds = Math.round(ms / 1000);
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}:${String(s).padStart(2, '0')}`;
}

type MediaDetailsPanelProps = {
  item: MediaLibraryItem | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  canDelete?: boolean;
  onDelete?: (item: MediaLibraryItem) => void;
  onInsert?: (item: MediaLibraryItem) => void;
};

export function MediaDetailsPanel({
  item,
  open,
  onOpenChange,
  canDelete,
  onDelete,
  onInsert,
}: MediaDetailsPanelProps) {
  const t = useTranslations('media.details');

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="w-full sm:max-w-md" data-testid="media-details">
        {item ? (
          <>
            <SheetHeader>
              <SheetTitle>{item.filename ?? item.id}</SheetTitle>
              <SheetDescription>{t('subtitle')}</SheetDescription>
            </SheetHeader>
            <div className="mt-4 space-y-4">
              {hasPlayback(item.kind, item.playbackUrl) ? (
                <MediaPlayback
                  src={item.playbackUrl}
                  poster={item.previewUrl}
                  label={item.filename}
                  className="max-h-56 w-full rounded-md bg-muted object-contain"
                />
              ) : item.previewUrl ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img
                  src={item.previewUrl}
                  alt={item.filename ?? ''}
                  className="max-h-56 w-full rounded-md object-contain bg-muted"
                />
              ) : null}
              <dl className="grid grid-cols-2 gap-3 text-sm">
                <div>
                  <dt className="text-muted-foreground">{t('kind')}</dt>
                  <dd>
                    <Badge variant="draft">{item.kind}</Badge>
                  </dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">{t('status')}</dt>
                  <dd>{item.status}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">{t('size')}</dt>
                  <dd>{formatBytes(item.sizeBytes)}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">{t('dimensions')}</dt>
                  <dd>
                    {item.width && item.height ? `${item.width}×${item.height}` : '—'}
                  </dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">{t('duration')}</dt>
                  <dd>{formatDuration(item.durationMs)}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">{t('mime')}</dt>
                  <dd className="truncate">{item.mime}</dd>
                </div>
              </dl>
              {item.tags && item.tags.length > 0 ? (
                <div className="flex flex-wrap gap-1">
                  {item.tags.map((tag) => (
                    <Badge key={tag} variant="scheduled">
                      {tag}
                    </Badge>
                  ))}
                </div>
              ) : null}
              <div>
                <h3 className="mb-2 text-sm font-medium">{t('usedIn')}</h3>
                {item.usedIn && item.usedIn.length > 0 ? (
                  <ul className="space-y-1 text-sm text-muted-foreground">
                    {item.usedIn.map((usage) => (
                      <li key={usage.postId}>{usage.title}</li>
                    ))}
                  </ul>
                ) : (
                  <p className="text-sm text-muted-foreground">{t('unused')}</p>
                )}
              </div>
              <div className="flex flex-wrap gap-2">
                {onInsert ? (
                  <Button type="button" onClick={() => onInsert(item)}>
                    {t('insert')}
                  </Button>
                ) : null}
                {canDelete && onDelete ? (
                  <Button type="button" variant="destructive" onClick={() => onDelete(item)}>
                    {t('delete')}
                  </Button>
                ) : null}
              </div>
            </div>
          </>
        ) : null}
      </SheetContent>
    </Sheet>
  );
}
