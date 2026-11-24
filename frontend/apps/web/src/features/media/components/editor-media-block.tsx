'use client';

import { Button, FileDropzone } from '@posts/ui';
import { GripVertical, Trash2 } from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useTranslations } from 'next-intl';
import type { NetworkId } from '@posts/domain';

import { useNetworkCapabilities } from '@/features/editor/hooks/use-network-capabilities';
import type { PublicationKind } from '@/features/editor/model/types';
import { useCan } from '@/shared/hooks/use-can';

import { useUploadQueue } from '../hooks/use-upload-queue';
import { kindFromMime } from '../lib/kind-from-mime';
import { resolveUploadMime, UPLOAD_ACCEPT } from '../lib/upload-mime';
import { validateMediaAgainstCapabilities } from '../lib/media-limits';
import type { CropPreset, EditorMediaItem, MediaLibraryItem } from '../model/types';
import { ImageCropper } from './image-cropper';
import { MediaPicker } from './media-picker';
import { MediaPreview } from './media-preview';

type EditorMediaBlockProps = {
  workspaceId: string;
  items: EditorMediaItem[];
  selectedNetworks: NetworkId[];
  onChange: (items: EditorMediaItem[]) => void;
  publicationKind?: PublicationKind;
};

function toEditorItem(asset: MediaLibraryItem, asClip = false): EditorMediaItem {
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

export function EditorMediaBlock({
  workspaceId,
  items,
  selectedNetworks,
  onChange,
  publicationKind = 'post',
}: EditorMediaBlockProps) {
  const t = useTranslations('media.editor');
  const tQueue = useTranslations('media.queue');
  const tStatus = useTranslations('media.status');
  const canManage = useCan('media.manage', workspaceId);
  const capabilitiesQuery = useNetworkCapabilities();
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;
  const itemsRef = useRef(items);
  itemsRef.current = items;
  const attachedLocalIds = useRef(new Set<string>());

  const { enqueue, items: uploads, retry, cancel } = useUploadQueue(workspaceId, {
    preferredKind: publicationKind === 'clip' ? 'clip' : undefined,
    onReady: (upload) => {
      if (!upload.mediaId || attachedLocalIds.current.has(upload.localId)) return;
      attachedLocalIds.current.add(upload.localId);
      const mime = resolveUploadMime(upload.file);
      const kind =
        publicationKind === 'clip' && mime.startsWith('video/')
          ? 'clip'
          : kindFromMime(mime);
      const next: EditorMediaItem = {
        id: crypto.randomUUID(),
        mediaId: upload.mediaId,
        kind,
        mime,
        filename: upload.file.name,
        sizeBytes: upload.file.size,
        previewUrl: mime.startsWith('image/') ? URL.createObjectURL(upload.file) : undefined,
      };
      onChangeRef.current([...itemsRef.current, next]);
    },
  });
  const [pickerOpen, setPickerOpen] = useState(false);
  const [cropId, setCropId] = useState<string | null>(null);
  const [dragIndex, setDragIndex] = useState<number | null>(null);

  const issues = useMemo(
    () => validateMediaAgainstCapabilities(items, capabilitiesQuery.data, selectedNetworks),
    [items, capabilitiesQuery.data, selectedNetworks],
  );

  const appendAssets = useCallback(
    (assets: MediaLibraryItem[]) => {
      onChange([...items, ...assets.map((asset) => toEditorItem(asset, publicationKind === 'clip'))]);
    },
    [items, onChange, publicationKind],
  );

  const removeAt = (index: number) => {
    onChange(items.filter((_, i) => i !== index));
  };

  const reorder = (from: number, to: number) => {
    if (from === to || from < 0 || to < 0 || from >= items.length || to >= items.length) return;
    const next = [...items];
    const [moved] = next.splice(from, 1);
    next.splice(to, 0, moved!);
    onChange(next);
  };

  const onPasteFiles = useCallback(
    (event: React.ClipboardEvent) => {
      if (!canManage) return;
      const files = [...event.clipboardData.files];
      if (files.length) {
        event.preventDefault();
        enqueue(files);
      }
    },
    [canManage, enqueue],
  );

  useEffect(() => {
    // Keep clip/video kind in sync when publication kind toggles.
    if (publicationKind === 'clip') {
      const needs = items.some((item) => item.kind === 'video');
      if (needs) {
        onChange(
          items.map((item) =>
            item.kind === 'video' ? { ...item, kind: 'clip' as const } : item,
          ),
        );
      }
    }
  }, [publicationKind]); // eslint-disable-line react-hooks/exhaustive-deps -- sync on kind change only

  const cropping = items.find((item) => item.id === cropId);
  const activeUploads = uploads.filter((item) => item.status !== 'cancelled');

  const statusLabel = (status: string) => {
    if (status === 'queued' || status === 'hashing' || status === 'creating' || status === 'completing') {
      return tStatus('uploading');
    }
    if (status === 'uploading') return tStatus('uploading');
    if (status === 'processing' || status === 'ready') return tStatus(status === 'ready' ? 'ready' : 'processing');
    if (status === 'failed') return tStatus('failed');
    return status;
  };

  return (
    <section
      className="space-y-3 rounded-md border border-border p-3"
      aria-label={t('label')}
      data-testid="editor-media-block"
      onPaste={onPasteFiles}
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-sm font-semibold">{t('title')}</h2>
        <div className="flex flex-wrap gap-2">
          <Button
            type="button"
            size="sm"
            variant="outline"
            onClick={() => setPickerOpen(true)}
            data-testid="editor-media-from-library"
          >
            {t('fromLibrary')}
          </Button>
        </div>
      </div>

      {canManage ? (
        <FileDropzone
          label={t('dropLabel')}
          hint={
            publicationKind === 'clip' ? t('dropHintClip') : t('dropHint')
          }
          accept={
            publicationKind === 'clip'
              ? 'video/mp4,video/quicktime,.mp4,.mov'
              : UPLOAD_ACCEPT
          }
          multiple
          onFiles={(files) => enqueue(files)}
          data-testid="editor-media-dropzone"
        />
      ) : null}

      {activeUploads.length > 0 ? (
        <ul
          className="space-y-2"
          aria-label={tQueue('label')}
          data-testid="editor-media-upload-queue"
        >
          {activeUploads.slice(0, 12).map((item) => (
            <li
              key={item.localId}
              className="flex flex-wrap items-center gap-2 rounded-md border border-border bg-muted/30 px-2 py-1.5 text-xs"
            >
              <span className="min-w-0 flex-1 truncate font-medium">{item.file.name}</span>
              <span className="text-muted-foreground">
                {Math.round(item.progress * 100)}% · {statusLabel(item.status)}
              </span>
              {item.status === 'failed' ? (
                <>
                  <span className="text-destructive" role="alert">
                    {item.error === 'unsupported_format'
                      ? t('unsupportedFormat')
                      : item.error === 'upload_timeout'
                        ? tQueue('upload_timeout')
                        : item.error === 'etag_unavailable'
                          ? tQueue('etag_unavailable')
                          : item.error === 'upload_network'
                            ? tQueue('upload_network')
                            : item.error || tStatus('failed')}
                  </span>
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    data-testid={`upload-retry-${item.localId}`}
                    onClick={() => retry(item.localId)}
                  >
                    {tQueue('retry')}
                  </Button>
                </>
              ) : item.status !== 'ready' ? (
                <Button
                  type="button"
                  size="sm"
                  variant="ghost"
                  onClick={() => cancel(item.localId)}
                >
                  {tQueue('cancel')}
                </Button>
              ) : null}
            </li>
          ))}
        </ul>
      ) : null}

      {items.length > 0 ? (
        <ul className="space-y-2" data-testid="editor-media-list">
          {items.map((item, index) => (
            <li
              key={item.id}
              className="flex items-center gap-2 rounded-md border border-border bg-card p-2"
              draggable
              onDragStart={() => setDragIndex(index)}
              onDragOver={(event) => event.preventDefault()}
              onDrop={() => {
                if (dragIndex !== null) {
                  reorder(dragIndex, index);
                  setDragIndex(null);
                }
              }}
            >
              <GripVertical className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
              <div className="relative h-12 w-12 shrink-0 overflow-hidden rounded bg-muted text-muted-foreground">
                <MediaPreview
                  previewUrl={item.previewUrl}
                  kind={item.kind}
                  iconClassName="h-5 w-5"
                />
              </div>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium">{item.filename}</p>
                <p className="text-xs text-muted-foreground">
                  {item.kind === 'clip'
                    ? t('kindClip')
                    : item.cropPreset
                      ? t('cropped', { preset: item.cropPreset })
                      : item.mime}
                </p>
              </div>
              {item.kind === 'image' ? (
                <Button
                  type="button"
                  size="sm"
                  variant="ghost"
                  onClick={() => setCropId(item.id)}
                >
                  {t('crop')}
                </Button>
              ) : null}
              <Button
                type="button"
                size="icon"
                variant="ghost"
                aria-label={t('remove')}
                onClick={() => removeAt(index)}
              >
                <Trash2 className="h-4 w-4" />
              </Button>
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-sm text-muted-foreground">{t('empty')}</p>
      )}

      {issues.length > 0 ? (
        <ul className="space-y-1 text-sm text-destructive" data-testid="editor-media-issues">
          {issues.map((issue) => (
            <li key={`${issue.code}-${issue.network}-${issue.message}`}>{issue.message}</li>
          ))}
        </ul>
      ) : null}

      {cropping && cropping.previewUrl && cropping.width && cropping.height ? (
        <ImageCropper
          src={cropping.previewUrl}
          width={cropping.width}
          height={cropping.height}
          value={cropping.cropPreset}
          onChange={(preset: CropPreset, rect) => {
            onChange(
              items.map((item) =>
                item.id === cropping.id
                  ? { ...item, cropPreset: preset, crop: rect }
                  : item,
              ),
            );
          }}
          onClose={() => setCropId(null)}
        />
      ) : null}

      <MediaPicker
        workspaceId={workspaceId}
        open={pickerOpen}
        onOpenChange={setPickerOpen}
        onConfirm={appendAssets}
      />
    </section>
  );
}
