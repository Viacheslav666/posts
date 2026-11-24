'use client';

import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  FileDropzone,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@posts/ui';
import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useMemo, useState } from 'react';
import { useTranslations } from 'next-intl';

import { queryKeys } from '@/shared/api-client';
import { useCan } from '@/shared/hooks/use-can';

import { useMediaLibrary } from '../hooks/use-media-library';
import { useUploadQueue } from '../hooks/use-upload-queue';
import { UPLOAD_ACCEPT } from '../lib/upload-mime';
import type { MediaKind, MediaLibraryItem } from '../model/types';
import { MediaGrid } from './media-grid';
import { MediaQuotaBar } from './media-quota-bar';

export type MediaPickerProps = {
  workspaceId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Restrict selectable kinds (e.g. image-only, or 9:16 stories). */
  acceptKinds?: MediaKind[];
  /** Prefer vertical assets (stories / grabber). */
  preferAspect?: '9:16' | '1:1' | 'any';
  multiple?: boolean;
  onConfirm: (items: MediaLibraryItem[]) => void;
};

/**
 * Reusable media picker for post editor, deferred stories (9:16), and grabber.
 */
export function MediaPicker({
  workspaceId,
  open,
  onOpenChange,
  acceptKinds,
  preferAspect = 'any',
  multiple = true,
  onConfirm,
}: MediaPickerProps) {
  const t = useTranslations('media.picker');
  const tQueue = useTranslations('media.queue');
  const canManage = useCan('media.manage', workspaceId);
  const canView = useCan('media.view', workspaceId);
  const queryClient = useQueryClient();
  const { items, filters, setFilters, quota, isLoading, refetch } = useMediaLibrary(
    workspaceId,
    open && canView,
  );
  const { items: uploads, enqueue, cancel, retry } = useUploadQueue(workspaceId);
  const [selected, setSelected] = useState<Map<string, MediaLibraryItem>>(new Map());

  const filtered = useMemo(() => {
    let list = items;
    if (acceptKinds?.length) {
      list = list.filter((item) => item.kind && acceptKinds.includes(item.kind));
    }
    if (preferAspect === '9:16') {
      list = [...list].sort((a, b) => {
        const score = (item: MediaLibraryItem) => {
          if (!item.width || !item.height) return 0;
          const ratio = item.width / item.height;
          return Math.abs(ratio - 9 / 16);
        };
        return score(a) - score(b);
      });
    }
    return list.filter((item) => item.status === 'ready');
  }, [items, acceptKinds, preferAspect]);

  const toggle = useCallback(
    (item: MediaLibraryItem) => {
      if (!item.id) return;
      setSelected((current) => {
        const next = new Map(current);
        if (next.has(item.id!)) {
          next.delete(item.id!);
        } else {
          if (!multiple) next.clear();
          next.set(item.id!, item);
        }
        return next;
      });
    },
    [multiple],
  );

  const handleConfirm = () => {
    onConfirm([...selected.values()]);
    setSelected(new Map());
    onOpenChange(false);
  };

  const onFiles = async (files: File[]) => {
    enqueue(files);
    // Give multipart a moment then refresh library.
    window.setTimeout(() => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.media.all(workspaceId) });
      void refetch();
    }, 800);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] max-w-4xl overflow-y-auto" data-testid="media-picker">
        <DialogHeader>
          <DialogTitle>{t('title')}</DialogTitle>
          <DialogDescription>{t('description')}</DialogDescription>
        </DialogHeader>

        {!canView ? (
          <p className="text-sm text-muted-foreground">{t('forbidden')}</p>
        ) : (
          <div className="space-y-4">
            <MediaQuotaBar quota={quota} />
            {canManage ? (
              <FileDropzone
                label={t('dropLabel')}
                hint={t('dropHint')}
                accept={UPLOAD_ACCEPT}
                onFiles={onFiles}
              />
            ) : null}
            {uploads.length > 0 ? (
              <ul className="space-y-1 text-xs" data-testid="media-picker-uploads">
                {uploads.slice(0, 5).map((item) => (
                  <li key={item.localId} className="flex flex-wrap items-center justify-between gap-2">
                    <span className="min-w-0 flex-1 truncate">
                      {item.file.name} - {Math.round(item.progress * 100)}% ({item.status})
                    </span>
                    {item.status === 'failed' && item.error ? (
                      <span className="text-destructive" role="alert" data-testid={`upload-error-${item.localId}`}>
                        {item.error === 'unsupported_format'
                          ? tQueue('unsupported_format')
                          : item.error === 'upload_timeout'
                            ? tQueue('upload_timeout')
                            : item.error === 'etag_unavailable'
                              ? tQueue('etag_unavailable')
                              : item.error === 'upload_network'
                                ? tQueue('upload_network')
                                : item.error}
                      </span>
                    ) : null}
                    {item.status === 'uploading' || item.status === 'queued' ? (
                      <Button type="button" size="sm" variant="ghost" onClick={() => cancel(item.localId)}>
                        {t('cancel')}
                      </Button>
                    ) : null}
                    {item.status === 'failed' || item.status === 'cancelled' ? (
                      <Button type="button" size="sm" variant="outline" onClick={() => retry(item.localId)}>
                        {tQueue('retry')}
                      </Button>
                    ) : null}
                  </li>
                ))}
              </ul>
            ) : null}
            <div className="flex flex-wrap gap-2">
              <Input
                value={filters.q}
                onChange={(event) => setFilters((current) => ({ ...current, q: event.target.value }))}
                placeholder={t('search')}
                aria-label={t('search')}
                className="max-w-xs"
              />
              <Select
                value={filters.kind ?? 'all'}
                onValueChange={(value) =>
                  setFilters((current) => ({
                    ...current,
                    kind: value === 'all' ? undefined : (value as MediaKind),
                  }))
                }
              >
                <SelectTrigger className="w-[140px]" aria-label={t('filterKind')}>
                  <SelectValue placeholder={t('filterKind')} />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">{t('kinds.all')}</SelectItem>
                  <SelectItem value="image">{t('kinds.image')}</SelectItem>
                  <SelectItem value="video">{t('kinds.video')}</SelectItem>
                  <SelectItem value="clip">{t('kinds.clip')}</SelectItem>
                  <SelectItem value="doc">{t('kinds.doc')}</SelectItem>
                  <SelectItem value="audio">{t('kinds.audio')}</SelectItem>
                </SelectContent>
              </Select>
            </div>
            {isLoading ? (
              <p className="text-sm text-muted-foreground">{t('loading')}</p>
            ) : (
              <MediaGrid
                items={filtered}
                selectable
                selectedIds={new Set(selected.keys())}
                onSelect={toggle}
              />
            )}
          </div>
        )}

        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
            {t('close')}
          </Button>
          <Button
            type="button"
            disabled={selected.size === 0}
            onClick={handleConfirm}
            data-testid="media-picker-confirm"
          >
            {t('confirm', { count: selected.size })}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
