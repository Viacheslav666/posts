'use client';

import { Button, FileDropzone } from '@posts/ui';
import { GripVertical, Trash2 } from 'lucide-react';
import { useCallback, useMemo, useState } from 'react';
import { useTranslations } from 'next-intl';
import type { NetworkId } from '@posts/domain';

import { useNetworkCapabilities } from '@/features/editor/hooks/use-network-capabilities';
import { useCan } from '@/shared/hooks/use-can';

import { useUploadQueue } from '../hooks/use-upload-queue';
import { validateMediaAgainstCapabilities } from '../lib/media-limits';
import type { CropPreset, EditorMediaItem, MediaLibraryItem } from '../model/types';
import { ImageCropper } from './image-cropper';
import { MediaPicker } from './media-picker';

type EditorMediaBlockProps = {
  workspaceId: string;
  items: EditorMediaItem[];
  selectedNetworks: NetworkId[];
  onChange: (items: EditorMediaItem[]) => void;
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

export function EditorMediaBlock({
  workspaceId,
  items,
  selectedNetworks,
  onChange,
}: EditorMediaBlockProps) {
  const t = useTranslations('media.editor');
  const canManage = useCan('media.manage', workspaceId);
  const capabilitiesQuery = useNetworkCapabilities();
  const { enqueue, items: uploads } = useUploadQueue(workspaceId);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [cropId, setCropId] = useState<string | null>(null);
  const [dragIndex, setDragIndex] = useState<number | null>(null);

  const issues = useMemo(
    () => validateMediaAgainstCapabilities(items, capabilitiesQuery.data, selectedNetworks),
    [items, capabilitiesQuery.data, selectedNetworks],
  );

  const appendAssets = useCallback(
    (assets: MediaLibraryItem[]) => {
      onChange([...items, ...assets.map(toEditorItem)]);
    },
    [items, onChange],
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

  const cropping = items.find((item) => item.id === cropId);

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
          hint={t('dropHint')}
          accept="image/jpeg,image/png,image/webp,image/gif,video/mp4,video/quicktime"
          onFiles={(files) => enqueue(files)}
        />
      ) : null}

      {uploads.some((item) => item.status === 'uploading' || item.status === 'ready') ? (
        <ul className="space-y-1 text-xs text-muted-foreground">
          {uploads
            .filter((item) => item.status !== 'cancelled')
            .slice(0, 4)
            .map((item) => (
              <li key={item.localId}>
                {item.file.name}: {Math.round(item.progress * 100)}% ({item.status})
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
              {item.previewUrl ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img
                  src={item.previewUrl}
                  alt=""
                  className="h-12 w-12 rounded object-cover"
                />
              ) : (
                <div className="flex h-12 w-12 items-center justify-center rounded bg-muted text-xs">
                  {item.kind}
                </div>
              )}
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium">{item.filename}</p>
                <p className="text-xs text-muted-foreground">
                  {item.cropPreset ? t('cropped', { preset: item.cropPreset }) : item.mime}
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
