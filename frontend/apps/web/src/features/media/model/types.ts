import type { MediaAsset } from '@posts/api-client';

export type MediaKind = NonNullable<MediaAsset['kind']>;
export type MediaStatus = NonNullable<MediaAsset['status']>;

/** Crop aspect presets used in the post editor and stories (9:16). */
export const CROP_PRESETS = {
  '1:1': 1,
  '4:5': 4 / 5,
  '16:9': 16 / 9,
  '9:16': 9 / 16,
} as const;

export type CropPreset = keyof typeof CROP_PRESETS;

/**
 * MediaAsset plus the folder picker id.
 * `previewUrl` comes from the API (presigned card thumbnail) and may be a local
 * object URL while an upload is still only in the editor.
 * `playbackUrl` is a presigned mp4 for a ready video or clip. Library cards stay still.
 */
export type MediaLibraryItem = MediaAsset & {
  folderId?: string | null;
  previewUrl?: string;
};

export type MediaFolder = {
  id: string;
  name: string;
  workspaceId: string;
};

export type MediaQuota = {
  workspaceId: string;
  usedBytes: number;
  limitBytes: number;
  usedCount: number;
  limitCount: number;
};

export type MediaListFilters = {
  workspaceId: string;
  kind?: MediaKind;
  status?: MediaStatus;
  q?: string;
  folderId?: string | null;
  tag?: string;
  cursor?: string;
  limit?: number;
};

/** Attachment selected in the post editor (ordered). */
export type EditorMediaItem = {
  id: string;
  mediaId: string;
  kind: MediaKind;
  mime: string;
  filename: string;
  previewUrl?: string;
  /** Presigned mp4 for a ready video or clip. Absent while an upload is local-only. */
  playbackUrl?: string;
  width?: number;
  height?: number;
  sizeBytes?: number;
  /** Optional crop applied client-side before publish. */
  cropPreset?: CropPreset;
  crop?: { x: number; y: number; width: number; height: number };
};

export type UploadQueueItemStatus =
  | 'queued'
  | 'hashing'
  | 'creating'
  | 'uploading'
  | 'completing'
  | 'processing'
  | 'ready'
  | 'failed'
  | 'cancelled';

export type UploadQueueItem = {
  localId: string;
  file: File;
  workspaceId: string;
  status: UploadQueueItemStatus;
  progress: number;
  mediaId?: string;
  error?: string;
  /** Completed S3 part numbers for resume. */
  completedParts?: Array<{ partNumber: number; etag: string }>;
  uploadId?: string;
  uploadUrl?: string;
  partSize?: number;
};
