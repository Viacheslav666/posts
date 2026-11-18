'use client';

import { useCallback, useRef, useState } from 'react';

import { completeUpload, createUpload } from '../api/media-api';
import { isSupportedUploadMime, kindFromMime } from '../lib/kind-from-mime';
import {
  parseUploadSessionHeaders,
  sha256Hex,
  UploadCancelledError,
  uploadMultipart,
  type CompletedPart,
} from '../lib/multipart-uploader';
import type { UploadQueueItem } from '../model/types';

function localId(): string {
  return `local-${crypto.randomUUID()}`;
}

export function useUploadQueue(workspaceId: string) {
  const [items, setItems] = useState<UploadQueueItem[]>([]);
  const controllers = useRef(new Map<string, AbortController>());
  const resumeParts = useRef(new Map<string, CompletedPart[]>());

  const patch = useCallback((id: string, partial: Partial<UploadQueueItem>) => {
    setItems((current) =>
      current.map((item) => (item.localId === id ? { ...item, ...partial } : item)),
    );
  }, []);

  const runUpload = useCallback(
    async (item: UploadQueueItem) => {
      const controller = new AbortController();
      controllers.current.set(item.localId, controller);

      try {
        if (!isSupportedUploadMime(item.file.type)) {
          patch(item.localId, {
            status: 'failed',
            error: 'unsupported_format',
          });
          return;
        }

        patch(item.localId, { status: 'hashing', progress: 0 });
        const sha256 = await sha256Hex(item.file);

        patch(item.localId, { status: 'creating' });
        const sessionResponse = await createUpload({
          workspaceId: item.workspaceId,
          kind: kindFromMime(item.file.type),
          mime: item.file.type,
          sizeBytes: item.file.size,
          filename: item.file.name,
          sha256,
        });

        const { uploadId, partSize } = parseUploadSessionHeaders(sessionResponse.headers);
        const mediaId = sessionResponse.mediaId!;
        const uploadUrl = sessionResponse.uploadUrl!;
        const resolvedUploadId = uploadId || `upload-${mediaId}`;

        patch(item.localId, {
          status: 'uploading',
          mediaId,
          uploadId: resolvedUploadId,
          uploadUrl,
          partSize,
        });

        const completed = await uploadMultipart({
          file: item.file,
          session: {
            mediaId,
            uploadUrl,
            uploadId: resolvedUploadId,
            partSize,
            headers: sessionResponse.headers,
          },
          completedParts: resumeParts.current.get(item.localId) ?? item.completedParts,
          signal: controller.signal,
          onProgress: (progress) => {
            resumeParts.current.set(item.localId, progress.completedParts);
            patch(item.localId, {
              progress: progress.ratio,
              completedParts: progress.completedParts,
            });
          },
        });

        patch(item.localId, { status: 'completing', progress: 1 });
        await completeUpload(mediaId, {
          uploadId: completed.uploadId,
          etags: completed.etags,
        });

        patch(item.localId, { status: 'processing', progress: 1 });
        // Polling for ready is handled by library consumers / media list invalidation.
        patch(item.localId, { status: 'ready' });
        resumeParts.current.delete(item.localId);
      } catch (error) {
        if (error instanceof UploadCancelledError || controller.signal.aborted) {
          patch(item.localId, { status: 'cancelled' });
          return;
        }
        patch(item.localId, {
          status: 'failed',
          error: error instanceof Error ? error.message : 'upload_failed',
        });
      } finally {
        controllers.current.delete(item.localId);
      }
    },
    [patch],
  );

  const enqueue = useCallback(
    (files: File[]) => {
      const next: UploadQueueItem[] = files.map((file) => ({
        localId: localId(),
        file,
        workspaceId,
        status: 'queued',
        progress: 0,
      }));
      setItems((current) => [...next, ...current]);
      for (const item of next) {
        void runUpload(item);
      }
      return next;
    },
    [runUpload, workspaceId],
  );

  const cancel = useCallback((localIdValue: string) => {
    controllers.current.get(localIdValue)?.abort();
    setItems((current) =>
      current.map((item) =>
        item.localId === localIdValue ? { ...item, status: 'cancelled' } : item,
      ),
    );
  }, []);

  const retry = useCallback(
    (localIdValue: string) => {
      const existing = items.find((item) => item.localId === localIdValue);
      if (!existing) return;
      const restarted: UploadQueueItem = {
        ...existing,
        status: 'queued',
        progress: existing.completedParts?.length ? existing.progress : 0,
        error: undefined,
      };
      setItems((current) =>
        current.map((item) => (item.localId === localIdValue ? restarted : item)),
      );
      void runUpload(restarted);
    },
    [items, runUpload],
  );

  const clearFinished = useCallback(() => {
    setItems((current) =>
      current.filter(
        (item) => !['ready', 'cancelled', 'failed'].includes(item.status),
      ),
    );
  }, []);

  return { items, enqueue, cancel, retry, clearFinished };
}
