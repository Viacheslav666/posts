'use client';

import {
  Button,
  EmptyState,
  FileDropzone,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Skeleton,
} from '@posts/ui';
import { useQueryClient } from '@tanstack/react-query';
import { FolderOpen, ShieldOff } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';

import { queryKeys } from '@/shared/api-client';
import { useCan } from '@/shared/hooks/use-can';

import { deleteMedia, importMediaUrl } from '../api/media-api';
import { useMediaLibrary } from '../hooks/use-media-library';
import { useUploadQueue } from '../hooks/use-upload-queue';
import type { MediaKind, MediaLibraryItem } from '../model/types';
import { DeleteMediaDialog } from './delete-media-dialog';
import { ImportUrlDialog } from './import-url-dialog';
import { MediaDetailsPanel } from './media-details-panel';
import { MediaGrid } from './media-grid';
import { MediaQuotaBar } from './media-quota-bar';

type MediaLibraryPageProps = {
  workspaceId: string;
};

export function MediaLibraryPage({ workspaceId }: MediaLibraryPageProps) {
  const t = useTranslations('media');
  const canView = useCan('media.view', workspaceId);
  const canManage = useCan('media.manage', workspaceId);
  const queryClient = useQueryClient();
  const { items, filters, setFilters, quota, folders, isLoading, refetch } = useMediaLibrary(
    workspaceId,
    canView,
  );
  const { items: uploads, enqueue, cancel, retry } = useUploadQueue(workspaceId);
  const [details, setDetails] = useState<MediaLibraryItem | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<MediaLibraryItem | null>(null);
  const [importOpen, setImportOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const invalidate = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.media.all(workspaceId) });
    void refetch();
  }, [queryClient, refetch, workspaceId]);

  useEffect(() => {
    const onPaste = (event: ClipboardEvent) => {
      if (!canManage) return;
      const files = event.clipboardData?.files;
      if (files && files.length > 0) {
        enqueue([...files]);
        window.setTimeout(invalidate, 600);
      }
    };
    window.addEventListener('paste', onPaste);
    return () => window.removeEventListener('paste', onPaste);
  }, [canManage, enqueue, invalidate]);

  useEffect(() => {
    if (uploads.some((item) => item.status === 'ready' || item.status === 'processing')) {
      invalidate();
    }
  }, [uploads, invalidate]);

  if (!canView) {
    return (
      <EmptyState
        icon={<ShieldOff className="h-6 w-6" aria-hidden />}
        title={t('forbiddenTitle')}
        description={t('forbiddenBody')}
      />
    );
  }

  if (isLoading) {
    return (
      <div className="space-y-4" data-testid="media-loading">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  return (
    <div className="mx-auto flex w-full max-w-6xl flex-col gap-6" data-testid="media-library-page">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">{t('title')}</h1>
          <p className="text-sm text-muted-foreground">{t('description')}</p>
        </div>
        {canManage ? (
          <div className="flex flex-wrap gap-2">
            <Button type="button" variant="outline" onClick={() => setImportOpen(true)}>
              {t('importUrl')}
            </Button>
          </div>
        ) : null}
      </header>

      <MediaQuotaBar quota={quota} />

      {canManage ? (
        <FileDropzone
          label={t('dropLabel')}
          hint={t('dropHint')}
          accept="image/jpeg,image/png,image/webp,image/gif,video/mp4,video/quicktime,application/pdf,audio/mpeg"
          onFiles={(files) => {
            enqueue(files);
            window.setTimeout(invalidate, 600);
          }}
        />
      ) : null}

      {uploads.length > 0 ? (
        <section aria-label={t('queue.label')} className="space-y-2" data-testid="media-upload-queue">
          <h2 className="text-sm font-semibold">{t('queue.label')}</h2>
          <ul className="space-y-2">
            {uploads.map((item) => (
              <li
                key={item.localId}
                className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-border px-3 py-2 text-sm"
              >
                <span className="truncate">
                  {item.file.name} — {Math.round(item.progress * 100)}% · {item.status}
                </span>
                <div className="flex gap-2">
                  {item.status === 'uploading' || item.status === 'queued' ? (
                    <Button type="button" size="sm" variant="ghost" onClick={() => cancel(item.localId)}>
                      {t('queue.cancel')}
                    </Button>
                  ) : null}
                  {item.status === 'failed' || item.status === 'cancelled' ? (
                    <Button type="button" size="sm" variant="outline" onClick={() => retry(item.localId)}>
                      {t('queue.retry')}
                    </Button>
                  ) : null}
                </div>
              </li>
            ))}
          </ul>
        </section>
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
          <SelectTrigger className="w-[150px]" aria-label={t('filterKind')}>
            <SelectValue placeholder={t('filterKind')} />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">{t('kinds.all')}</SelectItem>
            <SelectItem value="image">{t('kinds.image')}</SelectItem>
            <SelectItem value="video">{t('kinds.video')}</SelectItem>
            <SelectItem value="doc">{t('kinds.doc')}</SelectItem>
            <SelectItem value="audio">{t('kinds.audio')}</SelectItem>
          </SelectContent>
        </Select>
        <Select
          value={filters.folderId ?? 'all'}
          onValueChange={(value) =>
            setFilters((current) => ({
              ...current,
              folderId: value === 'all' ? undefined : value,
            }))
          }
        >
          <SelectTrigger className="w-[180px]" aria-label={t('filterFolder')}>
            <SelectValue placeholder={t('filterFolder')} />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">{t('folders.all')}</SelectItem>
            {folders
              .filter((folder) => folder.id !== 'folder-root')
              .map((folder) => (
                <SelectItem key={folder.id} value={folder.id}>
                  {folder.name}
                </SelectItem>
              ))}
          </SelectContent>
        </Select>
        <Input
          value={filters.tag ?? ''}
          onChange={(event) =>
            setFilters((current) => ({
              ...current,
              tag: event.target.value || undefined,
            }))
          }
          placeholder={t('filterTag')}
          aria-label={t('filterTag')}
          className="max-w-[140px]"
        />
      </div>

      {items.length === 0 ? (
        <EmptyState
          icon={<FolderOpen className="h-6 w-6" aria-hidden />}
          title={t('emptyTitle')}
          description={t('emptyBody')}
        />
      ) : (
        <MediaGrid items={items} onOpen={setDetails} />
      )}

      <MediaDetailsPanel
        item={details}
        open={Boolean(details)}
        onOpenChange={(open) => {
          if (!open) setDetails(null);
        }}
        canDelete={canManage}
        onDelete={(item) => {
          setDeleteTarget(item);
        }}
      />

      <DeleteMediaDialog
        item={deleteTarget}
        open={Boolean(deleteTarget)}
        onOpenChange={(open) => {
          if (!open) setDeleteTarget(null);
        }}
        busy={deleting}
        onConfirm={() => {
          if (!deleteTarget?.id) return;
          setDeleting(true);
          void deleteMedia(deleteTarget.id)
            .then(() => {
              setDeleteTarget(null);
              setDetails(null);
              invalidate();
            })
            .finally(() => setDeleting(false));
        }}
      />

      <ImportUrlDialog
        open={importOpen}
        onOpenChange={setImportOpen}
        onImport={async (url, filename) => {
          await importMediaUrl({ workspaceId, url, filename });
          invalidate();
        }}
      />
    </div>
  );
}
